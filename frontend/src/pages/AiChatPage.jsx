// ============================================
// AI 주식 비서 화면 (주소: /ai)
// - 백엔드 API: POST /api/ai/chat { message } → { success, answer, toolsUsed }
// - AI가 어떤 도구(DB 조회, 문서 검색 등)를 썼는지도 함께 보여줌
// ============================================
import { useEffect, useRef, useState } from 'react'
import './AiChatPage.css'

// 예시 질문 (처음 화면에 버튼으로 표시)
const EXAMPLES = [
  '삼성전자 최근 한 달 주가 흐름 알려줘',
  'SK하이닉스는 무슨 사업을 하는 회사야?',
  '지원하는 종목 중 최근 20일 수익률이 가장 높은 건?',
  '카카오랑 NAVER 현재가 비교해줘',
]

function AiChatPage() {

  // 대화 목록: { role: 'user' | 'ai', text, tools? }
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)

  // 새 메시지가 오면 맨 아래로 스크롤
  const bottomRef = useRef(null)
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

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
        setMessages((prev) => [...prev, { role: 'ai', text: data.answer, tools: data.toolsUsed }])
      } else {
        setMessages((prev) => [...prev, { role: 'ai', text: data.message, error: true }])
      }

    } catch {
      setMessages((prev) => [...prev, { role: 'ai', text: '서버에 연결할 수 없습니다.', error: true }])
    } finally {
      setLoading(false)
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
            실제 시세 데이터와 종목 문서를 직접 찾아보고 답해요 · 지원 종목: 삼성전자, SK하이닉스, NAVER, 카카오, 현대차
          </p>
        </div>

        {/* 대화 영역 */}
        <div className="ai-messages">

          {/* 처음: 예시 질문 */}
          {messages.length === 0 && (
            <div className="ai-examples">
              {EXAMPLES.map((ex) => (
                <button key={ex} type="button" className="ai-example" onClick={() => sendMessage(ex)}>
                  {ex}
                </button>
              ))}
            </div>
          )}

          {messages.map((m, i) => (
            <div key={i} className={`ai-msg ai-msg-${m.role}`}>
              <div className={`ai-bubble ${m.error ? 'ai-bubble-error' : ''}`}>{m.text}</div>

              {/* AI가 사용한 도구 */}
              {m.tools && m.tools.length > 0 && (
                <div className="ai-tools">
                  {m.tools.map((t, j) => (
                    <span key={j} className="ai-tool">🔧 {t}</span>
                  ))}
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