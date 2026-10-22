// ============================================
// 관리자: 📮 문의함 (주소: /admin/inquiries)
// - 문의마다 AI 분류: 종류 · 영역 · 심각도 · 한 줄 요약 · 고칠 곳 힌트 · 🤖 자동 수정 후보 · ⚠️ 위험 요청
//   (위험 요청은 서버 규칙이 먼저 잡음 → AI가 괜찮다고 해도 자동 수정 후보가 아님)
// - 답장: AI 초안을 넣고 고쳐서 보내기 / 답변하고 종료 / 답변 없이 종료 → 사용자에게 🔔·📲
// - 🤖 자동 수정 후보(위험 요청 없음)는 [AI로 고치기] → GitHub Actions의 Claude Code가 고치고 테스트 → /admin/autofix 에서 승인
// - 백엔드 API (관리자만): GET /api/admin/inquiries?status=, POST /{id}/reply | /status | /retriage
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminInquiriesPage.css'

const TYPE = { BUG: '🐞 버그', FEATURE: '💡 기능', QUESTION: '❓ 질문', OTHER: '💬 기타', SPAM: '🗑 스팸' }
const AREA = {
  STOCK: '주식 종목', MOCK: '모의투자', LEAGUE: 'AI 리그', PREDICT: '예측 게임', QUIZ: '뉴스 퀴즈', DUEL: '친구 대결', COUPLE: '커플',
  MESSAGES: '메시지', AI_CHAT: 'AI 비서', BEAUTY: '뷰티 랩', NOTIFICATION: '알림', APP: '앱 설치', ACCOUNT: '계정', ADMIN: '관리자', ETC: '기타',
}
const SEV = { HIGH: ['심각', 'iq-high'], MEDIUM: ['보통', 'iq-mid'], LOW: ['가벼움', 'iq-low'] }
const STATUS = { OPEN: '답변 대기', ANSWERED: '답변함', CLOSED: '종료' }
const FILTERS = [['OPEN', '답변 대기'], ['', '전체']]
const FIX = { REQUESTED: '🤖 AI 수정 요청됨', WORKING: '🤖 AI가 고치는 중', READY: '🤖 승인 대기', NO_CHANGE: '🤖 고칠 곳 못 찾음', FAILED: '🤖 AI 수정 실패', MERGED: '✅ AI 수정 배포됨', REJECTED: '🤖 AI 수정 거절함' }
const FIX_ACTIVE = ['REQUESTED', 'WORKING', 'READY']

const time = (ms) => {
  if (!ms) return '-'
  const d = new Date(ms)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
const post = (url, body) =>
  fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body ?? {}) })
    .then((r) => r.json())
    .catch(() => ({ success: false, message: '서버에 연결하지 못했어요.' }))

