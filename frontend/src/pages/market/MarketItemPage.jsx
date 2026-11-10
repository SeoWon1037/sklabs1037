// ============================================
// 🛍️ 상품 상세 (주소: /market/:id, 로그인 없이 누구나)
// - 사진(옆으로 넘기기) · 가격 · 상태 · 설명 · 판매자 · 판매자의 다른 상품
// - 사는 사람: [채팅하기] → 판매자와 1:1 메시지 (/messages?room=) · ♥ 찜 · 신고
// - 판매자: 판매중/예약중/판매완료 · 끌어올리기(24시간에 한 번) · 수정 · 삭제
// - 관리자: 어떤 상품이든 삭제
// - 백엔드: GET /api/market/items/{id}, POST …/like | chat | status | bump | delete | report
// ============================================
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import MarketCard from './MarketCard'
import { CATEGORY_ICON, getJson, postJson, priceText, timeAgo } from './marketUtil'
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
          <p className="mk-price">{priceText(item.price)}</p>
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

          <div className="mk-seller">
            <span className="mk-avatar" aria-hidden="true">{String(item.seller?.nickname ?? '?').slice(0, 1)}</span>
            <span>
              <b>{item.seller?.nickname}</b>
              <small>판매중 {item.seller?.itemCount ?? 0} · 거래완료 {item.seller?.soldCount ?? 0}</small>
            </span>
          </div>

          {notice && <p className="mk-notice" role="status">{notice}</p>}

          {item.mine ? (
            <div className="mk-owner">
              <div className="mk-seg" role="group" aria-label="판매 상태">
                {STATUS_BUTTONS.map(([value, label]) => (
                  <button key={value} type="button" className={item.status === value ? 'mk-seg-on' : ''} disabled={!!busy || item.status === 'HIDDEN' || item.status === value}
                    onClick={() => act('status', '/status', { status: value })}>
                    {label}
                  </button>
                ))}
              </div>
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
