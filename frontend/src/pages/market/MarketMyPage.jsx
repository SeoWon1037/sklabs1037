// ============================================
// 🛍️ 내 장터 (주소: /market/my, 로그인 필요) — 내 판매 목록 / 🧾 구매 내역 / 찜한 상품 / 🔔 키워드 알림
// - 키워드 알림: 등록한 낱말이 제목에 들어간 새 상품이 올라오면 알림 (10개까지)
// - 백엔드: GET /api/market/my, POST /api/market/keywords | keywords/delete
// ============================================
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import MarketCard from './MarketCard'
import { postJson } from './marketUtil'
import './Market.css'

const EMPTY = {
  selling: ['🧺', '아직 올린 상품이 없어요', '안 쓰는 물건을 올려보세요. 사진 한 장이면 돼요.'],
  purchases: ['🧾', '구매 내역이 없어요', '판매자가 판매완료 때 나를 거래한 사람으로 고르면 여기에 모여요. 후기도 남길 수 있어요.'],
  likes: ['♡', '찜한 상품이 없어요', '마음에 드는 상품에서 ♡를 눌러보세요.'],
}

function MarketMyPage() {

  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const tab = ['likes', 'keywords', 'purchases'].includes(params.get('tab')) ? params.get('tab') : 'selling'
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [now, setNow] = useState(0)

  useEffect(() => {
    let ignore = false
    fetch('/api/market/my').then((r) => {
      if (r.status === 401) {
        navigate('/login', { replace: true })
        return null
      }
      return r.json()
    }).then((j) => {
      if (ignore || !j) return
      if (!j.success) return setError(j.message)
      setData(j)
      setNow(Date.now())
    }).catch(() => { if (!ignore) setError('서버에 연결하지 못했어요.') })
    return () => { ignore = true }
  }, [navigate])

  const [word, setWord] = useState('')
  const [kwError, setKwError] = useState('')

  const changeKeyword = async (url, keyword) => {
    const j = await postJson(url, { keyword })
    if (!j.success) return setKwError(j.message)
    setKwError('')
    setWord('')
    setData((prev) => ({ ...prev, keywords: j.keywords }))
  }
  const addKeyword = (e) => {
    e.preventDefault()
    if (word.trim().length >= 2) changeKeyword('/api/market/keywords', word.trim())
  }

  const list = tab === 'keywords' ? [] : data?.[tab] ?? []

  return (
    <main className="mk-page">
      <Link to="/market" className="mk-back">‹ 장터로</Link>
      <div className="mk-my-head">
        <h1>내 장터</h1>
        <Link to="/market/new" className="mk-sell">+ 판매하기</Link>
      </div>

      <div className="mk-tabs" role="tablist">
        <button type="button" role="tab" aria-selected={tab === 'selling'} className={tab === 'selling' ? 'mk-tab-on' : ''} onClick={() => setParams({}, { replace: true })}>
          판매 내역 {data && <b>{data.selling.length}</b>}
        </button>
        <button type="button" role="tab" aria-selected={tab === 'purchases'} className={tab === 'purchases' ? 'mk-tab-on' : ''} onClick={() => setParams({ tab: 'purchases' }, { replace: true })}>
          구매 내역 {data && <b>{data.purchases?.length ?? 0}</b>}
        </button>
        <button type="button" role="tab" aria-selected={tab === 'likes'} className={tab === 'likes' ? 'mk-tab-on' : ''} onClick={() => setParams({ tab: 'likes' }, { replace: true })}>
          찜한 상품 {data && <b>{data.likes.length}</b>}
        </button>
        <button type="button" role="tab" aria-selected={tab === 'keywords'} className={tab === 'keywords' ? 'mk-tab-on' : ''} onClick={() => setParams({ tab: 'keywords' }, { replace: true })}>
          🔔 키워드 {data && <b>{data.keywords?.length ?? 0}</b>}
        </button>
      </div>

      {error && <p className="mk-error" role="alert">{error}</p>}
      {!error && !data && <p className="mk-muted">불러오는 중...</p>}

      {data && tab === 'keywords' && (
        <section className="mk-kw">
          <p className="mk-muted">찾는 물건의 낱말을 등록해 두면, 제목에 그 낱말이 들어간 새 상품이 올라올 때 🔔 알림으로 알려드려요. ({data.keywords?.length ?? 0}/{data.maxKeywords ?? 10})</p>
          <form className="mk-report-form" onSubmit={addKeyword}>
            <input value={word} onChange={(e) => setWord(e.target.value)} placeholder="예: 아이폰, 자전거, 에어팟" maxLength={20} aria-label="알림 받을 키워드" enterKeyHint="done" />
            <button type="submit" className="mk-btn" disabled={word.trim().length < 2}>등록</button>
          </form>
          {kwError && <p className="mk-error" role="alert">{kwError}</p>}
          <ul className="mk-kw-list">
            {(data.keywords ?? []).map((k) => (
              <li key={k}>
                <Link to={`/market?q=${encodeURIComponent(k)}`}>{k}</Link>
                <button type="button" onClick={() => changeKeyword('/api/market/keywords/delete', k)} aria-label={`${k} 키워드 지우기`}>✕</button>
              </li>
            ))}
          </ul>
          {(data.keywords ?? []).length === 0 && <p className="mk-muted">아직 등록한 키워드가 없어요.</p>}
        </section>
      )}

      {data && tab !== 'keywords' && list.length === 0 && (
        <div className="mk-empty">
          <span aria-hidden="true">{EMPTY[tab][0]}</span>
          <strong>{EMPTY[tab][1]}</strong>
          <p>{EMPTY[tab][2]}</p>
        </div>
      )}

      {tab === 'purchases' && list.length > 0 && <p className="mk-muted mk-tab-note">상품을 누르면 판매자에게 거래 후기를 남길 수 있어요.</p>}
      {list.length > 0 && <div className="mk-grid">{list.map((it) => <MarketCard key={it.id} item={it} now={now} />)}</div>}
    </main>
  )
}

export default MarketMyPage
