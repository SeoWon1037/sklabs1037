// ============================================
// 🛍️ 중고거래 장터 (주소: /market, 로그인 없이 누구나 구경)
// - 검색 · 카테고리 · 📍 지역 · 정렬 · 판매중만/나눔만/📉 가격 내림만 → 상품 카드 목록 (24개씩 더 보기)
// - 상품을 누르면 상세 → [채팅하기]로 판매자와 1:1 메시지
// - [판매하기] → /market/new (로그인 필요)
// - 검색 조건은 주소에 남김 (?c=DIGITAL&q=아이폰&sort=LOW) → 뒤로 가기·공유해도 그대로
// - 🕘 최근 본 상품: 이 브라우저에 남긴 12개를 목록 아래에 한 줄로 (조건 없이 볼 때만)
// - 백엔드: GET /api/market/items | regions
// ============================================
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import MarketCard from './MarketCard'
import { CATEGORIES, CATEGORY_ICON, SORTS, clearRecent, getJson, priceText, readRecent } from './marketUtil'
import './Market.css'

// 목록 요청 주소
const listUrl = ({ category, q, sort, sale, free, drop, region }, page) => {
  const sp = new URLSearchParams({ page: String(page), sort })
  if (category) sp.set('category', category)
  if (q) sp.set('q', q)
  if (sale) sp.set('sale', 'true')
  if (free) sp.set('free', 'true')
  if (drop) sp.set('drop', 'true')
  if (region) sp.set('region', region)
  return `/api/market/items?${sp}`
}

