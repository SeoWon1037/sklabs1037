// ============================================
// 내 관심 종목 박스 (메인 화면 오른쪽, 로그인했을 때만)
// - 백엔드 API: GET /api/watchlist
// - 비어 있으면 주식 종목 화면에서 ☆를 눌러 추가하라고 안내
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './Watchlist.css'

const formatPrice = (value) => (value == null ? '-' : Number(value).toLocaleString('ko-KR'))

function WatchlistBox() {

  const [stocks, setStocks] = useState(null)   // null = 불러오는 중

  useEffect(() => {
    fetch('/api/watchlist')
      .then((res) => res.json())
      .then((data) => setStocks(data.success ? data.stocks : []))
      .catch(() => setStocks([]))
  }, [])

  if (stocks === null) return null

  return (
    <section className="home-box">
      <div className="home-box-head">
        <h2 className="home-box-title">⭐ 내 관심 종목</h2>
      </div>

      {stocks.length === 0 ? (
        <p className="watch-empty">
          주식 종목 화면에서 ☆를 눌러<br />관심 종목을 추가해 보세요.
        </p>
      ) : (
        <ul className="home-quotes">
          {stocks.map((s) => {
            const change = Number(s.changeAmount)
            const cls = change > 0 ? 'up' : change < 0 ? 'down' : ''
            return (
              <li key={s.stockId} className="home-quote">
                <span className="home-quote-name">{s.name}</span>
                <span className="home-quote-price">{formatPrice(s.closePrice)}</span>
                <span className={`home-quote-rate ${cls}`}>
                  {change > 0 ? '▲' : change < 0 ? '▼' : ''} {s.changeRate == null ? '-' : `${Number(s.changeRate).toFixed(2)}%`}
                </span>
              </li>
            )
          })}
        </ul>
      )}

      <div className="watch-links">
        <Link to="/stock" className="home-box-link">관심 종목 관리 →</Link>
        {stocks.length > 0 && <Link to="/ai?briefing=1" className="home-box-link">AI 브리핑 받기 →</Link>}
      </div>
    </section>
  )
}

export default WatchlistBox