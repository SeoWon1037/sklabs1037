// ============================================
// AI 챗봇 화면 (주소: /ai) — 로그인 없이도 질문 가능 (하루 20번), 내 계좌 · 하트처럼 로그인이 필요한 답은 [로그인하러 가기]
// - 백엔드 API
// - 🧭 "○○ 열어줘"는 바로 그 화면을 엶 (voiceIntent로 먼저 가리고, 못 가린 말은 AI가 open_page 도구로 → 응답의 navigate)
//   POST /api/ai/chat     { message, history(앞 대화 최근 6개) }         → { success, chatId, answer, toolsUsed, sources(🧾 참고한 기사·종목) }
//   POST /api/ai/feedback { chatId, rating }  → { success }
//   GET  /api/watchlist                       → 관심 종목 (브리핑 버튼용)
// - AI가 어떤 도구(DB 조회, 문서 검색 등)를 썼는지 + 답변 피드백(👍/👎)
// - /ai?q=질문 으로 들어오면 입력창에 질문을 미리 채움 (메인 화면의 "AI에게 묻기")
// - /ai?briefing=1 로 들어오면 관심 종목 브리핑 질문을 미리 채움 (메인 화면의 "AI 브리핑 받기")
// ============================================
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import VoiceButton from '../../components/ai/VoiceButton'
import { canSpeak, speak, stopSpeaking } from '../../voice/speech'
import { alwaysOn } from '../../voice/alwaysListen'
import { voiceIntent } from '../../voice/voiceIntent'

// 🧾 답변 근거 종류
const SOURCE_TYPE = { NEWS: '뉴스', STOCK: '종목', DOC: '문서', MOCK: '내 계좌', DATA: '데이터', PLAN: '약속', MARKET: '장터' }
import './AiChatPage.css'

// 예시 질문 (처음 화면에 버튼으로 표시)
const EXAMPLES = [
  '삼성전자 최근 한 달 주가 흐름 알려줘',
  '🔎 코스닥에서 최근 3일 연속 오른 종목 찾아줘',
  '삼성전자 요즘 왜 이렇게 떨어졌어? 최근 뉴스로 알려줘',
  '💬 오늘 서울 날씨 어때?',
  '💬 면접에서 1분 자기소개 어떻게 하면 좋을까?',
  '🔒 토요일 저녁 7시 강남에서 민지랑 약속 잡아줘',
  '🔒 커플한테 하트 보내줘',
]

// 관심 종목 브리핑 질문 (기존 AI 챗봇 도구로 수익률 + 뉴스를 찾아 정리)
const briefingQuestion = (names) =>
  `내 관심 종목 ${names.join(', ')} 브리핑해줘. 종목별로 최근 5거래일 수익률과 관련 뉴스 핵심을 2줄씩 정리해줘.`

// AI 답변의 **굵은 글씨** 표시를 실제 굵은 글씨로 (나머지 글자는 그대로)
// - split에 괄호( )가 있으면 괄호 안 글자도 결과에 남음 → [일반, 굵게, 일반, 굵게, ...] 순서
// - 그래서 홀수 번째(i % 2 === 1)만 <strong>으로 감쌈
const renderText = (text) =>
  text.split(/\*\*(.+?)\*\*/g).map((part, i) => (i % 2 === 1 ? <strong key={i}>{part}</strong> : part))

