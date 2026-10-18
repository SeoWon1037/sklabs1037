// ============================================
// 주식 종목 화면 (주소: /stock) — 토스증권 차트 화면 스타일, 넓은 화면
// - 왼쪽: 종목 목록 (현재가, 등락률, 미니 차트)
// - 오른쪽: 종목 헤더 + 탭 (차트·시세 / 종목정보 / 뉴스)
// - 백엔드 API
//   GET /api/stock/dashboard?days=20        종목 목록 + 미니 차트
//   GET /api/stock/detail?symbol=005930     종목 정보 + 일봉
//   GET /api/news?stockId=1&size=15         종목 뉴스
//   GET /api/news/summary?stockId=1         AI 뉴스 요약 (NewsSummaryBox)
//   GET/POST/DELETE /api/watchlist          관심 종목 (☆ / ★)
// ============================================
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import StockChart from '../components/StockChart'
import CandleChart from '../components/CandleChart'
import NewsSummaryBox from '../components/NewsSummaryBox'
import WatchStar from '../components/WatchStar'
import './StockPage.css'

const TABS = [
  { value: 'chart', label: '차트 · 시세' },
  { value: 'info', label: '종목정보' },
  { value: 'news', label: '뉴스' },
]

const PERIODS = [
  { value: 'D', label: '일' },
  { value: 'W', label: '주' },
  { value: 'M', label: '월' },
]

// 한국 주식 관례: 상승 빨강 / 하락 파랑
const UP_COLOR = '#e5484d'
const DOWN_COLOR = '#3b82f6'

const formatPrice = (value) => (value == null ? '-' : Math.round(Number(value)).toLocaleString('ko-KR'))
const changeClass = (value) => (Number(value) > 0 ? 'up' : Number(value) < 0 ? 'down' : '')
const signed = (value, digits = 0) => {
  const n = Number(value)
  const text = digits ? n.toFixed(digits) : Math.round(n).toLocaleString('ko-KR')
  return n > 0 ? `+${text}` : text
}
const arrow = (value) => (Number(value) > 0 ? '▲' : Number(value) < 0 ? '▼' : '')

// 날짜 → "2026-10-16" (한국 시간 기준)
const toYmd = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

// 그 주의 월요일 (주봉 묶는 기준)
const weekKey = (date) => {
  const d = new Date(`${date}T00:00:00`)
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  return toYmd(d)
}

// 일봉 → 주봉 / 월봉 (시가=첫날 시가, 종가=마지막 날 종가, 고가·저가=기간 최고·최저, 거래량=합계)
const groupCandles = (candles, period) => {
  if (period === 'D') return candles

  const groups = []
  let current = null

  for (const c of candles) {
    const key = period === 'M' ? c.date.slice(0, 7) : weekKey(c.date)
    if (!current || current.key !== key) {
      current = { key, date: c.date, open: c.open, high: c.high, low: c.low, close: c.close, volume: c.volume }
      groups.push(current)
    } else {
      current.high = Math.max(current.high, c.high)
      current.low = Math.min(current.low, c.low)
      current.close = c.close
      current.volume += c.volume
    }
  }

  return groups
}

