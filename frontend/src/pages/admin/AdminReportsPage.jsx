// ============================================
// 관리자: 토론방 신고 관리 (주소: /admin/reports)
// - 처리 안 된 신고가 있는 글을 신고 많은 순으로
// - 신고가 3개 쌓인 글은 이미 화면에서 가려져 있음 → "삭제" 또는 "문제 없음"(가림 풀기)
// - 백엔드 API (관리자만)
//   GET  /api/admin/reports                       목록
//   POST /api/admin/reports/{postId} { action }   DELETE / DISMISS
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminOpsPage.css'
import './AdminReportsPage.css'

const REASON_LABELS = { SPAM: '도배', ABUSE: '욕설·비방', AD: '광고', FALSE: '허위 정보', ETC: '기타' }

// "SPAM:2,ABUSE:1" → [['도배', 2], ['욕설·비방', 1]]
const parseReasons = (text) =>
  (text || '').split(',').filter(Boolean).map((pair) => {
    const [code, count] = pair.split(':')
    return [REASON_LABELS[code] || code, Number(count)]
  })

const formatTime = (millis) => {
  if (!millis) return '-'
  const d = new Date(millis)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function AdminReportsPage() {

  const [posts, setPosts] = useState(null)   // null = 불러오는 중
  const [blindAt, setBlindAt] = useState(3)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(null)     // 처리 중인 글
  const [notice, setNotice] = useState('')

  const load = useCallback(() => {
    return fetch('/api/admin/reports')
      .then((res) => res.json())
      .then((data) => {
        if (!data.success) throw new Error(data.message)
        setPosts(data.posts)
        setBlindAt(data.blindAt)
        setError('')
      })
      .catch((e) => setError(e.message || '서버에 연결할 수 없습니다.'))
  }, [])

  useEffect(() => {
    let ignore = false
    fetch('/api/admin/reports')
      .then((res) => res.json())
      .then((data) => {
        if (ignore) return
        if (!data.success) throw new Error(data.message)
        setPosts(data.posts)
        setBlindAt(data.blindAt)
      })
      .catch((e) => { if (!ignore) setError(e.message || '서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [])

  const handle = async (post, action) => {
    const question = action === 'DELETE'
      ? '이 글을 삭제할까요? 신고도 함께 정리돼요.'
      : '문제 없는 글로 처리할까요? 가림이 풀리고 신고는 정리돼요.'
    if (!window.confirm(question)) return
    setBusy(post.postId)
    try {
      const res = await fetch(`/api/admin/reports/${post.postId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action }),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.message)
      setNotice(action === 'DELETE' ? '글을 삭제했어요.' : '문제 없음으로 처리했어요.')
      await load()
    } catch (e) {
      setNotice(e.message || '처리하지 못했어요.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <main className="admin-page">
      <AdminTabs reportCount={posts?.length} />

      <div className="admin-head">
        <h1 className="admin-title">신고 관리</h1>
        <p className="admin-desc">
          토론 글 신고를 확인해요. 신고가 {blindAt}개 쌓인 글은 이미 화면에서 가려져 있고, 관리자 메일로도 알려줘요.
        </p>
      </div>

      {notice && <p className="ops-notice" role="status">{notice}</p>}
      {error && <p className="admin-empty admin-error">{error}</p>}
      {!error && posts === null && <p className="admin-empty">불러오는 중...</p>}
      {posts?.length === 0 && <p className="admin-empty">처리할 신고가 없어요. 👍</p>}

      {posts?.length > 0 && (
        <ul className="report-list">
          {posts.map((p) => {
            const blinded = !p.deleted && p.reportCount >= blindAt
            return (
              <li key={p.postId} className="report-card">
                <div className="report-head">
                  <Link to={`/stock?symbol=${p.symbol}&tab=board`} className="report-stock">{p.stockName}</Link>
                  <span className="report-meta">{p.nickname} · {formatTime(p.insAt)}</span>
                  <span className={`report-count ${blinded ? 'report-count-hot' : ''}`}>
                    🚩 {p.reportCount}{blinded ? ' · 가려짐' : ''}{p.deleted ? ' · 작성자가 삭제함' : ''}
                  </span>
                </div>

                <p className="report-content">{p.content}</p>

                <div className="report-reasons">
                  {parseReasons(p.reasons).map(([label, count]) => (
                    <span key={label} className="report-reason">{label} {count}</span>
                  ))}
                  <span className="report-first">처음 신고 {formatTime(p.firstReportAt)}</span>
                </div>

                {p.memos && <p className="report-memos">“{p.memos}”</p>}

                <div className="report-actions">
                  <button type="button" className="ops-btn" onClick={() => handle(p, 'DISMISS')} disabled={busy === p.postId}>
                    문제 없음
                  </button>
                  <button type="button" className="ops-btn report-delete" onClick={() => handle(p, 'DELETE')} disabled={busy === p.postId}>
                    {p.deleted ? '신고 정리' : '글 삭제'}
                  </button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </main>
  )
}

export default AdminReportsPage
