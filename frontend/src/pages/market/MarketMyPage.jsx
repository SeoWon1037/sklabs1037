// ============================================
// 🛍️ 내 장터 (주소: /market/my, 로그인 필요) — 내 판매 목록 / 찜한 상품
// - 백엔드: GET /api/market/my
// ============================================
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import MarketCard from './MarketCard'
import './Market.css'

function MarketMyPage() {

  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'likes' ? 'likes' : 'selling'
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

  const list = data?.[tab] ?? []

  return (
    <main className="mk-page">
      <Link to="/" className="mk-back">‹ 장터로</Link>
      <div className="mk-my-head">
        <h1>내 장터</h1>
        <Link to="/market/new" className="mk-sell">+ 판매하기</Link>
      </div>

      <div className="mk-tabs" role="tablist">
        <button type="button" role="tab" aria-selected={tab === 'selling'} className={tab === 'selling' ? 'mk-tab-on' : ''} onClick={() => setParams({}, { replace: true })}>
          판매 내역 {data && <b>{data.selling.length}</b>}
        </button>
        <button type="button" role="tab" aria-selected={tab === 'likes'} className={tab === 'likes' ? 'mk-tab-on' : ''} onClick={() => setParams({ tab: 'likes' }, { replace: true })}>
          찜한 상품 {data && <b>{data.likes.length}</b>}
        </button>
      </div>

      {error && <p className="mk-error" role="alert">{error}</p>}
      {!error && !data && <p className="mk-muted">불러오는 중...</p>}

      {data && list.length === 0 && (
        <div className="mk-empty">
          <span aria-hidden="true">{tab === 'likes' ? '♡' : '🧺'}</span>
          <strong>{tab === 'likes' ? '찜한 상품이 없어요' : '아직 올린 상품이 없어요'}</strong>
          <p>{tab === 'likes' ? '마음에 드는 상품에서 ♡를 눌러보세요.' : '안 쓰는 물건을 올려보세요. 사진 한 장이면 돼요.'}</p>
        </div>
      )}

      {list.length > 0 && <div className="mk-grid">{list.map((it) => <MarketCard key={it.id} item={it} now={now} />)}</div>}
    </main>
  )
}

export default MarketMyPage
