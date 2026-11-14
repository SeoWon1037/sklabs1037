// ============================================
// 🎤 말로 시키기 (모든 화면 오른쪽 아래의 동그란 버튼)
// - 누르면 듣기 시작 → 들은 말을 voiceIntent로 가려서
//     화면 열기 · 종목 찾기 · 장터 검색 · 번역 · (나머지) AI 비서에게 질문
// - 주문 · 글쓰기 · 삭제 같은 실제 동작은 하지 않음 (화면을 열거나 찾아주기만)
// - 음성 인식이 안 되는 기기(아이폰 홈 화면 앱 등)에서는 글자 입력칸으로: 키보드의 🎤 받아쓰기를 쓰면 됨
// - 자기 🎤이 있는 화면(AI 비서 · 번역)과 입력칸이 아래에 붙는 화면(메시지)에서는 버튼을 숨김
// ============================================
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useLocation, useNavigate } from 'react-router-dom'
import { canListen, listen, speak, stopSpeaking } from '../../voice/speech'
import { voiceIntent } from '../../voice/voiceIntent'
import './VoiceCommand.css'

const EXAMPLES = ['모의투자 열어줘', '삼성전자 주가 보여줘', '아이폰 중고 찾아줘', '화장실 어디예요 영어로', '오늘 코스피 어땠어?']
const HIDE = [/^\/ai(\/|$)/, /^\/translate(\/|$)/, /^\/messages(\/|$)/, /^\/admin(\/|$)/, /^\/login/, /^\/signup/]

function VoiceCommand() {

  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [open, setOpen] = useState(false)
  const [state, setState] = useState('idle')   // idle · listening · working
  const [heard, setHeard] = useState('')
  const [message, setMessage] = useState('')
  const [typed, setTyped] = useState('')
  const session = useRef(null)
  const supported = canListen()

  const close = () => {
    session.current?.stop()
    session.current = null
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
    speak(say)
    close()
    navigate(to)
  }

  // 들은 말(또는 입력한 글)로 할 일 정하기
  const run = async (text) => {
    const intent = voiceIntent(text)
    setHeard(text)

    if (intent.type === 'none') return setMessage('무슨 말인지 듣지 못했어요. 다시 말해주세요.')
    if (intent.type === 'go') return go(intent.to, `${intent.label} 화면을 열게요.`)
    if (intent.type === 'translate') return go(`/translate?text=${encodeURIComponent(intent.text)}&to=${intent.to}`, '번역할게요.')
    if (intent.type === 'market') return go(`/market?q=${encodeURIComponent(intent.q)}`, `${intent.q} 상품을 찾아볼게요.`)
    if (intent.type === 'ask') return go(`/ai?q=${encodeURIComponent(intent.q)}&voice=1`, 'AI 비서에게 물어볼게요.')

    // 종목: 이름으로 찾아서 있으면 그 종목 화면, 없으면 AI 비서에게
    setState('working')
    setMessage(`"${intent.q}" 종목을 찾는 중...`)
    try {
      const j = await fetch(`/api/stock/search?q=${encodeURIComponent(intent.q)}`).then((r) => r.json())
      const stock = j.success ? j.stocks?.[0] : null
      if (stock) return go(`/stock?symbol=${stock.symbol}`, `${stock.name} 화면을 열게요.`)
      return go(`/ai?q=${encodeURIComponent(text)}&voice=1`, 'AI 비서에게 물어볼게요.')
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
    setOpen(true)
    if (supported) start()
  }

  const submitTyped = (e) => {
    e.preventDefault()
    if (typed.trim()) run(typed.trim())
  }

  if (HIDE.some((re) => re.test(pathname))) return null

  return (
    <>
      <button type="button" className={`vc-fab ${/^\/market\/\d+$/.test(pathname) ? 'vc-fab-raised' : ''}`} onClick={openSheet} aria-label="말로 시키기">
        <span aria-hidden="true">🎤</span>
      </button>

      {open && createPortal(
        <div className="vc-backdrop" onClick={close}>
          <div className="vc-sheet" role="dialog" aria-modal="true" aria-label="말로 시키기" onClick={(e) => e.stopPropagation()}>
            <button type="button" className="vc-close" onClick={close} aria-label="닫기">✕</button>

            {supported && (
              <button type="button" className={`vc-mic ${state === 'listening' ? 'vc-mic-on' : ''}`} onClick={state === 'listening' ? () => session.current?.stop() : start}
                disabled={state === 'working'} aria-label={state === 'listening' ? '듣는 중 · 눌러서 멈추기' : '눌러서 말하기'}>
                <span aria-hidden="true">{state === 'listening' ? '⏺' : '🎤'}</span>
              </button>
            )}

            <p className="vc-status" role="status" aria-live="polite">
              {message || (state === 'listening' ? '듣고 있어요. 말씀하세요.' : supported ? '버튼을 누르고 말해보세요.' : '이 기기에서는 음성 인식이 안 돼요. 아래 칸을 누르고 키보드의 🎤(받아쓰기)로 말해보세요.')}
            </p>
            {heard && <p className="vc-heard">“{heard}”</p>}

            <form className="vc-form" onSubmit={submitTyped}>
              <input value={typed} onChange={(e) => setTyped(e.target.value)} placeholder="직접 입력해도 돼요" maxLength={200} aria-label="시킬 말 입력" enterKeyHint="go" />
              <button type="submit" disabled={!typed.trim() || state === 'working'}>보내기</button>
            </form>

            <div className="vc-examples">
              <span>이렇게 말해보세요</span>
              {EXAMPLES.map((ex) => <button key={ex} type="button" onClick={() => run(ex)} disabled={state === 'working'}>{ex}</button>)}
            </div>
          </div>
        </div>,
        document.body,
      )}
    </>
  )
}

export default VoiceCommand