function AiChatPage() {

  // 주소의 ?q= / ?briefing= 값 (메인 화면에서 넘어온 질문)
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const wantBriefing = searchParams.get('briefing') === '1'

  // 대화 목록: { role: 'user' | 'ai', text, tools?, chatId?, rating?, error? }
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState(() => searchParams.get('q') ?? '')
  const [loading, setLoading] = useState(false)

  // 🔊 음성 대화: 켜면 🎤로 말한 질문을 바로 보내고, 답을 소리 내어 읽어줌 (이 브라우저에 기억)
  // - 🎤 말로 시키기에서 넘어온 질문(?voice=1)은 켠 채로 시작
  const [talk, setTalk] = useState(() => {
    if (!canSpeak()) return false
    if (searchParams.get('voice') === '1') return true
    if (alwaysOn()) return true                       // 🟢 항상 듣기를 켠 기기는 음성 대화도 켠 채로
    try { return localStorage.getItem('sklabs.ai.talk') === '1' } catch { return false }
  })
  const talkRef = useRef(talk)
  const toggleTalk = () => {
    const next = !talk
    talkRef.current = next
    setTalk(next)
    if (!next) {
      stopSpeaking()
      voiceRef.current?.stop()
    }
    try { localStorage.setItem('sklabs.ai.talk', next ? '1' : '0') } catch { /* 기억 못 해도 됨 */ }
  }

  // 관심 종목 이름 (브리핑 버튼용)
  const [watchNames, setWatchNames] = useState([])

  // 새 메시지가 오면 대화 칸만 맨 아래로 스크롤
  // - 처음(대화 없음)에는 스크롤하지 않음 → 휴대폰에서 안내·예시 질문 위쪽이 잘려 보이던 문제
  // - scrollIntoView는 화면 전체까지 움직여서, 대화 칸(scrollTop)만 움직임
  const bottomRef = useRef(null)
  useEffect(() => {
    if (messages.length === 0 && !loading) return
    const box = bottomRef.current?.parentElement
    if (box) box.scrollTo({ top: box.scrollHeight, behavior: 'smooth' })
  }, [messages, loading])

  // 처음 한 번: 관심 종목 (로그아웃 상태면 빈 목록)
  useEffect(() => {
    fetch('/api/watchlist')
      .then((res) => res.json())
      .then((data) => {
        const names = data.success ? data.stocks.map((s) => s.name) : []
        setWatchNames(names)
        // 메인 화면 "AI 브리핑 받기"로 들어온 경우 → 브리핑 질문을 입력창에 채워둠 (보내기는 직접)
        if (wantBriefing && names.length > 0) setInput(briefingQuestion(names))
      })
      .catch(() => setWatchNames([]))
  }, [wantBriefing])

  // ============================================
  // 질문 보내기
  // ============================================
  // 🟢 항상 듣기: 이 화면에 들어오면 마이크를 바로 켜고, 말이 없어도 계속 듣고, 답한 뒤에도 다시 들음 ("챗봇아"라고 부르지 않아도 됨)
  const [always] = useState(alwaysOn)
  useEffect(() => {
    if (!always || searchParams.get('voice') === '1' || searchParams.get('brief')) return undefined
    const timer = setTimeout(() => voiceRef.current?.start(), 500)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // byVoice: 🎤로 말한 질문 → 답을 다 읽은 뒤 마이크를 다시 켬 (계속 듣기: 버튼 없이 이어서 대화)
  const voiceRef = useRef(null)
  const sendMessage = async (text, byVoice = false) => {

    const question = text.trim()
    if (question === '' || loading) return

    // 🧭 "커플 페이지 열어줘"처럼 화면을 열어 달라는 말은 AI에게 묻지 않고 바로 엶 (빠르고 비용 0)
    const intent = voiceIntent(question)
    // ("모의투자"처럼 이름만 쓴 것은 질문일 수 있으니, 열어 달라는 말이 붙었을 때만)
    if (intent.type === 'go' && intent.to !== '/ai' && /(열어|가\s?줘|가자|보여|이동|들어가|넘어가|켜\s?줘|띄워)/.test(question)) {
      const say = `${intent.label} 화면을 열게요.`
      setMessages((prev) => [...prev, { role: 'user', text: question }, { role: 'ai', text: `🧭 ${say}` }])
      setInput('')
      if (talkRef.current) speak(say)
      setTimeout(() => navigate(intent.to), 700)
      return
    }

    // 🧠 대화 기억: 앞에 나눈 대화 중 최근 6개(오류 제외)를 같이 보내서 "그럼 그건?" 같은 이어지는 질문을 알아듣게 함
    const history = messages
      .filter((m) => !m.error && m.text)
      .slice(-6)
      .map((m) => ({ role: m.role === 'ai' ? 'assistant' : 'user', text: (m.speech ?? m.text).slice(0, 500) }))

    setMessages((prev) => [...prev, { role: 'user', text: question }])
    setInput('')
    setLoading(true)

    try {
      const res = await fetch('/api/ai/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: question, history }),
      })
      const data = await res.json()

      if (data.success) {
        setMessages((prev) => [
          ...prev,
          { role: 'ai', text: data.answer, tools: data.toolsUsed, sources: data.sources, chatId: data.chatId, rating: null, needLogin: data.loginRequired === true },
        ])
        // 🔊 음성 대화 중이면 답을 읽어줌. 말로 물었으면 다 읽고 나서 다시 들음
        // 🧭 챗봇이 화면을 열기로 했으면(open_page) 답을 잠깐 보여 준 뒤 이동 (서버가 정한 사이트 안 주소만)
        if (typeof data.navigate === 'string' && data.navigate.startsWith('/') && !data.navigate.startsWith('//')) {
          if (talkRef.current) speak(data.answer)
          setTimeout(() => navigate(data.navigate), 1200)
          return
        }

        if (talkRef.current) speak(data.answer, 'ko', byVoice || always ? () => { if (talkRef.current) voiceRef.current?.start() } : undefined)
        else if (always) setTimeout(() => voiceRef.current?.start(), 300)
      } else {
        setMessages((prev) => [...prev, { role: 'ai', text: data.message, error: true, needLogin: data.loginRequired === true }])
      }

    } catch {
      setMessages((prev) => [...prev, { role: 'ai', text: '서버에 연결할 수 없습니다.', error: true }])
    } finally {
      setLoading(false)
    }
  }

  // 🎤 말로 시키기에서 넘어온 질문(?q=…&voice=1)은 바로 보냄 (처음 한 번) + 화면을 떠나면 읽기를 멈춤
  const autoAsked = useRef(false)
  useEffect(() => {
    const q = searchParams.get('q')
    let timer = 0
    if (searchParams.get('voice') === '1' && q && !autoAsked.current) {
      autoAsked.current = true
      timer = setTimeout(() => sendMessage(q, true), 0)
    }

    // 🔔 브리핑 · 약속 알림을 눌러 들어온 경우(?brief=morning|close|plan): 지금 기준 문장을 받아서 바로 읽음
    const brief = searchParams.get('brief')
    if (brief && !autoAsked.current) {
      autoAsked.current = true
      fetch(`/api/voice/brief?kind=${encodeURIComponent(brief)}`)
        .then((res) => res.json())
        .then((data) => {
          if (!data.success) {
            setMessages((prev) => [...prev, { role: 'ai', text: data.message ?? '브리핑을 가져오지 못했어요.', error: true, needLogin: true }])
            return
          }
          speak(data.text)
          // 브라우저가 "누르기 전에는 소리 금지"로 막았으면 큰 버튼을 보여 줌
          setTimeout(() => {
            const silent = !window.speechSynthesis?.speaking && !window.speechSynthesis?.pending
            setMessages((prev) => [...prev, { role: 'ai', text: `${data.title}\n\n${data.text}`, speech: data.text, tapToHear: silent }])
          }, 400)
        })
        .catch(() => setMessages((prev) => [...prev, { role: 'ai', text: '서버에 연결할 수 없습니다.', error: true }]))
    }
    return () => {
      clearTimeout(timer)
      stopSpeaking()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // ============================================
  // 답변 피드백 (👍 UP / 👎 DOWN)
  // ============================================
  const sendFeedback = async (index, rating) => {

    const target = messages[index]
    if (!target.chatId || target.rating) return

    // 화면에 먼저 반영 (실패하면 되돌림)
    const setRating = (value) =>
      setMessages((prev) => prev.map((m, i) => (i === index ? { ...m, rating: value } : m)))

    setRating(rating)

    try {
      const res = await fetch('/api/ai/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ chatId: target.chatId, rating }),
      })
      const data = await res.json()
      if (!data.success) setRating(null)
    } catch {
      setRating(null)
    }
  }

  // Enter = 보내기, Shift+Enter = 줄바꿈
  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      sendMessage(input)
    }
  }

  // ============================================
  // 화면
  // ============================================
  return (
    <main className="ai-page">
      <div className="ai-card">

        <div className="ai-header">
          <h1 className="ai-title">AI 챗봇</h1>
          <p className="ai-subtitle">
             무엇이든 물어보세요 · 주식은 실제 시세 · 뉴스를 직접 찾아보고 답해요
          </p>
        </div>

        {/* 대화 영역 */}
        <div className="ai-messages">

          {/* 처음: 관심 종목 브리핑 + 예시 질문 */}
          {messages.length === 0 && (
            <div className="ai-welcome">
            <div className="ai-welcome-head">
              <span className="ai-welcome-icon" aria-hidden="true">🤖</span>
              <strong>무엇이든 물어보세요</strong>
              <p>주식뿐 아니라 일상 · 공부 · 글쓰기 질문도 답해요. 🔒 표시는 로그인해야 답할 수 있어요.</p>
            </div>
            <div className="ai-examples">
              {watchNames.length > 0 && (
                <button type="button" className="ai-example" onClick={() => sendMessage(briefingQuestion(watchNames))}>
                  ⭐ 내 관심 종목 브리핑 ({watchNames.length > 2 ? `${watchNames.slice(0, 2).join(', ')} 외 ${watchNames.length - 2}개` : watchNames.join(', ')})
                </button>
              )}
              {EXAMPLES.map((ex) => (
                <button key={ex} type="button" className="ai-example" onClick={() => sendMessage(ex)}>
                  {ex}
                </button>
              ))}
            </div>
            </div>
          )}

          {messages.map((m, i) => (
            <div key={i} className={`ai-msg ai-msg-${m.role}`}>

              {/* 말풍선: AI 답변은 **굵은 글씨** 처리, 내 질문은 그대로 */}
              <div className={`ai-bubble ${m.error ? 'ai-bubble-error' : ''}`}>
                {m.role === 'ai' ? renderText(m.text) : m.text}
              </div>

              {/* 로그인해야 답할 수 있는 질문이었으면 바로 갈 수 있게 */}
              {m.needLogin && (
                <Link to="/login" className="ai-login">🔑 로그인하러 가기</Link>
              )}

              {/* 🔊 이 답 듣기 (브리핑인데 자동으로 소리가 안 났으면 크게) */}
              {m.role === 'ai' && !m.error && canSpeak() && (
                <button type="button" className={m.tapToHear ? 'ai-login' : 'ai-speak'} onClick={() => speak(m.speech ?? m.text)} aria-label="이 답을 소리로 듣기">{m.tapToHear ? '▶ 눌러서 듣기' : '🔊 듣기'}</button>
              )}

              {/* AI가 사용한 도구 */}
              {m.tools && m.tools.length > 0 && (
                <div className="ai-tools">
                  {m.tools.map((t, j) => (
                    <span key={j} className="ai-tool">🔧 {t}</span>
                  ))}
                </div>
              )}

              {/* 🧾 참고한 자료 (뉴스는 새 창, 종목·계좌는 사이트 안) */}
              {m.sources && m.sources.length > 0 && (
                <details className="ai-sources">
                  <summary>🧾 참고한 자료 {m.sources.length}개</summary>
                  <ul>
                    {m.sources.map((src, j) => (
                      <li key={j}>
                        <span className="ai-src-type">{SOURCE_TYPE[src.type] ?? '자료'}</span>
                        {src.link
                          ? (src.link.startsWith('/')
                            ? <Link to={src.link}>{src.title}</Link>
                            : <a href={src.link} target="_blank" rel="noopener noreferrer">{src.title}</a>)
                          : <span>{src.title}</span>}
                        {src.sub && src.type === 'NEWS' && <small> · {src.sub}</small>}
                      </li>
                    ))}
                  </ul>
                </details>
              )}

              {/* 답변 피드백 */}
              {m.chatId && (
                <div className="ai-feedback">
                  {m.rating ? (
                    <span className="ai-feedback-done">
                      피드백 감사합니다 {m.rating === 'UP' ? '👍' : '👎'}
                    </span>
                  ) : (
                    <>
                      <span className="ai-feedback-label">답변이 도움이 됐나요?</span>
                      <button type="button" className="ai-feedback-btn" onClick={() => sendFeedback(i, 'UP')} aria-label="도움이 됐어요">
                        👍
                      </button>
                      <button type="button" className="ai-feedback-btn" onClick={() => sendFeedback(i, 'DOWN')} aria-label="도움이 안 됐어요">
                        👎
                      </button>
                    </>
                  )}
                </div>
              )}
            </div>
          ))}

          {loading && (
            <div className="ai-msg ai-msg-ai">
              <div className="ai-bubble ai-thinking">데이터를 찾아보는 중...</div>
            </div>
          )}

          <div ref={bottomRef} />
        </div>

        {/* 🔊 음성 대화 켜기/끄기 */}
        {canSpeak() && (
          <div className="ai-talk">
            <button type="button" className={`ai-talk-btn ${talk ? 'ai-talk-on' : ''}`} onClick={toggleTalk} aria-pressed={talk}>
              {talk ? '🔊 음성 대화 켜짐' : '🔈 음성 대화'}
            </button>
            <span>{always ? '🟢 항상 듣는 중이에요. 말하면 바로 묻고, 답한 뒤 다시 들어요. ⏺를 누르면 쉬어요.' : talk ? '🎤로 말하면 바로 묻고, 답을 읽은 뒤 다시 들어요. 말이 없으면 멈춰요.' : '켜면 말로 묻고 소리로 답을 들을 수 있어요.'}</span>
          </div>
        )}

        {/* 입력창 */}
        <div className="ai-input-row">
          <textarea
            className="ai-input"
            rows={2}
            maxLength={500}
            placeholder="무엇이든 물어보세요"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
          />
          <VoiceButton ref={voiceRef} keep={always} onText={setInput} onFinal={(text) => { if (talkRef.current) sendMessage(text, true) }} disabled={loading} />
          <button
            type="button"
            className="btn btn-primary ai-send"
            onClick={() => sendMessage(input)}
            disabled={loading || input.trim() === ''}
          >
            보내기
          </button>
        </div>

      </div>
    </main>
  )
}

export default AiChatPage