function MarketPage() {

  const [params, setParams] = useSearchParams()
  const category = params.get('c') ?? ''
  const q = params.get('q') ?? ''
  const sort = params.get('sort') ?? 'RECENT'
  const sale = params.get('sale') === '1'
  const free = params.get('free') === '1'
  const drop = params.get('drop') === '1'
  const region = params.get('r') ?? ''
  const [regions, setRegions] = useState([])

  const [items, setItems] = useState(null)
  const [hasMore, setHasMore] = useState(false)
  const [page, setPage] = useState(0)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const [now, setNow] = useState(0)
  const [keyword, setKeyword] = useState(q)
  const [recent, setRecent] = useState(readRecent)

  // 조건이 바뀌면 처음부터
  useEffect(() => {
    let ignore = false
    getJson(listUrl({ category, q, sort, sale, free, drop, region }, 0)).then((j) => {
      if (ignore) return
      if (!j.success) return setError(j.message || '상품을 불러오지 못했어요.')
      setError('')
      setItems(j.items)
      setHasMore(j.hasMore)
      setPage(0)
      setNow(Date.now())
    })
    return () => { ignore = true }
  }, [category, q, sort, sale, free, drop, region])

  // 📍 상품이 있는 지역 목록 (한 번만)
  useEffect(() => {
    let ignore = false
    getJson('/api/market/regions').then((j) => { if (!ignore && j.success) setRegions(j.regions) })
    return () => { ignore = true }
  }, [])

  const more = async () => {
    setLoadingMore(true)
    const j = await getJson(listUrl({ category, q, sort, sale, free, drop, region }, page + 1))
    setLoadingMore(false)
    if (!j.success) return
    setItems((prev) => [...(prev ?? []), ...j.items.filter((it) => !(prev ?? []).some((x) => x.id === it.id))])
    setHasMore(j.hasMore)
    setPage(page + 1)
  }

  // 주소의 조건 바꾸기 (빈 값은 지움)
  const change = (patch) => {
    const next = new URLSearchParams(params)
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
    setParams(next, { replace: true })
  }

  const search = (e) => {
    e.preventDefault()
    change({ q: keyword.trim() })
  }

  const filtered = category || q || sale || free || drop || region

  return (
    <main className="mk-page">
      <section className="mk-hero">
        <div>
          <h1>중고거래</h1>
          <p>안 쓰는 물건은 팔고, 필요한 물건은 채팅으로 바로 물어보세요.</p>
        </div>
        <form className="mk-search" onSubmit={search} role="search">
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="찾는 물건 (예: 아이폰, 자전거)"
            aria-label="상품 검색"
            maxLength={30}
            enterKeyHint="search"
          />
          <button type="submit" aria-label="검색">🔍</button>
        </form>
        <Link to="/market/new" className="mk-sell">+ 판매하기</Link>
      </section>

      <nav className="mk-cats" aria-label="카테고리">
        <button type="button" className={!category ? 'mk-cat-on' : ''} onClick={() => change({ c: '' })}>
          <i aria-hidden="true">🛍️</i>전체
        </button>
        {CATEGORIES.map(([code, name, icon]) => (
          <button key={code} type="button" className={category === code ? 'mk-cat-on' : ''} onClick={() => change({ c: category === code ? '' : code })}>
            <i aria-hidden="true">{icon}</i>{name}
          </button>
        ))}
      </nav>

      <div className="mk-filters">
        <label className="mk-check"><input type="checkbox" checked={sale} onChange={(e) => change({ sale: e.target.checked ? '1' : '' })} />판매중만</label>
        <label className="mk-check"><input type="checkbox" checked={free} onChange={(e) => change({ free: e.target.checked ? '1' : '' })} />나눔만</label>
        <label className="mk-check"><input type="checkbox" checked={drop} onChange={(e) => change({ drop: e.target.checked ? '1' : '' })} />📉 가격 내림만</label>
        {q && <button type="button" className="mk-chip-x" onClick={() => { setKeyword(''); change({ q: '' }) }}>“{q}” ✕</button>}
        {(regions.length > 0 || region) && (
          <select className="mk-region" value={region} onChange={(e) => change({ r: e.target.value })} aria-label="지역">
            <option value="">📍 전체 지역</option>
            {region && !regions.some((r) => r.region === region) && <option value={region}>{region}</option>}
            {regions.map((r) => <option key={r.region} value={r.region}>{r.region} ({r.count})</option>)}
          </select>
        )}
        <select value={sort} onChange={(e) => change({ sort: e.target.value === 'RECENT' ? '' : e.target.value })} aria-label="정렬">
          {SORTS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
        </select>
      </div>

      {error && <p className="mk-error" role="alert">{error}</p>}
      {!error && items === null && <div className="mk-grid" aria-hidden="true">{Array.from({ length: 8 }, (_, i) => <div key={i} className="mk-card mk-skeleton"><div className="mk-thumb" /></div>)}</div>}

      {items && items.length === 0 && (
        <div className="mk-empty">
          <span aria-hidden="true">🧺</span>
          <strong>{filtered ? '조건에 맞는 상품이 없어요' : '아직 올라온 상품이 없어요'}</strong>
          <p>{filtered ? '검색어나 카테고리를 바꿔보세요.' : '첫 번째 판매자가 되어보세요!'}</p>
          <Link to="/market/new" className="mk-sell">+ 판매하기</Link>
        </div>
      )}

      {items && items.length > 0 && (
        <>
          <div className="mk-grid">
            {items.map((it) => <MarketCard key={it.id} item={it} now={now} />)}
          </div>
          {hasMore && (
            <button type="button" className="mk-more" onClick={more} disabled={loadingMore}>
              {loadingMore ? '불러오는 중...' : '더 보기'}
            </button>
          )}
        </>
      )}

      {!filtered && recent.length > 0 && (
        <section className="mk-recent" aria-label="최근 본 상품">
          <div className="mk-recent-head">
            <h2>🕘 최근 본 상품</h2>
            <button type="button" className="mk-link-btn" onClick={() => { clearRecent(); setRecent([]) }}>지우기</button>
          </div>
          <div className="mk-recent-row">
            {recent.map((r) => (
              <Link key={r.id} to={`/market/${r.id}`} className="mk-recent-item">
                <span className="mk-thumb">
                  {r.thumb ? <img src={r.thumb} alt="" loading="lazy" /> : <span className="mk-thumb-empty" aria-hidden="true">{CATEGORY_ICON[r.category] ?? '📦'}</span>}
                </span>
                <span className="mk-recent-title">{r.title}</span>
                <b>{priceText(r.price)}</b>
              </Link>
            ))}
          </div>
        </section>
      )}

      <p className="mk-safe">
        🛡️ 안전 거래: 직접 만나 물건을 확인하고 거래하세요. 먼저 입금을 요구하거나 다른 메신저·링크로 유도하면 사기일 수 있어요.
        SKLabs는 결제를 대신 받지 않아요.
      </p>

      {/* 휴대폰: 엄지 닿는 곳에 판매하기 */}
      <Link to="/market/new" className="mk-fab" aria-label="판매하기">+ 판매하기</Link>
    </main>
  )
}

export default MarketPage
