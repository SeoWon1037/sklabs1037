// ============================================
// 관리자: ♻️ 모의투자 계좌 초기화 신청 (주소: /admin/resets)
// - 기다리는 신청: 닉네임 · 이유 · 지금 계좌(총자산 · 수익률 · 보유 종목 수 · 지난 초기화 횟수)
//   → "승인" 누르면 바로 초기화 (보유 · 주문 · 거래 · 그래프 지우고 3,000만 원) / "거절"은 한마디와 함께
// - 아래에 최근 처리 30개
// - 백엔드 API (관리자만): GET /api/admin/resets, POST /api/admin/resets/{id}/approve | reject { note }
// ============================================
import { useEffect, useState } from 'react'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminOpsPage.css'
import './AdminReportsPage.css'

const STATUS = { PENDING: '기다리는 중', APPROVED: '승인 · 초기화함', REJECTED: '거절' }

const formatTime = (millis) => {
  if (!millis) return '-'
  const d = new Date(millis)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
const won = (v) => (v == null ? '-' : `${Math.round(Number(v)).toLocaleString('ko-KR')}원`)
const pct = (v) => (v == null ? '-' : `${Number(v) > 0 ? '+' : ''}${Number(v).toFixed(2)}%`)

function AdminResetsPage() {

  const [list, setList] = useState(null)
  const [pending, setPending] = useState(0)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(null)
  const [notes, setNotes] = useState({})     // 거절 한마디 (신청 id별)
  const [notice, setNotice] = useState('')

  useEffect(() => {
    let ignore = false
    fetch('/api/admin/resets')
      .then((res) => res.json())
      .then((data) => {
        if (ignore) return
        if (!data.success) throw new Error(data.message)
        setList(data.requests)
        setPending(data.pending)
      })
      .catch((e) => { if (!ignore) setError(e.message || '서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [])

  const handle = async (r, action) => {
    setBusy(r.id)
    setNotice('')
    try {
      const res = await fetch(`/api/admin/resets/${r.id}/${action}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ note: notes[r.id] || '' }),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.message)
      setList(data.requests)
      setPending(data.pending)
      setNotice(action === 'approve' ? `${r.nickname}님 계좌를 초기화했어요.` : `${r.nickname}님 신청을 거절했어요.`)
    } catch (e) {
      setNotice(e.message || '처리하지 못했어요.')
    } finally {
      setBusy(null)
    }
  }

  const open = list?.filter((r) => r.status === 'PENDING') || []
  const done = list?.filter((r) => r.status !== 'PENDING') || []

  return (
    <main className="admin-page">
      <AdminTabs />

      <div className="admin-head">
        <h1 className="admin-title">계좌 초기화 신청</h1>
        <p className="admin-desc">
          모의투자 계좌 초기화는 관리자가 승인해야 돼요. 승인하면 그 자리에서 보유 종목·주문·거래 내역·수익률 그래프가 지워지고 3,000만 원으로 다시 시작해요.
        </p>
      </div>

      {notice && <p className="ops-notice" role="status">{notice}</p>}
      {error && <p className="admin-empty admin-error">{error}</p>}
      {!error && list === null && <p className="admin-empty">불러오는 중...</p>}
      {list && pending === 0 && <p className="admin-empty">기다리는 신청이 없어요. 👍</p>}

      {open.length > 0 && (
        <ul className="report-list">
          {open.map((r) => (
            <li key={r.id} className="report-card">
              <div className="report-head">
                <strong className="report-stock">{r.nickname}</strong>
                <span className="report-meta">신청 {formatTime(r.insAt)}</span>
              </div>
              <p className="report-content">{r.reason || '(이유 없이 신청)'}</p>
              {r.account && (
                <div className="report-reasons">
                  <span className="report-reason">총자산 {won(r.account.totalAsset)}</span>
                  <span className="report-reason">수익률 {pct(r.account.returnRate)}</span>
                  <span className="report-reason">보유 {r.account.holdings}종목</span>
                  <span className="report-reason">지난 초기화 {r.account.resetCount}번</span>
                </div>
              )}
              <div className="report-actions">
                <input
                  className="reset-note"
                  value={notes[r.id] || ''}
                  onChange={(e) => setNotes({ ...notes, [r.id]: e.target.value })}
                  placeholder="거절할 때 한마디 (선택)"
                  maxLength={200}
                />
                <button type="button" className="ops-btn" onClick={() => handle(r, 'reject')} disabled={busy === r.id}>거절</button>
                <button type="button" className="ops-btn report-delete" onClick={() => handle(r, 'approve')} disabled={busy === r.id}>
                  {busy === r.id ? '처리 중...' : '승인 · 초기화'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {done.length > 0 && (
        <section className="reset-done">
          <h2 className="admin-title reset-done-title">최근 처리</h2>
          <ul className="reset-done-list">
            {done.map((r) => (
              <li key={r.id}>
                <b>{r.nickname}</b>
                <span className={`reset-status reset-${r.status.toLowerCase()}`}>{STATUS[r.status]}</span>
                <span className="report-meta">{formatTime(r.handledAt)}</span>
                {r.note && <span className="report-meta">· {r.note}</span>}
              </li>
            ))}
          </ul>
        </section>
      )}
    </main>
  )
}

export default AdminResetsPage
