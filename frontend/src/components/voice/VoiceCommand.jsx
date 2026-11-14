// ============================================
// 🎤 말로 시키기 (모든 화면 오른쪽 아래의 동그란 버튼)
// - 어느 화면에서나 같은 AI 챗봇: 누르면 듣기 시작 → 들은 말을 voiceIntent로 가려서
//     화면 열기 · 종목 찾기 · 장터 검색 · 번역은 바로 하고, 나머지는 그 자리에서 AI 챗봇에게 물어 답을 보여 주고 읽어 줌
//     (화면을 옮기지 않음. 하트 보내기 · 약속 잡기 · 날씨 · 주식 질문 모두 여기서. 답을 읽은 뒤 다시 들어서 이어서 대화)
// - 챗봇이 무언가를 했으면(aiActed) 지금 보고 있는 화면이 숫자 · 목록을 다시 불러옴
// - AI 챗봇 화면(/ai)에서만 숨김 (그 화면이 곧 챗봇)
// - 음성 인식이 안 되는 기기(아이폰 홈 화면 앱 등)에서는 글자 입력칸으로: 키보드의 🎤 받아쓰기를 쓰면 됨
// - 🟢 항상 듣기(voice/alwaysListen.js): 켜 두면 버튼을 누르지 않아도 계속 듣다가, 말소리가 들리면 창이 저절로 열리고
//   들리는 말이 그대로 보이고, 말이 끝나면 바로 실행 (서원 요청: "챗봇아"라고 부르지 않아도 되게)
//   (시트가 열려 있거나, 탭이 가려졌거나, 소리로 읽는 중이면 잠깐 쉼. 마이크 권한이 막혀 있으면 이번 접속에서는 멈춤)
// ============================================
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { canListen, listen, speak, stopSpeaking } from '../../voice/speech'
import { voiceIntent } from '../../voice/voiceIntent'
import { alwaysOn, onAlwaysChange, setAlways, wakeCommand } from '../../voice/alwaysListen'
import { aiActed, onOpenVoice } from '../../voice/aiActed'
import './VoiceCommand.css'

const EXAMPLES = ['오늘 날씨 어때?', '커플한테 하트 보내줘', '토요일 7시 데이트 잡아줘', '모의투자 열어줘', '삼성전자 주가 보여줘']
const HIDE = [/^\/ai(\/|$)/]                       // AI 챗봇 화면은 그 자체가 챗봇
const RAISED = [/^\/market\/\d+$/, /^\/messages(\/|$)/]   // 아래에 입력칸 · 버튼 줄이 붙는 화면에서는 위로

