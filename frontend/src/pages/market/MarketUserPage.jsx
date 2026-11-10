// ============================================
// 🛍️ 판매자 프로필 (주소: /market/user/:id, 로그인 없이 누구나)
// - 매너 온도(받은 거래 후기로 계산) · 받은 후기 · 판매 상품
// - 백엔드: GET /api/market/users/{id}
// ============================================
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import MarketCard from './MarketCard'
import { Temp } from './MarketItemPage'
import { getJson, timeAgo } from './marketUtil'
import './Market.css'

const FACE = { 3: ['😊', '좋아요'], 2: ['🙂', '보통이에요'], 1: ['😞', '별로예요'] }

function MarketUserPage() {

  const { id } = useParams()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [now, setNow] = useState(0)

  useEffect(() => {
    let ignore = false
    getJson(`/api/market/users/${id}`).then((j) => {
      if (ignore) return
      if (!j.success) return setError(j.message || '프로필을 불러오지 못했어요.')
      setError('')
      setData(j)
      setNow(Date.now())
    })
    return () => { ignore = true }
  }, [id])

  if (error) {
    return (
      <main className="mk-page">
        <div className="mk-empty">
          <span aria-hidden="true">🫥</span>
          <strong>{error}</strong>
          <Link to="/market" className="mk-sell">장터로 돌아가기</Link>
        </div>
      </main>
    )
  }
  if (!data) return <main className="mk-page"><p className="mk-muted">불러오는 중...</p></main>

  const { seller, reviews, items } = data
  const joined = new Date(seller.joinedAt)

  return (
    <main className="mk-page">
      <Link to="/market" className="mk-back">‹ 장터로</Link>

      <section className="mk-profile">
        <span className="mk-avatar mk-avatar-lg" aria-hidden="true">{String(seller.nickname ?? '?').slice(0, 1)}</span>
        <div className="mk-profile-name">
          <h1>{seller.nickname}</h1>
          <p className="mk-muted">{joined.getFullYear()}년 {joined.getMonth() + 1}월 가입 · 판매중 {seller.itemCount} · 거래완료 {seller.soldCount}</p>
        </div>
        <Temp temp={seller.temp} />
      </section>

      <ul className="mk-review-stats" aria-label="받은 후기">
        <li><i aria-hidden="true">😊</i>좋아요 <b>{seller.good}</b></li>
        <li><i aria-hidden="true">🙂</i>보통이에요 <b>{seller.ok}</b></li>
        <li><i aria-hidden="true">😞</i>별로예요 <b>{seller.bad}</b></li>
      </ul>
      <p className="mk-muted">매너 온도는 36.5℃에서 시작해서, 거래 후기를 받을 때마다 좋아요 +0.5 · 보통 +0.1 · 별로예요 −1.0 만큼 바뀌어요.</p>

      <section className="mk-others">
        <h2>받은 거래 후기 {reviews.length > 0 && <small className="mk-muted">최근 {reviews.length}개</small>}</h2>
        {reviews.length === 0 && <p className="mk-muted">아직 받은 후기가 없어요.</p>}
        <ul className="mk-reviews">
          {reviews.map((r, i) => (
            <li key={`${r.at}-${i}`}>
              <div className="mk-review-head">
                <span><i aria-hidden="true">{FACE[r.rating]?.[0]}</i> <b>{FACE[r.rating]?.[1]}</b></span>
                <small className="mk-muted">{r.writerName} · {r.writerRole === 'BUYER' ? '구매자' : '판매자'} · {timeAgo(r.at, now)}</small>
              </div>
              {r.tags && <ul className="mk-tags">{r.tags.split(',').map((t) => <li key={t}>{t}</li>)}</ul>}
              {r.comment && <p>{r.comment}</p>}
              <small className="mk-muted">거래 상품: {r.itemTitle}</small>
            </li>
          ))}
        </ul>
      </section>

      <section className="mk-others">
        <h2>판매 상품 {items.length > 0 && <small className="mk-muted">{items.length}개</small>}</h2>
        {items.length === 0 && <p className="mk-muted">올린 상품이 없어요.</p>}
        <div className="mk-grid">{items.map((it) => <MarketCard key={it.id} item={it} now={now} />)}</div>
      </section>
    </main>
  )
}

export default MarketUserPage
