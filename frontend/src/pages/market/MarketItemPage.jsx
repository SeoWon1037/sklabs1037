// ============================================
// 🛍️ 상품 상세 (주소: /market/:id, 로그인 없이 누구나)
// - 사진(옆으로 넘기기) · 가격 · 상태 · 설명 · 판매자 · 판매자의 다른 상품
// - 사는 사람: [채팅하기] → 판매자와 1:1 메시지 (/messages?room=) · ♥ 찜 · 신고
// - 판매자: 판매중/예약중/판매완료 · 끌어올리기(24시간에 한 번) · 수정 · 삭제
// - 관리자: 어떤 상품이든 삭제
// - ⭐ 판매완료를 누르면 "누구와 거래했나요?"(채팅한 사람 중에서) → 두 사람이 서로 후기 → 매너 온도
// - 📉 가격을 내렸으면 전 가격 표시 · 🔗 공유(휴대폰은 공유 시트, PC는 주소 복사) · 🕘 최근 본 상품에 기록
// - 💰 가격 제안: 사는 사람이 낮은 가격을 제안(상품당 3번) → 판매자가 수락(채팅방으로)/거절 · 📊 비슷한 상품 시세
// - 백엔드: POST …/offer | offer/cancel, POST /api/market/offers/{id}/answer
// - 백엔드: GET /api/market/items/{id} | …/buyers, POST …/like | chat | status | sold | review | bump | delete | report
// ============================================
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import MarketCard from './MarketCard'
import { BAD_TAGS, CATEGORY_ICON, GOOD_TAGS, RATINGS, getJson, guideText, postJson, priceText, pushRecent, tempLook, timeAgo } from './marketUtil'
import './Market.css'

const STATUS_BUTTONS = [['SALE', '판매중'], ['RESERVED', '예약중'], ['SOLD', '판매완료']]

function Gallery({ photos, title, category }) {

  const [index, setIndex] = useState(0)
  const scroller = useRef(null)

  const onScroll = () => {
    const el = scroller.current
    if (el) setIndex(Math.round(el.scrollLeft / el.clientWidth))
  }
  const go = (i) => scroller.current?.scrollTo({ left: i * scroller.current.clientWidth, behavior: 'smooth' })

  if (photos.length === 0) {
    return <div className="mk-gallery"><div className="mk-photo mk-photo-empty" aria-hidden="true">{CATEGORY_ICON[category] ?? '📦'}</div></div>
  }

  return (
    <div className="mk-gallery">
      <div className="mk-photos" ref={scroller} onScroll={onScroll}>
        {photos.map((src, i) => (
          <div key={src} className="mk-photo"><img src={src} alt={`${title} 사진 ${i + 1}`} loading={i === 0 ? 'eager' : 'lazy'} /></div>
        ))}
      </div>
      {photos.length > 1 && (
        <>
          <span className="mk-photo-count">{index + 1} / {photos.length}</span>
          <div className="mk-dots">
            {photos.map((src, i) => (
              <button key={src} type="button" className={i === index ? 'mk-dot-on' : ''} onClick={() => go(i)} aria-label={`사진 ${i + 1} 보기`} />
            ))}
          </div>
        </>
      )}
    </div>
  )
}

// 판매자 카드 속 매너 온도 (숫자 + 막대)
export function Temp({ temp }) {

  const { color, face } = tempLook(temp)
  return (
    <span className="mk-temp" style={{ '--temp': color }} title="매너 온도: 받은 거래 후기로 계산해요 (36.5도에서 시작)">
      <b>{Number(temp).toFixed(1)}℃ <i aria-hidden="true">{face}</i></b>
      <span className="mk-temp-bar" aria-hidden="true"><span style={{ width: `${Math.min(100, Number(temp))}%` }} /></span>
    </span>
  )
}

