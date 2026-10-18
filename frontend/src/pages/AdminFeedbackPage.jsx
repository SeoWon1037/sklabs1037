// ============================================
// 관리자: AI 피드백 대시보드 (주소: /admin)
// - 백엔드 API (관리자만)
//   GET /api/admin/feedback                  요약 / 도구별 / 날짜별
//   GET /api/admin/feedback/chats?rating=    대화 목록
// - 👎 받은 답변을 모아 보고 → 프롬프트·도구 개선에 활용
// ============================================
import { useEffect, useState } from 'react'
import './AdminFeedbackPage.css'

// 목록 필터
const FILTERS = [
  { value: 'DOWN', label: '👎 개선 대상' },
  { value: 'UP', label: '👍 좋은 답변' },
  { value: '', label: '전체' },
]

const formatDateTime = (text) => (text ? text.replace('T', ' ').slice(0, 16) : '-')

function AdminFeedbackPage() {

  const [dashboard, setDashboard] = useState(null)
  const [error, setError] = useState('')

  const [filter, setFilter] = useState('DOWN')
  const [chats, setChats] = useState({ key: null, items: [] })

  // 요약 / 도구별 / 날짜별
  useEffect(() => {
    fetch('/api/admin/feedback')
      .then((res) => res.json())
      .then((data) => (data.success ? setDashboard(data) : setError(data.message)))
      .catch(() => setError('서버에 연결할 수 없습니다.'))
  }, [])

  // 필터가 바뀔 때마다 대화 목록
  useEffect(() => {

    let ignore = false

    fetch(`/api/admin/feedback/chats?rating=${filter}`)
      .then((res) => res.json())
      .then((data) => {
        if (!ignore && data.success) setChats({ key: filter, items: data.chats })
      })
      .catch(() => {})

    return () => { ignore = true }

  }, [filter])

  if (error) {
    return (
      <main className="admin-page">
        <p className="admin-empty admin-error">{error}</p>
      </main>
    )
  }

  if (!dashboard) {
    return (
      <main className="admin-page">
        <p className="admin-empty">불러오는 중...</p>
      </main>
    )
  }

  const { summary, tools, daily } = dashboard
  const rated = summary.upCount + summary.downCount
  const satisfaction = rated > 0 ? Math.round((summary.upCount / rated) * 100) : null
  const maxTool = Math.max(1, ...tools.map((t) => t.useCount))
  const maxDaily = Math.max(1, ...daily.map((d) => d.chatCount))

  return (
    <main className="admin-page">

      <div className="admin-head">
        <h1 className="admin-title">AI 피드백 대시보드</h1>
        <p className="admin-desc">사용자 질문·AI 도구 사용·👍/👎 평가를 모아 보고, 👎 답변을 기준으로 프롬프트와 도구를 개선합니다.</p>
      </div>

      {/* ===== 요약 숫자 ===== */}
      <section className="admin-stats">
        <div className="admin-stat"><span>전체 질문</span><strong>{summary.totalCount}</strong></div>
        <div className="admin-stat"><span>👍 도움됨</span><strong>{summary.upCount}</strong></div>
        <div className="admin-stat"><span>👎 아쉬움</span><strong>{summary.downCount}</strong></div>
        <div className="admin-stat"><span>만족도 (평가한 답변 중)</span><strong>{satisfaction === null ? '-' : `${satisfaction}%`}</strong></div>
        <div className="admin-stat"><span>질문당 평균 도구</span><strong>{summary.avgTools ?? '-'}개</strong></div>
      </section>

      <div className="admin-grid">

        {/* ===== 도구별 사용 횟수 ===== */}
        <section className="admin-box">
          <h2 className="admin-box-title">도구별 사용 횟수</h2>
          {tools.length === 0 && <p className="admin-empty">아직 기록이 없습니다.</p>}
          <ul className="admin-bars">
            {tools.map((t) => (
              <li key={t.toolName} className="admin-bar-row">
                <span className="admin-bar-label">{t.toolName}</span>
                <div className="admin-bar-track">
                  <div className="admin-bar" style={{ width: `${(t.useCount / maxTool) * 100}%` }} />
                </div>
                <span className="admin-bar-value">{t.useCount}</span>
              </li>
            ))}
          </ul>
        </section>

        {/* ===== 최근 7일 ===== */}
        <section className="admin-box">
          <h2 className="admin-box-title">최근 7일 질문 수</h2>
          <div className="admin-columns">
            {daily.map((d) => (
              <div key={d.statDate} className="admin-column">
                <span className="admin-column-value">{d.chatCount}</span>
                <div className="admin-column-track">
                  <div className="admin-column-bar" style={{ height: `${(d.chatCount / maxDaily) * 100}%` }} />
                </div>
                <span className="admin-column-label">{d.statDate.slice(5)}</span>
                <span className="admin-column-rating">👍{d.upCount} 👎{d.downCount}</span>
              </div>
            ))}
          </div>
        </section>
      </div>

      {/* ===== 대화 목록 ===== */}
      <section className="admin-box">
        <div className="admin-list-head">
          <h2 className="admin-box-title">대화 기록</h2>
          <div className="admin-filters">
            {FILTERS.map((f) => (
              <button
                key={f.value}
                type="button"
                className={`admin-filter ${filter === f.value ? 'admin-filter-active' : ''}`}
                onClick={() => setFilter(f.value)}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {chats.key === filter && chats.items.length === 0 && <p className="admin-empty">해당하는 대화가 없습니다.</p>}

        <ul className="admin-chats">
          {chats.items.map((c) => (
            <li key={c.chatId} className="admin-chat">
              <details>
                <summary className="admin-chat-summary">
                  <span className="admin-chat-rating">{c.rating === 'UP' ? '👍' : c.rating === 'DOWN' ? '👎' : '·'}</span>
                  <span className="admin-chat-question">{c.question}</span>
                  <span className="admin-chat-meta">{c.nickname ?? '-'} · {formatDateTime(c.insDay)}</span>
                </summary>
                <div className="admin-chat-detail">
                  <p className="admin-chat-tools">🔧 {c.toolsUsed || '도구 사용 없음'}</p>
                  <p className="admin-chat-answer">{c.answer}</p>
                </div>
              </details>
            </li>
          ))}
        </ul>
      </section>
    </main>
  )
}

export default AdminFeedbackPage