const timeAgo = (dateText) => {
  const minutes = Math.floor((Date.now() - new Date(dateText).getTime()) / 60000)
  if (minutes < 60) return `${Math.max(minutes, 1)}분 전`
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}시간 전`
  return `${Math.floor(minutes / (60 * 24))}일 전`
}

function StockPage() {

  // ===== 종목 목록 =====
  const [stocks, setStocks] = useState([])
  const [listError, setListError] = useState('')
  const [selectedSymbol, setSelectedSymbol] = useState(null)

  // ===== 선택한 종목 상세 =====
  const [detail, setDetail] = useState({ key: null, stock: null, candles: [], error: '' })
  const [tab, setTab] = useState('chart')
  const [period, setPeriod] = useState('D')
  const [showMA, setShowMA] = useState(true)
  const [news, setNews] = useState({ key: null, items: [] })

  // ===== 관심 종목 =====
  const [watch, setWatch] = useState({ loggedIn: false, ids: [] })
  const [watchMsg, setWatchMsg] = useState('')

  // 처음 한 번: 종목 목록
  useEffect(() => {
    fetch('/api/stock/dashboard?days=20')
      .then((res) => res.json())
      .then((data) => {
        if (data.success) {
          setStocks(data.stocks)
          setSelectedSymbol(data.stocks[0]?.symbol ?? null)
        } else {
          setListError(data.message)
        }
      })
      .catch(() => setListError('서버에 연결할 수 없습니다.'))
  }, [])

  // 처음 한 번: 내 관심 종목 (로그아웃 상태면 빈 목록)
  useEffect(() => {
    fetch('/api/watchlist')
      .then((res) => res.json())
      .then((data) => {
        if (data.success) setWatch({ loggedIn: data.loggedIn, ids: data.stocks.map((s) => s.stockId) })
      })
      .catch(() => {})
  }, [])

  // ☆ / ★ 누르기: 화면에 먼저 반영하고, 실패하면 되돌림
  const toggleWatch = async (id) => {

    if (!watch.loggedIn) {
      setWatchMsg('로그인하면 관심 종목을 저장할 수 있어요.')
      return
    }

    const on = watch.ids.includes(id)
    const before = watch.ids
    setWatch((prev) => ({ ...prev, ids: on ? prev.ids.filter((x) => x !== id) : [...prev.ids, id] }))
    setWatchMsg('')

    try {
      const res = on
        ? await fetch(`/api/watchlist?stockId=${id}`, { method: 'DELETE' })
        : await fetch('/api/watchlist', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ stockId: id }),
        })
      const data = await res.json()
      if (!data.success) {
        setWatch((prev) => ({ ...prev, ids: before }))
        setWatchMsg(data.message)
      }
    } catch {
      setWatch((prev) => ({ ...prev, ids: before }))
      setWatchMsg('서버에 연결할 수 없습니다.')
    }
  }

  // 종목이 바뀔 때마다: 상세 + 일봉
  useEffect(() => {

    if (!selectedSymbol) return
    let ignore = false

    fetch(`/api/stock/detail?symbol=${selectedSymbol}`)
      .then((res) => res.json())
      .then((data) => {
        if (ignore) return
        if (!data.success) {
          setDetail({ key: selectedSymbol, stock: null, candles: [], error: data.message })
          return
        }
        // 가격은 숫자로 바꿔두기 (계산·차트용)
        const candles = data.candles.map((c) => ({
          date: c.date,
          open: Number(c.open),
          high: Number(c.high),
          low: Number(c.low),
          close: Number(c.close),
          volume: Number(c.volume),
        }))
        setDetail({ key: selectedSymbol, stock: data.stock, candles, error: '' })
      })
      .catch(() => {
        if (!ignore) setDetail({ key: selectedSymbol, stock: null, candles: [], error: '서버에 연결할 수 없습니다.' })
      })

    return () => { ignore = true }

  }, [selectedSymbol])

  const selected = stocks.find((s) => s.symbol === selectedSymbol)
  const stockId = selected?.stockId

  // 뉴스 탭을 열었을 때만: 종목 뉴스
  useEffect(() => {

    if (tab !== 'news' || !stockId) return
    let ignore = false

    fetch(`/api/news?stockId=${stockId}&size=15`)
      .then((res) => res.json())
      .then((data) => {
        if (!ignore) setNews({ key: stockId, items: data.success ? data.news : [] })
      })
      .catch(() => {
        if (!ignore) setNews({ key: stockId, items: [] })
      })

    return () => { ignore = true }

  }, [tab, stockId])

  // ===== 화면에서 쓸 계산 =====
  const ready = detail.key === selectedSymbol && detail.stock
  const daily = useMemo(() => (ready ? detail.candles : []), [ready, detail.candles])
  const chartCandles = useMemo(() => groupCandles(daily, period), [daily, period])

  const latest = daily[daily.length - 1]
  const prev = daily[daily.length - 2]
  const change = latest && prev ? latest.close - prev.close : null
  const changeRate = latest && prev ? (change / prev.close) * 100 : null
  const rangeHigh = daily.length ? Math.max(...daily.map((c) => c.high)) : null
  const rangeLow = daily.length ? Math.min(...daily.map((c) => c.low)) : null
  const avgVolume = daily.length ? daily.reduce((sum, c) => sum + c.volume, 0) / daily.length : null
  const periodReturn = daily.length > 1 ? ((latest.close - daily[0].close) / daily[0].close) * 100 : null

  // 일별 시세 (최근 20일, 최신부터)
  const dailyRows = daily.slice(-21).map((c, i, arr) => {
    const before = arr[i - 1]
    return { ...c, change: before ? c.close - before.close : null, rate: before ? ((c.close - before.close) / before.close) * 100 : null }
  }).slice(1).reverse()

  if (listError) {
    return <main className="stock-page"><p className="stock-status stock-error">{listError}</p></main>
  }

  return (
    <main className="stock-page">
      <div className="stock-layout">

        {/* ===== 왼쪽: 종목 목록 ===== */}
        <aside className="stock-list">
          <div className="stock-list-head">
            <span>종목</span>
            <span className="stock-list-count">{stocks.length}</span>
          </div>
          <ul className="stock-list-items">
            {stocks.map((s) => (
              <li key={s.symbol}>
                <button
                  type="button"
                  className={`stock-item ${s.symbol === selectedSymbol ? 'stock-item-active' : ''}`}
                  onClick={() => setSelectedSymbol(s.symbol)}
                >
                  <span className="stock-item-name">
                    <span>{watch.ids.includes(s.stockId) && <span className="watch-mark">★</span>}{s.name}</span>
                    <small>{s.symbol}</small>
                  </span>
                  <span className="stock-item-chart">
                    <StockChart points={s.prices} height={28} color={Number(s.changeAmount) >= 0 ? UP_COLOR : DOWN_COLOR} />
                  </span>
                  <span className="stock-item-price">
                    {formatPrice(s.closePrice)}
                    <small className={changeClass(s.changeAmount)}>
                      {s.changeRate == null ? '-' : `${signed(s.changeRate, 2)}%`}
                    </small>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </aside>

        {/* ===== 오른쪽: 종목 상세 ===== */}
        <section className="stock-detail">

          {!ready && !detail.error && <p className="stock-status">시세를 불러오는 중...</p>}
          {detail.key === selectedSymbol && detail.error && <p className="stock-status stock-error">{detail.error}</p>}

          {ready && (
            <>
              {/* 종목 헤더 */}
              <div className="detail-head">
                <div>
                  <div className="detail-name">
                    {detail.stock.name}
                    <WatchStar active={watch.ids.includes(stockId)} onClick={() => toggleWatch(stockId)} />
                    <span className="detail-code">{detail.stock.symbol} · {detail.stock.market}</span>
                  </div>
                  <div className="detail-price-row">
                    <span className="detail-price">{formatPrice(latest?.close)}원</span>
                    {change !== null && (
                      <span className={`detail-change ${changeClass(change)}`}>
                        {arrow(change)} {signed(change)} ({signed(changeRate, 2)}%)
                      </span>
                    )}
                    <span className="detail-date">{latest?.date} 종가 기준</span>
                  </div>
                  {watchMsg && (
                    <p className="watch-msg">
                      {watchMsg}
                      {!watch.loggedIn && <Link to="/login">로그인</Link>}
                    </p>
                  )}
                </div>

                <dl className="detail-range">
                  <div><dt>{daily.length}일 최고</dt><dd className="up">{formatPrice(rangeHigh)}</dd></div>
                  <div><dt>{daily.length}일 최저</dt><dd className="down">{formatPrice(rangeLow)}</dd></div>
                </dl>
              </div>

              {/* 탭 */}
              <div className="detail-tabs">
                {TABS.map((t) => (
                  <button
                    key={t.value}
                    type="button"
                    className={`detail-tab ${tab === t.value ? 'detail-tab-active' : ''}`}
                    onClick={() => setTab(t.value)}
                  >
                    {t.label}
                  </button>
                ))}
              </div>

              {/* 탭: 차트 · 시세 */}
              {tab === 'chart' && (
                <>
                  <div className="chart-box">
                    <div className="chart-toolbar">
                      <div className="chart-periods">
                        {PERIODS.map((p) => (
                          <button
                            key={p.value}
                            type="button"
                            className={`chart-period ${period === p.value ? 'chart-period-active' : ''}`}
                            onClick={() => setPeriod(p.value)}
                          >
                            {p.label}
                          </button>
                        ))}
                      </div>
                      <button
                        type="button"
                        className={`chart-toggle ${showMA ? 'chart-toggle-on' : ''}`}
                        onClick={() => setShowMA(!showMA)}
                      >
                        이동평균선 5 · 20
                      </button>
                    </div>

                    <CandleChart candles={chartCandles} showMA={showMA} height={520} />
                  </div>

                  <div className="daily-box">
                    <h2 className="daily-title">일별 시세</h2>
                    <table className="daily-table">
                      <thead>
                        <tr>
                          <th>날짜</th>
                          <th>종가</th>
                          <th>전일비</th>
                          <th>등락률</th>
                          <th>시가</th>
                          <th>고가</th>
                          <th>저가</th>
                          <th>거래량</th>
                        </tr>
                      </thead>
                      <tbody>
                        {dailyRows.map((r) => (
                          <tr key={r.date}>
                            <td>{r.date.slice(5).replace('-', '.')}</td>
                            <td className={changeClass(r.change)}>{formatPrice(r.close)}</td>
                            <td className={changeClass(r.change)}>{arrow(r.change)} {formatPrice(Math.abs(r.change))}</td>
                            <td className={changeClass(r.change)}>{signed(r.rate, 2)}%</td>
                            <td>{formatPrice(r.open)}</td>
                            <td>{formatPrice(r.high)}</td>
                            <td>{formatPrice(r.low)}</td>
                            <td>{formatPrice(r.volume)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}

              {/* 탭: 종목정보 */}
              {tab === 'info' && (
                <div className="info-box">
                  <div className="info-stats">
                    <div className="info-stat"><span>시장</span><strong>{detail.stock.market}</strong></div>
                    <div className="info-stat">
                      <span>{daily.length}거래일 수익률</span>
                      <strong className={changeClass(periodReturn)}>{periodReturn === null ? '-' : `${signed(periodReturn, 2)}%`}</strong>
                    </div>
                    <div className="info-stat"><span>평균 거래량</span><strong>{formatPrice(avgVolume)}</strong></div>
                    <div className="info-stat"><span>데이터 기간</span><strong>{daily[0]?.date} ~ {latest?.date}</strong></div>
                  </div>

                  <h2 className="info-title">{detail.stock.docTitle ?? '사업 개요'}</h2>
                  <p className="info-content">{detail.stock.docContent ?? '등록된 종목 설명이 없습니다.'}</p>

                  <Link
                    to={`/ai?q=${encodeURIComponent(`${detail.stock.name} 최근 흐름이랑 무슨 사업을 하는 회사인지 알려줘`)}`}
                    className="btn btn-primary info-ask"
                  >
                    ✨ {detail.stock.name}에 대해 AI에게 묻기
                  </Link>
                </div>
              )}

              {/* 탭: 뉴스 */}
              {tab === 'news' && (
                <div className="news-box">
                  <NewsSummaryBox key={stockId} stockId={stockId} />
                  {news.key !== stockId && <p className="stock-status">뉴스를 불러오는 중...</p>}
                  {news.key === stockId && news.items.length === 0 && <p className="stock-status">수집된 뉴스가 없습니다.</p>}
                  <ul className="news-list">
                    {news.key === stockId && news.items.map((n) => (
                      <li key={n.newsId} className="news-item">
                        <a href={n.link} target="_blank" rel="noopener noreferrer" className="news-title">{n.title}</a>
                        <span className="news-meta">{n.press} · {timeAgo(n.pubDate)}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </main>
  )
}

export default StockPage