// ⭐ 거래 후기 쓰기 (판매자 ↔ 거래한 사람)
function ReviewForm({ who, busy, onSubmit }) {

  const [rating, setRating] = useState(0)
  const [tags, setTags] = useState([])
  const [comment, setComment] = useState('')
  const options = rating === 1 ? BAD_TAGS : GOOD_TAGS

  const pick = (r) => {
    setRating(r)
    setTags([])
  }
  const toggle = (t) => setTags((prev) => (prev.includes(t) ? prev.filter((x) => x !== t) : prev.length < 3 ? [...prev, t] : prev))

  return (
    <div className="mk-review-form">
      <strong>⭐ {who}님과의 거래는 어떠셨나요?</strong>
      <div className="mk-rating" role="group" aria-label="거래 평가">
        {RATINGS.map(([value, face, label]) => (
          <button key={value} type="button" className={rating === value ? 'mk-rating-on' : ''} aria-pressed={rating === value} onClick={() => pick(value)}>
            <i aria-hidden="true">{face}</i>{label}
          </button>
        ))}
      </div>
      {rating > 0 && (
        <>
          <div className="mk-chips">
            {options.map((t) => (
              <button key={t} type="button" className={tags.includes(t) ? 'mk-chip-on' : ''} aria-pressed={tags.includes(t)} onClick={() => toggle(t)}>{t}</button>
            ))}
          </div>
          <textarea value={comment} onChange={(e) => setComment(e.target.value)} rows={2} maxLength={300}
            placeholder={rating === 1 ? '어떤 점이 아쉬웠나요? (선택 · 상대에게 알림은 가지 않아요)' : '한마디 남기기 (선택)'} aria-label="후기 한마디" />
          <button type="button" className="mk-chat" disabled={busy} onClick={() => onSubmit({ rating, tags, comment })}>후기 남기기</button>
        </>
      )}
    </div>
  )
}

// 💰 사는 사람: 가격 제안 보내기 / 보낸 제안 상태
const OFFER_STATUS = { PENDING: '판매자의 답을 기다리고 있어요', ACCEPTED: '수락됐어요! 채팅으로 거래를 이어가세요', DECLINED: '거절됐어요', CANCELED: '취소했어요' }

function OfferBox({ item, busy, onSend, onCancel }) {

  const [open, setOpen] = useState(false)
  const [price, setPrice] = useState('')
  const mine = item.myOffer
  const pending = mine?.status === 'PENDING'
  const num = Number(price)
  const valid = num > 0 && num < item.price && num * 2 >= item.price

  if (item.status !== 'SALE' || Number(item.price) === 0) return null

  return (
    <div className="mk-offer">
      {mine && (
        <p className={`mk-offer-state mk-offer-${mine.status}`}>
          💰 내 제안 <b>{priceText(mine.price)}</b> · {OFFER_STATUS[mine.status]}
          {pending && <button type="button" className="mk-link-btn" disabled={busy} onClick={onCancel}> 취소</button>}
        </p>
      )}
      {open ? (
        <form className="mk-report-form" onSubmit={(e) => { e.preventDefault(); if (valid) onSend(num, () => { setOpen(false); setPrice('') }) }}>
          <input value={price} onChange={(e) => setPrice(e.target.value.replace(/[^0-9]/g, '').slice(0, 8))} inputMode="numeric"
            placeholder={`${Math.ceil(item.price / 2).toLocaleString('ko-KR')}원 이상 ~ 판매가 미만`} aria-label="제안할 가격" />
          <button type="submit" className="mk-btn" disabled={busy || !valid}>보내기</button>
          <button type="button" className="mk-btn" onClick={() => setOpen(false)}>닫기</button>
        </form>
      ) : item.offersLeft > 0 ? (
        <button type="button" className="mk-btn mk-offer-open" onClick={() => setOpen(true)}>
          💰 가격 제안하기 <small>{pending ? '다시 보내면 앞의 제안은 취소돼요' : `${item.offersLeft}번 남음`}</small>
        </button>
      ) : (
        !pending && <p className="mk-muted">이 상품에는 제안을 모두 썼어요. 채팅으로 이야기해보세요.</p>
      )}
      {open && price !== '' && !valid && <p className="mk-muted">판매가보다 낮고, 판매가의 절반 이상인 가격만 보낼 수 있어요.</p>}
    </div>
  )
}

// 💰 판매자: 받은 제안들 (높은 가격부터)
function OfferList({ offers, price, now, busy, onAnswer }) {

  if (!offers?.length) return null
  return (
    <div className="mk-sold" role="group" aria-label="받은 가격 제안">
      <strong>💰 받은 가격 제안 {offers.length}개</strong>
      <p className="mk-muted">수락하면 그분과의 채팅방에 수락 메시지가 가요. 상품 가격과 상태는 그대로예요.</p>
      <ul className="mk-offer-list">
        {offers.map((o) => (
          <li key={o.id}>
            <span>
              <b>{priceText(o.price)}</b> <small className="mk-muted">−{Math.round((1 - o.price / price) * 100)}%</small>
              <small className="mk-muted">{o.buyerName} · {timeAgo(o.at, now)}</small>
            </span>
            <button type="button" className="mk-btn" disabled={busy} onClick={() => onAnswer(o.id, false)}>거절</button>
            <button type="button" className="mk-btn mk-btn-primary" disabled={busy} onClick={() => onAnswer(o.id, true)}>수락</button>
          </li>
        ))}
      </ul>
    </div>
  )
}

