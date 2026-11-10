// ============================================
// 관리자: 🛍️ 장터 관리 (주소: /admin/market)
// - 신고가 들어온 상품 · 신고 3번으로 숨겨진 상품을 신고 이유와 함께 봄
// - [문제없음 · 다시 보이기]: 숨김을 풀고 신고 기록을 지움 (판매자에게 알림)
//   [삭제]: 상품을 지움 (되돌릴 수 없음)
// - 백엔드 (관리자만): GET /api/market/admin/reported, POST /api/market/admin/{id}/restore, POST /api/market/items/{id}/delete
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import AdminTabs from '../../components/admin/AdminTabs'
import { getJson, postJson, priceText } from '../market/marketUtil'
import './AdminFeedbackPage.css'
import './AdminInquiriesPage.css'

function AdminMarketPage() {

  const [items, setItems] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(null)
  const [notice, setNotice] = useState('')

  const load = useCallback(() => {
    getJson('/api/market/admin/reported').then((j) => {
      if (!j.success) return setError(j.message || '불러오지 못했어요.')
      setError('')
      setItems(j.items)
    })
  }, [])

  useEffect(() => { load() }, [load])

  const act = async (id, url, done, ask) => {
    if (ask && !window.confirm(ask)) return
    setBusy(id)
    const j = await postJson(url)
    setBusy(null)
    setNotice(j.success ? done : j.message)
    load()
  }

  return (
    <main className="admin-page">
      <AdminTabs />
      <div className="admin-head">
        <h1 className="admin-title">장터 관리</h1>
        <p className="admin-desc">
          신고가 들어온 상품과, 신고 3번으로 자동으로 숨겨진 상품이에요. 살펴보고 문제가 없으면 다시 보이게, 문제가 있으면 지워주세요.
        </p>
      </div>

      {notice && <p className="iq-notice" role="status">{notice}</p>}
      {error && <p className="admin-empty admin-error">{error}</p>}
      {!error && items === null && <p className="admin-empty">불러오는 중...</p>}
      {items?.length === 0 && <p className="admin-empty">신고된 상품이 없어요. 👍</p>}

      <ul className="iq-list">
        {items?.map((it) => (
          <li key={it.id} className="iq-card">
            <div className="iq-head">
              <strong><Link to={`/market/${it.id}`}>{it.title}</Link></strong>
              <span className="iq-meta">{priceText(it.price)} · 판매자 {it.sellerName}</span>
            </div>
            <div className="iq-chips">
              <span className={`iq-chip ${it.status === 'HIDDEN' ? 'iq-risk' : ''}`}>{it.status === 'HIDDEN' ? '숨겨짐' : it.statusName}</span>
              <span className="iq-chip iq-mid">신고 {it.reports}번</span>
            </div>
            {it.reasons && <p className="iq-body">신고 이유: {it.reasons}</p>}
            <div className="iq-actions">
              <span className="iq-gap" />
              <button type="button" className="iq-btn" disabled={busy === it.id}
                onClick={() => act(it.id, `/api/market/admin/${it.id}/restore`, '다시 보이게 했어요. 신고 기록도 지웠어요.')}>
                문제없음 · 다시 보이기
              </button>
              <button type="button" className="iq-btn iq-primary" disabled={busy === it.id}
                onClick={() => act(it.id, `/api/market/items/${it.id}/delete`, '상품을 지웠어요.', `"${it.title}" 상품을 지울까요? 되돌릴 수 없어요.`)}>
                🗑 삭제
              </button>
            </div>
          </li>
        ))}
      </ul>
    </main>
  )
}

export default AdminMarketPage
