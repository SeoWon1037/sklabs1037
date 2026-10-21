// ============================================
// AI 주식 비서 화면 (주소: /ai)
// - 백엔드 API
//   POST /api/ai/chat     { message }         → { success, chatId, answer, toolsUsed }
//   POST /api/ai/feedback { chatId, rating }  → { success }
//   GET  /api/watchlist                       → 관심 종목 (브리핑 버튼용)
// - AI가 어떤 도구(DB 조회, 문서 검색 등)를 썼는지 + 답변 피드백(👍/👎)
// - /ai?q=질문 으로 들어오면 입력창에 질문을 미리 채움 (메인 화면의 "AI에게 묻기")
// - /ai?briefing=1 로 들어오면 관심 종목 브리핑 질문을 미리 채움 (메인 화면의 "AI 브리핑 받기")
// ============================================
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import './AiChatPage.css'

// 예시 질문 (처음 화면에 버튼으로 표시)
const EXAMPLES = [
  '삼성전자 최근 한 달 주가 흐름 알려줘',
  'SK하이닉스는 무슨 사업을 하는 회사야?',
  '🔎 코스닥에서 최근 3일 연속 오른 종목 찾아줘',
  '🔎 시가총액 1조 이상인데 최근 20일 동안 10% 넘게 빠진 종목은?',
  '🔎 어제 거래량이 평소의 3배 넘게 터진 종목 알려줘',
  '삼성전자 요즘 왜 이렇게 떨어졌어? 최근 뉴스로 알려줘',
  '내 모의투자 포트폴리오 어때? 비중이랑 수익/손실 종목 짚어줘',
]

// 관심 종목 브리핑 질문 (기존 AI 비서 도구로 수익률 + 뉴스를 찾아 정리)
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
  const wantBriefing = searchParams.get('briefing') === '1'

  // 대화 목록: { role: 'user' | 'ai', text, tools?, chatId?, rating?, error? }
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState(() => searchParams.get('q') ?? '')
  const [loading, setLoading] = useState(false)

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
  const sendMessage = async (text) => {

    const question = text.trim()
    if (question === '' || loading) return

    setMessages((prev) => [...prev, { role: 'user', text: question }])
    setInput('')
    setLoading(true)

    try {
      const res = await fetch('/api/ai/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: question }),
      })
      const data = await res.json()

      if (data.success) {
        setMessages((prev) => [
          ...prev,
          { role: 'ai', text: data.answer, tools: data.toolsUsed, chatId: data.chatId, rating: null },
        ])
      } else {
        setMessages((prev) => [...prev, { role: 'ai', text: data.message, error: true }])
      }

    } catch {
      setMessages((prev) => [...prev, { role: 'ai', text: '서버에 연결할 수 없습니다.', error: true }])
    } finally {
      setLoading(false)
    }
  }

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
          <h1 className="ai-title">AI 주식 비서</h1>
          <p className="ai-subtitle">
             실제 시세 데이터와 종목 문서, 최근 뉴스를 직접 찾아보고 답해요 · 코스피 대표 12개 종목 지원
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
              <p>시세 · 종목 정보 · 최근 뉴스 · 내 모의투자까지 직접 찾아보고 답해요. 아래 질문을 눌러 바로 시작해도 돼요.</p>
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

              {/* AI가 사용한 도구 */}
              {m.tools && m.tools.length > 0 && (
                <div className="ai-tools">
                  {m.tools.map((t, j) => (
                    <span key={j} className="ai-tool">🔧 {t}</span>
                  ))}
                </div>
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

        {/* 입력창 */}
        <div className="ai-input-row">
          <textarea
            className="ai-input"
            rows={2}
            maxLength={500}
            placeholder="예: 현대차 최근 일주일 거래량 추이는?"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
          />
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