// 판매완료: 누구와 거래했는지 고르기
function SoldPicker({ buyers, busy, onPick, onCancel }) {

  return (
    <div className="mk-sold" role="group" aria-label="거래한 사람 고르기">
      <strong>🤝 누구와 거래했나요?</strong>
      <p className="mk-muted">고르면 두 분이 서로 후기를 남길 수 있어요.</p>
      {buyers === null && <p className="mk-muted">불러오는 중...</p>}
      {buyers?.length === 0 && <p className="mk-muted">이 상품으로 채팅한 사람이 아직 없어요.</p>}
      <div className="mk-sold-list">
        {buyers?.map((b) => (
          <button key={b.userId} type="button" className="mk-btn" disabled={busy} onClick={() => onPick(b.userId)}>
            <span className="mk-avatar mk-avatar-sm" aria-hidden="true">{String(b.nickname).slice(0, 1)}</span>{b.nickname}
          </button>
        ))}
      </div>
      <div className="mk-owner-row">
        <button type="button" className="mk-btn" disabled={busy} onClick={() => onPick(null)}>고르지 않고 판매완료</button>
        <button type="button" className="mk-btn" disabled={busy} onClick={onCancel}>취소</button>
      </div>
    </div>
  )
}

function MarketItemPage() {

  const { id } = useParams()
  const navigate = useNavigate()
  const [item, setItem] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState('')
  const [now, setNow] = useState(0)
  const [reporting, setReporting] = useState(false)
  const [reason, setReason] = useState('')
  const [buyers, setBuyers] = useState(undefined)   // undefined: 닫힘 / null: 불러오는 중 / []: 후보

  const load = useCallback(() => {
    return getJson(`/api/market/items/${id}`).then((j) => {
      if (!j.success) return setError(j.message || '상품을 불러오지 못했어요.')
      setError('')
      setItem(j)
      setNow(Date.now())
    })
  }, [id])

  useEffect(() => {
    let ignore = false
    getJson(`/api/market/items/${id}`).then((j) => {
      if (ignore) return
      if (!j.success) return setError(j.message || '상품을 불러오지 못했어요.')
      setError('')
      setItem(j)
      setNow(Date.now())
      setBuyers(undefined)
      pushRecent(j)
      window.scrollTo(0, 0)
    })
    return () => { ignore = true }
  }, [id])

  // 로그인이 필요한 요청: 401이면 로그인 화면으로
  const act = async (name, path, body, done) => {
    setBusy(name)
    setNotice('')
    const j = await postJson(`/api/market/items/${id}${path}`, body)
    setBusy('')
    if (!j.success) {
      if (/로그인/.test(j.message ?? '')) return navigate('/login')
      return setNotice(j.message)
    }
    return done ? done(j) : load()
  }

  const chat = () => act('chat', '/chat', null, (j) => navigate(`/messages?room=${j.roomId}`))
  const like = () => act('like', '/like', { on: !item.liked }, (j) => setItem((prev) => ({ ...prev, liked: j.liked, likes: j.likes })))
  const remove = () => {
    if (!window.confirm('이 상품을 지울까요? 되돌릴 수 없어요.')) return
    act('delete', '/delete', null, () => navigate('/', { replace: true }))
  }
  const report = () => act('report', '/report', { reason }, (j) => {
    setReporting(false)
    setReason('')
    setNotice(j.hidden ? '신고가 접수돼 상품이 숨겨졌어요.' : '신고가 접수됐어요. 관리자가 확인할게요.')
  })

  // 판매완료: 채팅한 사람이 있으면 누구와 거래했는지 먼저 물어봄
  const openSold = async () => {
    setBuyers(null)
    const j = await getJson(`/api/market/items/${id}/buyers`)
    setBuyers(j.success ? j.buyers : [])
  }
  const sold = (buyerId) => act('sold', '/sold', { buyerId }, () => {
    setBuyers(undefined)
    setNotice(buyerId ? '판매완료! 거래한 분께 후기 요청을 보냈어요.' : '판매완료로 바꿨어요.')
    return load()
  })
  const review = (body) => act('review', '/review', body, () => {
    setNotice('후기를 남겼어요. 고마워요!')
    return load()
  })

  // 💰 가격 제안
  const sendOffer = (price, done) => act('offer', '/offer', { price }, (j) => {
    done()
    setNotice('가격 제안을 보냈어요. 판매자가 답하면 알림으로 알려드려요.')
    setItem((prev) => ({ ...prev, myOffer: j.myOffer, offersLeft: j.offersLeft }))
  })
  const cancelOffer = () => act('offer', '/offer/cancel', null, () => load())
  const answerOffer = async (offerId, accept) => {
    setBusy('offer')
    setNotice('')
    const j = await postJson(`/api/market/offers/${offerId}/answer`, { accept })
    setBusy('')
    if (!j.success) return setNotice(j.message)
    if (accept && j.roomId) return navigate(`/messages?room=${j.roomId}`)
    setNotice(accept ? '제안을 수락했어요.' : '제안을 거절했어요.')
    return load()
  }

  // 🔗 공유: 휴대폰은 공유 시트, 아니면 주소 복사
  const share = async () => {
    const url = window.location.href
    try {
      if (navigator.share) await navigator.share({ title: item.title, text: `${item.title} · ${priceText(item.price)}`, url })
      else {
        await navigator.clipboard.writeText(url)
        setNotice('주소를 복사했어요.')
      }
    } catch { /* 공유를 취소했거나 복사를 못 함 */ }
  }

  if (error) {
    return (
      <main className="mk-page">
        <div className="mk-empty">
          <span aria-hidden="true">🫥</span>
          <strong>{error}</strong>
          <Link to="/" className="mk-sell">장터로 돌아가기</Link>
        </div>
      </main>
    )
  }
  if (!item) return <main className="mk-page"><p className="mk-muted">불러오는 중...</p></main>

  const closed = item.status !== 'SALE'

  return (
    <main className="mk-page mk-detail-page">
      <Link to={`/?c=${item.category}`} className="mk-back">‹ {item.categoryName}</Link>

      <div className="mk-detail">
        <Gallery photos={item.photos} title={item.title} category={item.category} />

        <section className="mk-info">
          {closed && <span className={`mk-badge mk-badge-inline mk-badge-${item.status}`}>{item.statusName}</span>}
          <h1>{item.title}</h1>
          <p className="mk-price">
            {priceText(item.price)}
            {item.prevPrice > item.price && <span className="mk-drop mk-drop-lg"><s>{priceText(item.prevPrice)}</s> ↓ 가격 내림</span>}
            <button type="button" className="mk-share" onClick={share} aria-label="이 상품 공유하기">🔗 공유</button>
          </p>
          {item.priceGuide?.count > 0 && Number(item.price) > 0 && <p className="mk-guide">📊 {guideText(item.priceGuide)}</p>}
          <p className="mk-muted">{item.categoryName} · {timeAgo(item.bumpAt, now)} · 조회 {item.views} · 찜 {item.likes} · 채팅 {item.chats}</p>

          <ul className="mk-tags">
            <li>{item.condName}</li>
            <li>{item.tradeName}</li>
            {item.region && <li>📍 {item.region}</li>}
          </ul>

          <p className="mk-desc">{item.description}</p>

          {item.cautions?.length > 0 && (
            <div className="mk-caution" role="note">
              <strong>⚠️ 거래 전에 확인하세요</strong>
              <ul>{item.cautions.map((c) => <li key={c}>이 글은 {c}</li>)}</ul>
              <p>직접 만나서 물건을 확인하고 거래하는 것이 가장 안전해요.</p>
            </div>
          )}

          <Link to={`/market/user/${item.seller?.id}`} className="mk-seller" aria-label={`${item.seller?.nickname}님 프로필 보기`}>
            <span className="mk-avatar" aria-hidden="true">{String(item.seller?.nickname ?? '?').slice(0, 1)}</span>
            <span className="mk-seller-name">
              <b>{item.seller?.nickname}</b>
              <small>판매중 {item.seller?.itemCount ?? 0} · 거래완료 {item.seller?.soldCount ?? 0} · 후기 {item.seller?.reviewCount ?? 0}</small>
            </span>
            {item.seller?.temp != null && <Temp temp={item.seller.temp} />}
          </Link>

          {notice && <p className="mk-notice" role="status">{notice}</p>}

          {item.mine ? (
            <div className="mk-owner">
              <div className="mk-seg" role="group" aria-label="판매 상태">
                {STATUS_BUTTONS.map(([value, label]) => (
                  <button key={value} type="button" className={item.status === value ? 'mk-seg-on' : ''} disabled={!!busy || item.status === 'HIDDEN' || item.status === value}
                    onClick={() => (value === 'SOLD' ? openSold() : act('status', '/status', { status: value }))}>
                    {label}
                  </button>
                ))}
              </div>
              <OfferList offers={item.offers} price={item.price} now={now} busy={!!busy} onAnswer={answerOffer} />
              {buyers !== undefined && <SoldPicker buyers={buyers} busy={!!busy} onPick={sold} onCancel={() => setBuyers(undefined)} />}
              {item.status === 'SOLD' && buyers === undefined && (
                <p className="mk-muted">
                  {item.buyerName ? `🤝 ${item.buyerName}님과 거래했어요.` : '거래한 사람을 고르면 서로 후기를 남길 수 있어요.'}
                  {!item.reviewed && <button type="button" className="mk-link-btn" onClick={openSold}> {item.buyerName ? '바꾸기' : '고르기'} ›</button>}
                </p>
              )}
              {item.status === 'HIDDEN' && <p className="mk-muted">신고가 여러 번 들어와 숨겨진 상태예요. 관리자가 확인하고 있어요.</p>}
              <div className="mk-owner-row">
                <button type="button" className="mk-btn" disabled={!!busy || !item.canBump} onClick={() => act('bump', '/bump', null, () => { setNotice('목록 맨 위로 끌어올렸어요.'); return load() })}
                  title="24시간에 한 번, 목록 맨 위로">
                  ⬆️ 끌어올리기
                </button>
                <Link to={`/market/${item.id}/edit`} className="mk-btn">✏️ 수정</Link>
                <button type="button" className="mk-btn mk-btn-danger" disabled={!!busy} onClick={remove}>🗑 삭제</button>
              </div>
              <Link to="/messages" className="mk-muted mk-link">받은 문의는 💬 메시지에서 볼 수 있어요 ›</Link>
            </div>
          ) : (
            <div className="mk-actions">
              <button type="button" className={`mk-like ${item.liked ? 'mk-like-on' : ''}`} onClick={like} disabled={busy === 'like'} aria-pressed={item.liked} aria-label="찜">
                {item.liked ? '♥' : '♡'}
              </button>
              <button type="button" className="mk-chat" onClick={chat} disabled={busy === 'chat' || item.status === 'SOLD'}>
                {item.status === 'SOLD' ? '판매가 끝난 상품이에요' : busy === 'chat' ? '채팅방 여는 중...' : '💬 채팅하기'}
              </button>
            </div>
          )}

          {!item.mine && <OfferBox item={item} busy={busy === 'offer'} onSend={sendOffer} onCancel={cancelOffer} />}

          {item.canReview && <ReviewForm who={item.mine ? item.buyerName : item.seller?.nickname} busy={busy === 'review'} onSubmit={review} />}
          {item.reviewed && <p className="mk-muted">⭐ 이 거래에 후기를 남겼어요.</p>}
          {item.iAmBuyer && !item.canReview && !item.reviewed && <p className="mk-muted">🤝 내가 거래한 상품이에요.</p>}

          {!item.mine && (
            <div className="mk-report">
              {reporting ? (
                <div className="mk-report-form">
                  <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="신고 이유 (예: 사기 의심, 금지 품목)" maxLength={200} aria-label="신고 이유" />
                  <button type="button" className="mk-btn" onClick={report} disabled={busy === 'report' || reason.trim().length < 2}>신고</button>
                  <button type="button" className="mk-btn" onClick={() => setReporting(false)}>취소</button>
                </div>
              ) : (
                <button type="button" className="mk-link-btn" onClick={() => setReporting(true)}>🚩 이 상품 신고하기</button>
              )}
              {item.reports != null && item.reports > 0 && <span className="mk-muted"> · 신고 {item.reports}번</span>}
              {item.reports != null && <button type="button" className="mk-link-btn mk-btn-danger" onClick={remove}> · 관리자 삭제</button>}
            </div>
          )}
        </section>
      </div>

      {item.others?.length > 0 && (
        <section className="mk-others">
          <h2>{item.seller?.nickname}님의 다른 상품</h2>
          <div className="mk-grid">
            {item.others.map((it) => <MarketCard key={it.id} item={it} now={now} />)}
          </div>
        </section>
      )}
    </main>
  )
}

export default MarketItemPage