function AdminInquiriesPage() {

  const [filter, setFilter] = useState('OPEN')
  const [list, setList] = useState(null)
  const [error, setError] = useState('')
  const [drafts, setDrafts] = useState({})
  const [busy, setBusy] = useState(null)
  const [notice, setNotice] = useState('')

  const load = useCallback(() => {
    fetch(`/api/admin/inquiries${filter ? `?status=${filter}` : ''}`)
      .then((r) => r.json())
      .then((j) => {
        if (!j.success) throw new Error(j.message)
        setList(j.inquiries)
        setError('')
      })
      .catch((e) => setError(e.message || '불러오지 못했어요.'))
  }, [filter])

  useEffect(() => { load() }, [load])

  const act = async (id, url, body, done) => {
    setBusy(id)
    const j = await post(url, body)
    setBusy(null)
    if (!j.success) return setNotice(j.message)
    setNotice(done)
    load()
  }

  return (
    <main className="admin-page">
      <AdminTabs />
      <div className="admin-head">
        <h1 className="admin-title">문의함</h1>
        <p className="admin-desc">
          사용자 문의를 AI가 먼저 분류해요. 위험한 요청(권한·비밀번호·삭제·지시 무시 등)은 서버 규칙이 걸러서 자동 수정 후보에서 빠져요.
        </p>
      </div>

      <div className="iq-filters">
        {FILTERS.map(([v, label]) => (
          <button key={label} type="button" className={`iq-filter ${filter === v ? 'iq-filter-on' : ''}`} onClick={() => setFilter(v)}>{label}</button>
        ))}
      </div>

      {notice && <p className="iq-notice" role="status">{notice}</p>}
      {error && <p className="admin-empty admin-error">{error}</p>}
      {!error && list === null && <p className="admin-empty">불러오는 중...</p>}
      {list?.length === 0 && <p className="admin-empty">{filter === 'OPEN' ? '답변을 기다리는 문의가 없어요. 👍' : '아직 문의가 없어요.'}</p>}

      <ul className="iq-list">
        {list?.map((q) => {
          const draft = drafts[q.id] ?? ''
          return (
            <li key={q.id} className="iq-card">
              <div className="iq-head">
                <strong>{q.title}</strong>
                <span className="iq-meta">{q.nickname} · {time(q.insAt)} · {STATUS[q.status]}</span>
              </div>

              <div className="iq-chips">
                {q.triageStatus === 'WAITING' && <span className="iq-chip">🤖 분류 중...</span>}
                {q.aiType && <span className="iq-chip">{TYPE[q.aiType] ?? q.aiType}</span>}
                {q.aiArea && <span className="iq-chip">{AREA[q.aiArea] ?? q.aiArea}</span>}
                {q.aiSeverity && <span className={`iq-chip ${SEV[q.aiSeverity]?.[1] ?? ''}`}>{SEV[q.aiSeverity]?.[0]}</span>}
                {q.autofix && <span className="iq-chip iq-auto">🤖 자동 수정 후보</span>}
                {q.riskFlags && <span className="iq-chip iq-risk">⚠️ {q.riskFlags}</span>}
                {q.triageStatus === 'FAILED' && <span className="iq-chip iq-risk">AI 분류 실패</span>}
                {q.triageStatus === 'RULES' && <span className="iq-chip">규칙으로만 분류</span>}
                {q.autofixStatus && <Link to="/admin/autofix" className="iq-chip iq-fix">{FIX[q.autofixStatus] ?? q.autofixStatus}</Link>}
              </div>

              {q.aiSummary && <p className="iq-summary">📝 {q.aiSummary}</p>}
              <p className="iq-body">{q.body}</p>
              <p className="iq-small">보던 화면: {q.page || '-'} · {q.userAgent ? q.userAgent.slice(0, 80) : '-'}</p>
              {q.aiFixHint && <p className="iq-hint">🔧 {q.aiFixHint}</p>}

              {q.autofix && !q.riskFlags && !FIX_ACTIVE.includes(q.autofixStatus) && q.autofixStatus !== 'MERGED' && (
                <div className="iq-autofix">
                  <span>AI가 코드를 고치고 테스트까지 돌린 뒤 승인을 요청해요. (하루 3번)</span>
                  <button type="button" className="iq-btn iq-primary" disabled={busy === q.id}
                    onClick={() => act(q.id, `/api/admin/inquiries/${q.id}/autofix`, null, 'AI 수정을 시작했어요. 끝나면 📲 알림이 와요 (보통 5~15분).')}>
                    🤖 AI로 고치기
                  </button>
                </div>
              )}

              {q.reply ? (
                <div className="iq-replied"><b>보낸 답변</b><p>{q.reply}</p></div>
              ) : (
                <div className="iq-reply">
                  <textarea rows={3} value={draft} maxLength={2000} placeholder="답변 (사용자에게 🔔·📲 알림으로 가요)"
                    onChange={(e) => setDrafts((d) => ({ ...d, [q.id]: e.target.value }))} />
                  <div className="iq-actions">
                    {q.aiReply && <button type="button" className="iq-btn" onClick={() => setDrafts((d) => ({ ...d, [q.id]: q.aiReply }))}>🤖 AI 초안 넣기</button>}
                    <button type="button" className="iq-btn" disabled={busy === q.id} onClick={() => act(q.id, `/api/admin/inquiries/${q.id}/retriage`, null, 'AI가 다시 분류했어요.')}>다시 분류</button>
                    <span className="iq-gap" />
                    {q.status !== 'CLOSED' && <button type="button" className="iq-btn" disabled={busy === q.id} onClick={() => act(q.id, `/api/admin/inquiries/${q.id}/status`, { status: 'CLOSED' }, '종료했어요.')}>답변 없이 종료</button>}
                    <button type="button" className="iq-btn" disabled={busy === q.id || draft.trim().length < 2} onClick={() => act(q.id, `/api/admin/inquiries/${q.id}/reply`, { reply: draft, close: true }, '답변하고 종료했어요.')}>답변하고 종료</button>
                    <button type="button" className="iq-btn iq-primary" disabled={busy === q.id || draft.trim().length < 2} onClick={() => act(q.id, `/api/admin/inquiries/${q.id}/reply`, { reply: draft, close: false }, '답변을 보냈어요.')}>답변 보내기</button>
                  </div>
                </div>
              )}
            </li>
          )
        })}
      </ul>
    </main>
  )
}

export default AdminInquiriesPage