function VoiceCommand() {

  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [open, setOpen] = useState(false)
  const [state, setState] = useState('idle')   // idle · listening · working
  const [heard, setHeard] = useState('')
  const [message, setMessage] = useState('')
  const [typed, setTyped] = useState('')
  const [chat, setChat] = useState([])          // 이 시트에서 나눈 대화 [{ role: 'user' | 'ai', text, needLogin? }]
  const chatBox = useRef(null)
  useEffect(() => { if (chatBox.current) chatBox.current.scrollTop = chatBox.current.scrollHeight }, [chat])   // 새 답이 오면 아래로
  const openRef = useRef(false)                 // 답을 읽은 뒤 다시 들을지 판단용 (닫혔으면 안 들음)
  const session = useRef(null)
  const supported = canListen()
  const hidden = HIDE.some((re) => re.test(pathname))

  // 🟢 항상 듣기
  const [always, setAlwaysState] = useState(alwaysOn)
  const [blocked, setBlocked] = useState(false)     // 마이크 권한이 막힘 → 이번 접속에서는 쉬기
  useEffect(() => onAlwaysChange(setAlwaysState), [])

  const close = () => {
    session.current?.stop()
    session.current = null
    openRef.current = false
    stopSpeaking()
    setChat([])
    setOpen(false)
    setState('idle')
    setHeard('')
    setMessage('')
    setTyped('')
  }

  // Esc로 닫기
  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => { if (e.key === 'Escape') close() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  const go = (to, say) => {
    close()
    speak(say)
    navigate(to)
  }

  // 그 자리에서 AI 챗봇에게 묻기 (AI 챗봇 화면과 같은 API · 같은 도구)
  const askHere = async (question) => {
    const history = chat.filter((m) => m.text).slice(-6).map((m) => ({ role: m.role === 'ai' ? 'assistant' : 'user', text: m.text.slice(0, 500) }))
    setChat((c) => [...c, { role: 'user', text: question }])
    setHeard('')
    setState('working')
    setMessage('생각하는 중...')

    try {
      const res = await fetch('/api/ai/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: question.slice(0, 500), history }),
      })
      const data = await res.json()
      setState('idle')
      setMessage('')

      if (!data.success) {
        setChat((c) => [...c, { role: 'ai', text: data.message ?? 'AI 응답에 실패했어요.', needLogin: data.loginRequired === true }])
        return
      }

      setChat((c) => [...c, { role: 'ai', text: data.answer, needLogin: data.loginRequired === true }])
      if ((data.toolsUsed ?? []).length > 0) aiActed(data.toolsUsed)   // 하트 · 약속 등 → 보고 있는 화면 새로고침

      // 챗봇이 화면을 열기로 했으면 답을 읽으면서 이동
      if (typeof data.navigate === 'string' && data.navigate.startsWith('/') && !data.navigate.startsWith('//')) {
        return go(data.navigate, data.answer)
      }

      // 답을 읽고, 시트가 아직 열려 있으면 다시 들음 (이어서 대화)
      speak(data.answer, 'ko', () => { if (openRef.current && supported) start() })
    } catch {
      setState('idle')
      setMessage('서버에 연결하지 못했어요. 잠시 후 다시 해주세요.')
    }
  }

  // 들은 말(또는 입력한 글)로 할 일 정하기
  const run = async (text) => {
    const intent = voiceIntent(text)
    setHeard(text)

    if (intent.type === 'none') return setMessage('무슨 말인지 듣지 못했어요. 다시 말해주세요.')
    if (intent.type === 'go') return go(intent.to, `${intent.label} 화면을 열게요.`)
    if (intent.type === 'translate') return go(`/translate?text=${encodeURIComponent(intent.text)}&to=${intent.to}`, '번역할게요.')
    if (intent.type === 'market') return go(`/market?q=${encodeURIComponent(intent.q)}`, `${intent.q} 상품을 찾아볼게요.`)
    if (intent.type === 'ask') return askHere(intent.q)

    // 종목: 이름으로 찾아서 있으면 그 종목 화면, 없으면 AI 챗봇에게
    setState('working')
    setMessage(`"${intent.q}" 종목을 찾는 중...`)
    try {
      const j = await fetch(`/api/stock/search?q=${encodeURIComponent(intent.q)}`).then((r) => r.json())
      const stock = j.success ? j.stocks?.[0] : null
      if (stock) return go(`/stock?symbol=${stock.symbol}`, `${stock.name} 화면을 열게요.`)
      return askHere(text)
    } catch {
      setState('idle')
      return setMessage('서버에 연결하지 못했어요. 잠시 후 다시 해주세요.')
    }
  }

  const start = () => {
    stopSpeaking()
    setHeard('')
    setMessage('')
    setState('listening')
    session.current = listen({
      onInterim: setHeard,
      onFinal: (text) => run(text),
      onError: (msg) => setMessage(msg),
      onEnd: () => setState((s) => (s === 'listening' ? 'idle' : s)),
    })
  }

  const openSheet = () => {
    openRef.current = true
    setOpen(true)
    if (supported) start()
  }

  // 상단의 🎤 버튼을 눌렀을 때도 같은 시트를 엶
  const opener = useRef(null)
  useEffect(() => { opener.current = openSheet })
  useEffect(() => onOpenVoice(() => opener.current?.()), [])

  // 🟢 항상 듣기에서 말소리가 들리기 시작했을 때: 창을 열고 들리는 말을 바로 보여 줌 / 다 들었을 때: 그 말을 실행
  const auto = useRef({})
  useEffect(() => {
    auto.current = {
      heard: (text) => {
        openRef.current = true
        setOpen(true)
        setState('listening')
        setMessage('')
        setHeard(text)
      },
      done: (text) => {
        setState('idle')
        // "챗봇아, …"라고 불렀으면 부르는 말은 떼고, 부르기만 했으면 "네" 하고 다음 말을 들음
        const called = wakeCommand(text)
        const command = called === null ? text : called
        if (called === '') {
          speak('네')
          return setTimeout(start, 700)
        }
        // "챗봇 열어줘"는 AI 챗봇 화면을 열라는 말
        if (called && /^(열어|켜|보여|틀어|띄워|화면|페이지|(으)?로\s?가)/.test(called)) return run(`AI 챗봇 ${called}`)
        return run(command)
      },
    }
  })

  // 항상 듣기 반복: 듣기 → 말소리가 들리면 창이 열리고 → 다 들으면 실행. 조용하면 조금 뒤 다시 듣기
  // (창이 열려 있는 동안은 창 안에서 대화가 이어지므로 이 반복은 쉼)
  useEffect(() => {
    if (!always || !supported || blocked || open || hidden) return undefined

    let stopped = false
    let handed = false       // 말소리가 들려서 창으로 넘긴 듣기 → 정리할 때 끊지 않음
    let timer = 0
    let current = null

    const loop = () => {
      if (stopped) return
      // 탭이 가려졌거나 소리로 읽는 중(내 목소리를 다시 듣지 않게)이면 잠깐 뒤에
      if (document.visibilityState !== 'visible' || window.speechSynthesis?.speaking) {
        timer = setTimeout(loop, 1000)
        return
      }
      current = listen({
        onInterim: (text) => {
          if (stopped && !handed) return
          if (text.replace(/\s/g, '').length < 2) return     // 한 글자짜리 잡음은 무시
          if (!handed) {
            handed = true
            session.current = current                         // 창의 ⏺ 버튼으로 멈출 수 있게
          }
          auto.current.heard?.(text)
        },
        onFinal: (text) => {
          if (handed) auto.current.done?.(text)
        },
        onError: (_msg, code) => {
          if (code === 'not-allowed' || code === 'service-not-allowed' || code === 'unsupported') setBlocked(true)
        },
        onEnd: () => {
          if (handed) setState((st) => (st === 'listening' ? 'idle' : st))   // 창으로 넘긴 듣기가 끝남 (오류로 끝났어도 "듣는 중"에 멈춰 있지 않게)
          else if (!stopped) timer = setTimeout(loop, 400)
        },
      })
    }
    timer = setTimeout(loop, 600)

    return () => {
      stopped = true
      clearTimeout(timer)
      if (!handed) current?.stop()
    }
  }, [always, supported, blocked, open, hidden])

  const toggleAlways = () => {
    const next = !always
    setBlocked(false)
    setAlways(next)
    setMessage(next ? '항상 듣기를 켰어요. 이제 창을 닫아도, 말만 하면 창이 열리고 바로 물어봐요.' : '항상 듣기를 껐어요.')
  }

  const submitTyped = (e) => {
    e.preventDefault()
    if (!typed.trim()) return
    run(typed.trim())
    setTyped('')
  }

  if (hidden) return null

  const live = always && supported && !blocked

  return (
    <>
      <button type="button" className={`vc-fab ${RAISED.some((re) => re.test(pathname)) ? 'vc-fab-raised' : ''} ${live ? 'vc-fab-live' : ''}`} onClick={openSheet}
        aria-label={live ? '말로 시키기 (항상 듣는 중: 그냥 말하면 돼요)' : '말로 시키기'} title={live ? '항상 듣는 중 · 그냥 말하면 창이 열려요' : '말로 시키기'}>
        <span aria-hidden="true">🎤</span>
        <b className="vc-fab-label">말하기</b>
      </button>

      {open && createPortal(
        <div className="vc-backdrop" onClick={close}>
          <div className="vc-sheet" role="dialog" aria-modal="true" aria-label="말로 시키기" onClick={(e) => e.stopPropagation()}>
            <button type="button" className="vc-close" onClick={close} aria-label="닫기">✕</button>

            {/* 이 자리에서 나눈 대화 (최근 것이 아래) */}
            {chat.length > 0 && (
              <div className="vc-chat" aria-live="polite" ref={chatBox}>
                {chat.slice(-6).map((m, i) => (
                  <div key={i} className={`vc-msg vc-msg-${m.role}`}>
                    <p>{m.text.replace(/\*\*/g, '')}</p>
                    {m.needLogin && <Link to="/login" className="vc-login" onClick={close}>🔑 로그인하러 가기</Link>}
                  </div>
                ))}
              </div>
            )}

            {supported && (
              <button type="button" className={`vc-mic ${state === 'listening' ? 'vc-mic-on' : ''}`} onClick={state === 'listening' ? () => session.current?.stop() : start}
                disabled={state === 'working'} aria-label={state === 'listening' ? '듣는 중 · 눌러서 멈추기' : '눌러서 말하기'}>
                <span aria-hidden="true">{state === 'listening' ? '⏺' : '🎤'}</span>
              </button>
            )}

            <p className="vc-status" role="status" aria-live="polite">
              {message || (state === 'listening' ? '듣고 있어요. 말씀하세요.' : supported ? (chat.length > 0 ? '이어서 말하려면 버튼을 누르세요.' : '버튼을 누르고 말해보세요.') : '이 기기에서는 음성 인식이 안 돼요. 아래 칸을 누르고 키보드의 🎤(받아쓰기)로 말해보세요.')}
            </p>
            {heard && <p className="vc-heard">“{heard}”</p>}

            <form className="vc-form" onSubmit={submitTyped}>
              <input value={typed} onChange={(e) => setTyped(e.target.value)} placeholder="직접 입력해도 돼요" maxLength={200} aria-label="시킬 말 입력" enterKeyHint="go" />
              <button type="submit" disabled={!typed.trim() || state === 'working'}>보내기</button>
            </form>

            {supported && (
              <div className="vc-always">
                <div>
                  <b>🟢 항상 듣기</b>
                  <span>{blocked ? '마이크 권한이 막혀 있어요. 주소창의 자물쇠에서 마이크를 허용해 주세요.' : '켜 두면 버튼을 누르지 않아도, 말하면 창이 열리고 바로 물어봐요.'}</span>
                </div>
                <button type="button" className={always ? 'vc-always-on' : ''} onClick={toggleAlways} aria-pressed={always}>{always ? '켜짐' : '꺼짐'}</button>
              </div>
            )}

            {chat.length === 0 && (
            <div className="vc-examples">
              <span>이렇게 말해보세요</span>
              {EXAMPLES.map((ex) => <button key={ex} type="button" onClick={() => run(ex)} disabled={state === 'working'}>{ex}</button>)}
            </div>
            )}
          </div>
        </div>,
        document.body,
      )}
    </>
  )
}

export default VoiceCommand
