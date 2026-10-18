// ============================================
// 메인 화면 (주소: /)
// - 백엔드 API: GET /api/stock/dashboard → { success, baseDate, stocks }
// - 종목 카드(현재가, 전일 대비, 미니 차트) + 선택한 종목 상세 차트
// - "AI에게 묻기" → AI 비서 화면에 질문을 미리 채워서 이동
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import StockChart from '../components/StockChart'
import './MainPage.css'

// 상세 차트 기간 (거래일 기준)
const PERIODS = [
  { label: '1개월', days: 20 },
  { label: '3개월', days: 60 },
  { label: '전체', days: 100 },
]

// 한국 주식 관례: 상승 빨강 / 하락 파랑
const UP_COLOR = '#e5484d'
const DOWN_COLOR = '#3b82f6'

const formatPrice = (value) => (value == null ? '-' : Number(value).toLocaleString('ko-KR'))

// 등락 표시 (▲ +9,500 +4.58%)
const formatChange = (amount, rate) => {
  if (amount == null || rate == null) return '-'
  const n = Number(amount)
  const arrow = n > 0 ? '▲' : n < 0 ? '▼' : '-'
  const sign = n > 0 ? '+' : ''
  return `${arrow} ${sign}${formatPrice(n)} (${sign}${Number(rate).toFixed(2)}%)`
}

const changeClass = (value) => (Number(value) > 0 ? 'up' : Number(value) < 0 ? 'down' : '')

function MainPage() {

  const [stocks, setStocks] = useState([])
  const [baseDate, setBaseDate] = useState(null)
  const [selectedSymbol, setSelectedSymbol] = useState(null)
  const [period, setPeriod] = useState(60)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // 처음 한 번 시세 불러오기
  useEffect(() => {
    fetch('/api/stock/dashboard')
      .then((res) => res.json())
      .then((data) => {
        if (data.success) {
          setStocks(data.stocks)
          setBaseDate(data.baseDate)
          setSelectedSymbol(data.stocks[0]?.symbol ?? null)
        } else {
          setError(data.message)
        }
      })
      .catch(() => setError('서버에 연결할 수 없습니다.'))
      .finally(() => setLoading(false))
  }, [])

  // 선택한 종목 + 기간에 맞는 차트 데이터
  const selected = stocks.find((s) => s.symbol === selectedSymbol)
  const periodPoints = selected ? selected.prices.slice(-period) : []
  const periodFirst = periodPoints[0]
  const periodLast = periodPoints[periodPoints.length - 1]
  const periodRate = periodFirst && periodLast
    ? ((Number(periodLast.close) - Number(periodFirst.close)) / Number(periodFirst.close)) * 100
    : null
  const periodCloses = periodPoints.map((p) => Number(p.close))

  return (
    <main className="main-page">

      {/* ===== 소개 ===== */}
      <section className="main-hero">
        <div>
          <p className="main-eyebrow">SKLabs 주식 분석</p>
          <h1 className="main-title">데이터로 답하는 AI 주식 비서</h1>
          <p className="main-desc">
            매일 장 마감 후 자동 수집한 시세와 종목 문서를 AI가 직접 조회해서 답합니다.
          </p>
        </div>
        <Link to="/ai" className="btn btn-primary main-cta">🤖 AI 비서에게 물어보기</Link>
      </section>

      {loading && <p className="main-status">시세를 불러오는 중...</p>}
      {error && <p className="main-status main-error">{error}</p>}

      {!loading && !error && (
        <>
          {/* ===== 종목 카드 ===== */}
          <div className="main-section-head">
            <h2 className="main-section-title">종목 시세</h2>
            {baseDate && <span className="main-base-date">{baseDate} 종가 기준 · 평일 20:30 자동 수집</span>}
          </div>

          <section className="stock-grid">
            {stocks.map((s) => (
              <button
                key={s.symbol}
                type="button"
                className={`stock-card ${s.symbol === selectedSymbol ? 'stock-card-active' : ''}`}
                onClick={() => setSelectedSymbol(s.symbol)}
              >
                <div className="stock-card-head">
                  <span className="stock-name">{s.name}</span>
                  <span className="stock-code">{s.symbol}</span>
                </div>
                <div className="stock-price">{formatPrice(s.closePrice)}</div>
                <div className={`stock-change ${changeClass(s.changeAmount)}`}>
                  {formatChange(s.changeAmount, s.changeRate)}
                </div>
                <StockChart
                  points={s.prices.slice(-20)}
                  height={44}
                  color={Number(s.changeAmount) >= 0 ? UP_COLOR : DOWN_COLOR}
                />
              </button>
            ))}
          </section>

          {/* ===== 선택한 종목 상세 ===== */}
          {selected && (
            <section className="stock-detail">
              <div className="stock-detail-head">
                <div>
                  <div className="stock-detail-name">
                    {selected.name} <span className="stock-code">{selected.symbol} · {selected.market}</span>
                  </div>
                  <div className="stock-detail-price">
                    {formatPrice(selected.closePrice)}원
                    <span className={`stock-change ${changeClass(selected.changeAmount)}`}>
                      {formatChange(selected.changeAmount, selected.changeRate)}
                    </span>
                  </div>
                </div>

                <div className="period-tabs">
                  {PERIODS.map((p) => (
                    <button
                      key={p.days}
                      type="button"
                      className={`period-tab ${period === p.days ? 'period-tab-active' : ''}`}
                      onClick={() => setPeriod(p.days)}
                    >
                      {p.label}
                    </button>
                  ))}
                </div>
              </div>

              <StockChart
                points={periodPoints}
                height={240}
                detailed
                color={periodRate !== null && periodRate >= 0 ? UP_COLOR : DOWN_COLOR}
              />

              <div className="stock-stats">
                <div className="stock-stat">
                  <span>기간 수익률</span>
                  <strong className={changeClass(periodRate)}>
                    {periodRate === null ? '-' : `${periodRate > 0 ? '+' : ''}${periodRate.toFixed(2)}%`}
                  </strong>
                </div>
                <div className="stock-stat">
                  <span>기간 최고 종가</span>
                  <strong>{periodCloses.length ? formatPrice(Math.max(...periodCloses)) : '-'}</strong>
                </div>
                <div className="stock-stat">
                  <span>기간 최저 종가</span>
                  <strong>{periodCloses.length ? formatPrice(Math.min(...periodCloses)) : '-'}</strong>
                </div>
                <div className="stock-stat">
                  <span>최근 거래량</span>
                  <strong>{formatPrice(selected.volume)}</strong>
                </div>
              </div>

              <Link
                to={`/ai?q=${encodeURIComponent(`${selected.name} 최근 한 달 흐름이랑 무슨 사업을 하는 회사인지 알려줘`)}`}
                className="btn btn-ghost stock-ask"
              >
                🤖 {selected.name}에 대해 AI에게 묻기
              </Link>
            </section>
          )}
        </>
      )}
    </main>
  )
}

export default MainPage