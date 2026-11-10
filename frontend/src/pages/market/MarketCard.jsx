// ============================================
// 🛍️ 상품 카드 (목록 한 칸) — 사진 · 제목 · 가격(📉 내렸으면 표시) · 지역·시간 · 찜/채팅 수
// ============================================
import { Link } from 'react-router-dom'
import { CATEGORY_ICON, priceText, timeAgo } from './marketUtil'

function MarketCard({ item, now }) {

  const closed = item.status !== 'SALE'

  return (
    <Link to={`/market/${item.id}`} className={`mk-card ${closed ? 'mk-card-closed' : ''}`}>
      <div className="mk-thumb">
        {item.thumb
          ? <img src={item.thumb} alt="" loading="lazy" decoding="async" />
          : <span className="mk-thumb-empty" aria-hidden="true">{CATEGORY_ICON[item.category] ?? '📦'}</span>}
        {closed && <span className={`mk-badge mk-badge-${item.status}`}>{item.statusName}</span>}
      </div>
      <div className="mk-card-body">
        <p className="mk-card-title">{item.title}</p>
        <p className="mk-card-price">
          {priceText(item.price)}
          {item.prevPrice > item.price && <span className="mk-drop" title={`전 가격 ${priceText(item.prevPrice)}`}>↓ 내림</span>}
        </p>
        <p className="mk-card-meta">
          <span>{[item.region, timeAgo(item.bumpAt, now)].filter(Boolean).join(' · ')}</span>
          {(item.likes > 0 || item.chats > 0) && (
            <span className="mk-card-counts">
              {item.chats > 0 && <>💬 {item.chats}</>}
              {item.likes > 0 && <> ♥ {item.likes}</>}
            </span>
          )}
        </p>
      </div>
    </Link>
  )
}

export default MarketCard
