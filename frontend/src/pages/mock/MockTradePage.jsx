// ============================================
// 모의투자 화면 (주소: /mock)
// - 가상 자금 1,000만 원으로 매수/매도 → 보유 종목, 수익률, 랭킹
//   · 시장가: 지금 가격으로 바로 체결
//   · 지정가: 정해둔 가격이 되면 체결 (정규장 1분마다 확인, 그 거래일 장 마감까지 안 되면 자동 취소)
// - 주소로 종목 골라서 열기: /mock?symbol=005930 (종목 화면 "모의 매수" 버튼)
// - PC: 요약 카드 / [수익률 추이 + 보유 종목 + 거래 내역 | 주문 + 미체결 주문 + 수익률 랭킹]
// - 휴대폰: 요약 → 주문 → 미체결 주문 → 수익률 추이 → 보유 종목 → 거래 내역 → 랭킹 (한 줄씩)
// - 백엔드 API
//   GET  /api/mock/account                          내 계좌 + 보유 종목 + 주문 가능 종목
//   GET  /api/mock/trades?page=1                    내 거래 내역 (20개씩)
//   POST /api/mock/order { stockId, side, quantity, orderType, limitPrice }   주문
//   POST /api/mock/orders/{orderId}/cancel          지정가 주문 취소
//   POST /api/mock/reset                            계좌 초기화
//   GET  /api/mock/ranking?size=10&period=ALL|WEEK  수익률 랭킹 (누구나, 전체 / 이번 주)
//   GET  /api/mock/history                          내 수익률 추이 (평일 15:40 기록 + 지금)
// ============================================
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import ReturnChart from '../../components/mock/ReturnChart'
import './MockTradePage.css'

// 현재가 자동 새로고침 간격 (30초)
const REFRESH_MS = 30_000

// 수익률 그래프 기간
const HISTORY_PERIODS = [
  { value: 7, label: '1주' },
  { value: 31, label: '1개월' },
  { value: 0, label: '전체' },
]

// 수량 빠른 선택
const QUICK_RATES = [
  { label: '10%', value: 0.1 },
  { label: '25%', value: 0.25 },
  { label: '50%', value: 0.5 },
  { label: '최대', value: 1 },
]

const won = (value) => (value == null ? '-' : Math.round(Number(value)).toLocaleString('ko-KR'))
const signedWon = (value) => {
  const n = Math.round(Number(value ?? 0))
  return `${n > 0 ? '+' : ''}${n.toLocaleString('ko-KR')}`
}
const signedRate = (value) => {
  const n = Number(value ?? 0)
  return `${n > 0 ? '+' : ''}${n.toFixed(2)}%`
}
// 상승 빨강 / 하락 파랑 (한국 주식 관례)
const tone = (value) => (Number(value) > 0 ? 'mock-up' : Number(value) < 0 ? 'mock-down' : '')

// 거래 시각: 10.19 21:30
const formatTime = (ms) => {
  const d = new Date(ms)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// 가격 기준 표시: 토스 현재가면 "21:30 기준", 종가면 "10-16 종가"
const priceLabel = (stock) => {
  if (!stock) return ''
  if (stock.priceSource === 'CLOSE') return `${stock.priceTime?.slice(5) ?? ''} 종가 기준`
  const d = stock.priceTime ? new Date(stock.priceTime) : null
  if (!d || Number.isNaN(d.getTime())) return '현재가'
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')} 기준 현재가`
}

// 한국 주식 호가 단위 (백엔드 MockMarketHours.tickSize와 같은 규칙)
const tickSize = (p) => (p < 2000 ? 1 : p < 5000 ? 5 : p < 20000 ? 10 : p < 50000 ? 50 : p < 200000 ? 100 : p < 500000 ? 500 : 1000)

// 2026-10-20 → 10월 20일
const formatDay = (ymd) => (ymd ? `${Number(ymd.slice(5, 7))}월 ${Number(ymd.slice(8, 10))}일` : '')

// 순위 표시
const rankMark = (rank) => ({ 1: '🥇', 2: '🥈', 3: '🥉' })[rank] ?? rank

function MockTradePage() {

  const [searchParams] = useSearchParams()
  const paramSymbol = searchParams.get('symbol')

  // 계좌 (null: 불러오는 중)
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  // 거래 내역
  const [trades, setTrades] = useState({ items: [], total: 0, page: 1 })

  // 랭킹 (ALL: 시작 자금 대비 / WEEK: 이번 주)
  const [ranking, setRanking] = useState(null)
  const [rankPeriod, setRankPeriod] = useState('ALL')

  // 수익률 추이
  const [history, setHistory] = useState(null)
  const [historyDays, setHistoryDays] = useState(31)

  // 주문 입력
  const [side, setSide] = useState('BUY')
  const [orderType, setOrderType] = useState('MARKET')   // MARKET | LIMIT
  const [stockId, setStockId] = useState(null)
  const [quantity, setQuantity] = useState('')
  const [limitPrice, setLimitPrice] = useState('')
  const [ordering, setOrdering] = useState(false)
  const [orderMsg, setOrderMsg] = useState(null)   // { type: 'ok' | 'error', text }

  // 초기화 확인 단계
  const [confirmReset, setConfirmReset] = useState(false)

  // ===== 불러오기 =====
  const loadAccount = useCallback(() => {
    return fetch('/api/mock/account')
      .then((res) => res.json())
      .then((json) => {
        if (!json.success) throw new Error(json.message)
        setData(json)
        setError('')
      })
      .catch((e) => setError(e.message || '계좌 정보를 불러오지 못했습니다.'))
  }, [])

  const loadTrades = useCallback((page = 1) => {
    return fetch(`/api/mock/trades?page=${page}`)
      .then((res) => res.json())
      .then((json) => {
        if (!json.success) return
        setTrades((prev) => ({
          items: page === 1 ? json.trades : [...prev.items, ...json.trades],
          total: json.total,
          page,
        }))
      })
      .catch(() => {})
  }, [])

  const loadRanking = useCallback(() => {
    return fetch(`/api/mock/ranking?size=10&period=${rankPeriod}`)
      .then((res) => res.json())
      .then((json) => json.success && setRanking(json))
      .catch(() => {})
  }, [rankPeriod])

  const loadHistory = useCallback(() => {
    return fetch('/api/mock/history')
      .then((res) => res.json())
      .then((json) => json.success && setHistory(json))
      .catch(() => {})
  }, [])

  // 처음 + 30초마다 (현재가 반영)
  useEffect(() => {
    loadAccount()
    loadRanking()
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') {
        loadAccount()
        loadRanking()
      }
    }, REFRESH_MS)
    return () => clearInterval(timer)
  }, [loadAccount, loadRanking])

  // 로그인 상태면 거래 내역 + 수익률 추이
  useEffect(() => {
    if (!data?.loggedIn) return
    loadTrades(1)
    loadHistory()
  }, [data?.loggedIn, loadTrades, loadHistory])

  // ===== 주문 종목 =====
  const stocks = useMemo(() => data?.stocks ?? [], [data])
  const holdings = useMemo(() => data?.holdings ?? [], [data])
  const pendingOrders = useMemo(() => data?.pendingOrders ?? [], [data])
  const market = data?.market

  // 선택 종목: 직접 고른 것 → 주소의 종목 → 첫 번째 종목
  const selectedId = stockId
    ?? stocks.find((s) => s.symbol === paramSymbol)?.stockId
    ?? stocks[0]?.stockId
  const selected = stocks.find((s) => s.stockId === selectedId)
  const holding = holdings.find((h) => h.stockId === selectedId)

  const price = Number(selected?.price ?? 0)
  const isLimit = orderType === 'LIMIT'
  const orderPrice = isLimit ? (Number(limitPrice) || 0) : price   // 수량·금액 계산에 쓰는 가격
  const qty = Number(quantity) || 0
  const cash = Number(data?.account?.cash ?? 0)
  const sellable = holding?.sellable ?? 0
  const maxQty = side === 'BUY'
    ? (orderPrice > 0 ? Math.floor(cash / orderPrice) : 0)
    : sellable

  // 종목 고르기 (지정가는 그 종목 현재가로 다시 맞춤)
  const selectStock = (id) => {
    const s = stocks.find((x) => x.stockId === id)
    setStockId(id)
    setQuantity('')
    setLimitPrice(s ? String(Math.round(Number(s.price))) : '')
    setOrderMsg(null)
  }

  // 시장가 / 지정가 바꾸기
  const changeOrderType = (type) => {
    setOrderType(type)
    setLimitPrice(price > 0 ? String(Math.round(price)) : '')
    setOrderMsg(null)
  }

  // 지정가 한 호가씩 올리고 내리기
  const stepPrice = (dir) => {
    const now = Number(limitPrice) || Math.round(price)
    const tick = tickSize(dir > 0 ? now : now - 1)
    const next = dir > 0 ? Math.floor(now / tick) * tick + tick : Math.ceil(now / tick) * tick - tick
    setLimitPrice(String(Math.max(tick, next)))
    setOrderMsg(null)
  }

  const pickQuick = (rate) => {
    const n = Math.floor(maxQty * rate)
    setQuantity(n > 0 ? String(n) : '')
    setOrderMsg(null)
  }

  const stepQuantity = (delta) => {
    setQuantity(String(Math.max(0, qty + delta) || ''))
    setOrderMsg(null)
  }

  // ===== 주문 =====
  const submitOrder = async () => {
    if (!selected || qty < 1 || ordering) return
    setOrdering(true)
    setOrderMsg(null)
    try {
      const res = await fetch('/api/mock/order', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          stockId: selected.stockId,
          side,
          quantity: qty,
          orderType,
          limitPrice: isLimit ? orderPrice : null,
        }),
      })
      const json = await res.json()
      if (!json.success) {
        setOrderMsg({ type: 'error', text: json.message })
        return
      }
      const t = json.trade
      const sideText = t.side === 'BUY' ? '매수' : '매도'
      if (t.status === 'PENDING') {
        setOrderMsg({
          type: 'ok',
          text: `지정가 주문 접수: ${t.name} ${won(t.quantity)}주 ${won(t.limitPrice)}원 ${sideText} · ${formatDay(t.orderDate)} 장 마감까지 기다려요`,
        })
      } else {
        const profitText = t.side === 'SELL' ? ` · 실현손익 ${signedWon(t.profit)}원` : ''
        setOrderMsg({
          type: 'ok',
          text: `${t.name} ${won(t.quantity)}주 ${sideText} 체결 (${won(t.price)}원)${profitText}`,
        })
      }
      setQuantity('')
      await Promise.all([loadAccount(), loadTrades(1), loadRanking(), loadHistory()])
    } catch {
      setOrderMsg({ type: 'error', text: '서버에 연결할 수 없습니다.' })
    } finally {
      setOrdering(false)
    }
  }

  // ===== 지정가 주문 취소 =====
  const cancelOrder = async (orderId) => {
    try {
      const res = await fetch(`/api/mock/orders/${orderId}/cancel`, { method: 'POST' })
      const json = await res.json()
      setOrderMsg(json.success ? { type: 'ok', text: '주문을 취소했어요.' } : { type: 'error', text: json.message })
      await loadAccount()
    } catch {
      setOrderMsg({ type: 'error', text: '서버에 연결할 수 없습니다.' })
    }
  }

  // ===== 초기화 =====
  const doReset = async () => {
    try {
      const res = await fetch('/api/mock/reset', { method: 'POST' })
      const json = await res.json()
      if (!json.success) {
        setOrderMsg({ type: 'error', text: json.message })
        return
      }
      setOrderMsg({ type: 'ok', text: '계좌를 1,000만 원으로 초기화했어요. (미체결 주문도 모두 취소)' })
      await Promise.all([loadAccount(), loadTrades(1), loadRanking(), loadHistory()])
    } finally {
      setConfirmReset(false)
    }
  }

  // ===== 화면 조각 =====
  const rankingBox = (
    <section className="mock-card mock-ranking">
      <div className="mock-card-head">
        <h2>수익률 랭킹</h2>
        {ranking && <span className="mock-muted">{ranking.participants}명 참여</span>}
        <div className="mock-seg mock-seg-right">
          {[['ALL', '전체'], ['WEEK', '이번 주']].map(([value, label]) => (
            <button
              key={value}
              type="button"
              className={rankPeriod === value ? 'mock-seg-active' : ''}
              onClick={() => setRankPeriod(value)}
            >
              {label}
            </button>
          ))}
        </div>
      </div>
      {!ranking && <p className="mock-empty">불러오는 중...</p>}
      {ranking && ranking.ranking.length === 0 && <p className="mock-empty">아직 참여한 사람이 없어요. 첫 주문의 주인공이 되어보세요!</p>}
      {ranking && ranking.ranking.length > 0 && (
        <ol className="mock-rank-list">
          {ranking.ranking.map((r, i) => (
            <li key={`${r.rank}-${r.nickname}-${i}`} className={`mock-rank-item ${r.me ? 'mock-rank-me' : ''}`}>
              <span className="mock-rank-no">{rankMark(r.rank)}</span>
              <span className="mock-rank-name">{r.nickname}{r.me && <em>나</em>}</span>
              <span className="mock-rank-value">
                <strong className={tone(r.returnRate)}>{signedRate(r.returnRate)}</strong>
                <small>{won(r.totalAsset)}원</small>
              </span>
            </li>
          ))}
          {ranking.me && !ranking.ranking.some((r) => r.me) && (
            <li className="mock-rank-item mock-rank-me mock-rank-mine">
              <span className="mock-rank-no">{ranking.me.rank}</span>
              <span className="mock-rank-name">{ranking.me.nickname}<em>나</em></span>
              <span className="mock-rank-value">
                <strong className={tone(ranking.me.returnRate)}>{signedRate(ranking.me.returnRate)}</strong>
                <small>{won(ranking.me.totalAsset)}원</small>
              </span>
            </li>
          )}
        </ol>
      )}
      <p className="mock-note">
        {rankPeriod === 'WEEK'
          ? '이번 주 수익률 = 지난주 마지막 장 마감 기록 대비 (이번 주에 시작했으면 1,000만 원 대비)'
          : '총자산 = 현금 + 주문에 묶인 돈 + 보유 주식 평가금액 · 거래 1번 이상 한 계좌만'}
      </p>
    </section>
  )

  // 불러오는 중 / 오류
  if (!data && !error) {
    return <main className="mock-page"><p className="mock-status">불러오는 중...</p></main>
  }
  if (!data && error) {
    return <main className="mock-page"><p className="mock-status mock-down">{error}</p></main>
  }

  // 로그아웃 상태: 안내 + 랭킹
  if (!data.loggedIn) {
    return (
      <main className="mock-page">
        <div className="mock-guest">
          <section className="mock-card mock-guest-card">
            <span className="mock-guest-icon">💰</span>
            <h1>가상 1,000만 원으로 모의투자</h1>
            <p>실제 시세로 사고팔고, 수익률 랭킹에 도전해보세요. 진짜 돈은 들지 않아요.</p>
            <ul>
              <li>토스증권 실시간 가격으로 체결되는 시장가 · 지정가 주문</li>
              <li>평균 단가 · 평가손익 · 종목 비중 자동 계산</li>
              <li>AI 비서에게 "내 포트폴리오 어때?" 물어보기</li>
            </ul>
            <div className="mock-guest-actions">
              <Link to="/login" className="btn btn-primary">로그인하고 시작하기</Link>
              <Link to="/signup" className="btn btn-ghost">회원가입</Link>
            </div>
          </section>
          {rankingBox}
        </div>
      </main>
    )
  }

  const account = data.account

  // 그래프 기간 자르기 (마지막 "지금" 점은 항상 포함)
  const allPoints = history?.points ?? []
  const lastDay = allPoints.length ? new Date(allPoints[allPoints.length - 1].date).getTime() : 0   // 오늘 (마지막 "지금" 점)
  const historyPoints = historyDays === 0
    ? allPoints
    : allPoints.filter((p) => p.live || (lastDay - new Date(p.date).getTime()) / 86400000 <= historyDays)

  return (
    <main className="mock-page">

      {/* ===== 요약 ===== */}
      <section className="mock-summary">
        <div className="mock-summary-main">
          <span className="mock-label">총자산</span>
          <strong className="mock-total">{won(account.totalAsset)}<small>원</small></strong>
          <span className={`mock-total-change ${tone(account.profit)}`}>
            {signedWon(account.profit)}원 ({signedRate(account.returnRate)})
          </span>
          <span className="mock-muted">시작 자금 {won(account.initCash)}원 대비</span>
        </div>
        <div className="mock-summary-item">
          <span className="mock-label">주문 가능 현금</span>
          <strong>{won(account.cash)}원</strong>
          {Number(account.pendingCash) > 0 && (
            <span className="mock-small mock-muted">매수 주문 대기 {won(account.pendingCash)}원</span>
          )}
        </div>
        <div className="mock-summary-item">
          <span className="mock-label">주식 평가금액</span>
          <strong>{won(account.stockValue)}원</strong>
          <span className={`mock-small ${tone(account.stockProfit)}`}>평가손익 {signedWon(account.stockProfit)}원</span>
        </div>
        <div className="mock-summary-item mock-summary-actions">
          <Link
            to={`/ai?q=${encodeURIComponent('내 모의투자 포트폴리오 어때? 비중이랑 수익/손실 종목 짚어줘')}`}
            className="btn btn-primary mock-ai-btn"
          >
            ✨ AI 포트폴리오 코치
          </Link>
          {!confirmReset ? (
            <button type="button" className="mock-reset" onClick={() => setConfirmReset(true)}>계좌 초기화</button>
          ) : (
            <span className="mock-reset-confirm">
              보유 종목·주문·거래 내역이 모두 지워져요.
              <button type="button" className="mock-reset-yes" onClick={doReset}>초기화</button>
              <button type="button" className="mock-reset-no" onClick={() => setConfirmReset(false)}>취소</button>
            </span>
          )}
        </div>
      </section>

      <div className="mock-grid">

        {/* 왼쪽: 보유 종목 + 거래 내역 */}
        <div className="mock-col-main">
          {/* ===== 수익률 추이 ===== */}
          <section className="mock-card mock-history">
            <div className="mock-card-head">
              <h2>📈 내 수익률 추이</h2>
              <strong className={`mock-history-now ${tone(account.returnRate)}`}>{signedRate(account.returnRate)}</strong>
              <div className="mock-seg mock-seg-right">
                {HISTORY_PERIODS.map((p) => (
                  <button
                    key={p.value}
                    type="button"
                    className={historyDays === p.value ? 'mock-seg-active' : ''}
                    onClick={() => setHistoryDays(p.value)}
                  >
                    {p.label}
                  </button>
                ))}
              </div>
            </div>
            {historyPoints.length > 0 && <ReturnChart points={historyPoints} />}
            <p className="mock-note">
              {historyPoints.length < 2
                ? '평일 장 마감(15:40)마다 하루 한 점씩 쌓여요. 다음 장 마감부터 선이 그려져요.'
                : '평일 장 마감(15:40) 기록 + 마지막 점은 지금 가격 기준'}
            </p>
          </section>

          {/* ===== 보유 종목 ===== */}
          <section className="mock-card mock-holdings">
            <div className="mock-card-head">
              <h2>보유 종목</h2>
              <span className="mock-muted">{holdings.length}개</span>
            </div>
            {holdings.length === 0 ? (
              <p className="mock-empty">아직 보유한 종목이 없어요. 주문 칸에서 첫 매수를 해보세요.</p>
            ) : (
              <table className="mock-table mock-holding-table">
                <thead>
                  <tr>
                    <th>종목</th>
                    <th>보유</th>
                    <th>평균 단가</th>
                    <th>현재가</th>
                    <th>평가금액</th>
                    <th>평가손익</th>
                    <th>비중</th>
                  </tr>
                </thead>
                <tbody>
                  {holdings.map((h) => (
                    <tr
                      key={h.stockId}
                      className={h.stockId === selectedId ? 'mock-row-active' : ''}
                      onClick={() => selectStock(h.stockId)}
                      title="눌러서 주문 칸에 이 종목 선택"
                    >
                      <td className="mock-cell-name">
                        <strong>{h.name}</strong>
                        <small>{h.symbol}</small>
                      </td>
                      <td data-label="보유">{won(h.quantity)}주</td>
                      <td data-label="평균 단가">{won(h.avgPrice)}</td>
                      <td data-label="현재가">{won(h.price)}</td>
                      <td data-label="평가금액">{won(h.value)}</td>
                      <td data-label="평가손익" className={tone(h.profit)}>
                        <span>
                          {signedWon(h.profit)}
                          <small>{signedRate(h.returnRate)}</small>
                        </span>
                      </td>
                      <td data-label="비중">
                          <span>
                          <span className="mock-weight">
                            <i style={{ width: `${Math.min(100, Number(h.weight))}%` }} />
                          </span>
                          {Number(h.weight).toFixed(1)}%
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          {/* ===== 거래 내역 ===== */}
          <section className="mock-card mock-trades">
            <div className="mock-card-head">
              <h2>거래 내역</h2>
              <span className="mock-muted">{trades.total}건</span>
            </div>
            {trades.items.length === 0 ? (
              <p className="mock-empty">거래 내역이 없어요.</p>
            ) : (
              <table className="mock-table mock-trade-table">
                <thead>
                  <tr>
                    <th>시각</th>
                    <th>종목</th>
                    <th>구분</th>
                    <th>수량</th>
                    <th>체결가</th>
                    <th>금액</th>
                    <th>실현손익</th>
                  </tr>
                </thead>
                <tbody>
                  {trades.items.map((t) => (
                    <tr key={t.tradeId}>
                      <td className="mock-cell-time">{formatTime(t.insAt)}</td>
                      <td className="mock-cell-name"><strong>{t.name}</strong></td>
                      <td>
                        <span className={`mock-side-chip ${t.side === 'BUY' ? 'mock-side-chip-buy' : 'mock-side-chip-sell'}`}>
                          {t.orderType === 'LIMIT' ? '지정 ' : ''}{t.side === 'BUY' ? '매수' : '매도'}
                        </span>
                      </td>
                      <td data-label="수량">{won(t.quantity)}주</td>
                      <td data-label="체결가"><span>{won(t.price)}{t.priceSource === 'CLOSE' && <small className="mock-close-tag">종가</small>}</span></td>
                      <td data-label="금액">{won(t.amount)}</td>
                      <td data-label="실현손익" className={t.profit == null ? 'mock-muted' : tone(t.profit)}>
                        {t.profit == null ? '-' : signedWon(t.profit)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {trades.items.length < trades.total && (
              <button type="button" className="mock-more" onClick={() => loadTrades(trades.page + 1)}>더보기</button>
            )}
          </section>
        </div>

        {/* 오른쪽: 주문 + 미체결 주문 + 랭킹 */}
        <div className="mock-col-side">
          {/* ===== 주문 ===== */}
          <section className="mock-card mock-order">
            <div className="mock-side-tabs" role="tablist">
              <button
                type="button"
                className={`mock-side-tab mock-side-buy ${side === 'BUY' ? 'mock-side-active' : ''}`}
                onClick={() => { setSide('BUY'); setQuantity(''); setOrderMsg(null) }}
              >
                매수
              </button>
              <button
                type="button"
                className={`mock-side-tab mock-side-sell ${side === 'SELL' ? 'mock-side-active' : ''}`}
                onClick={() => { setSide('SELL'); setQuantity(''); setOrderMsg(null) }}
              >
                매도
              </button>
            </div>

            {/* 시장가 / 지정가 */}
            <div className="mock-type-tabs">
              {[['MARKET', '시장가'], ['LIMIT', '지정가']].map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  className={`mock-type-tab ${orderType === value ? 'mock-type-active' : ''}`}
                  onClick={() => changeOrderType(value)}
                >
                  {label}
                </button>
              ))}
            </div>

            <label className="mock-field">
              <span>종목</span>
              <select
                value={selectedId ?? ''}
                onChange={(e) => selectStock(Number(e.target.value))}
              >
                {stocks.map((s) => (
                  <option key={s.stockId} value={s.stockId}>{s.name} ({s.symbol})</option>
                ))}
              </select>
            </label>

            <div className="mock-price-box">
              <span className="mock-label">{priceLabel(selected)}</span>
              <strong>{won(selected?.price)}원</strong>
              {selected?.priceSource === 'CLOSE' && (
                <span className="mock-badge">
                  {isLimit ? '실시간 시세를 못 받는 동안은 지정가 주문이 체결되지 않아요' : '실시간 시세를 못 받아 최근 종가로 체결돼요'}
                </span>
              )}
            </div>

            {/* 지정가: 가격 입력 (± 버튼은 한 호가씩) */}
            {isLimit && (
              <div className="mock-field">
                <span className="mock-field-row">
                  가격
                  <button type="button" className="mock-link-btn" onClick={() => changeOrderType('LIMIT')}>현재가로</button>
                </span>
                <div className="mock-qty">
                  <button type="button" onClick={() => stepPrice(-1)} aria-label="한 호가 내리기">−</button>
                  <input
                    type="number"
                    inputMode="numeric"
                    min="1"
                    placeholder="0"
                    value={limitPrice}
                    onChange={(e) => { setLimitPrice(e.target.value.replace(/[^0-9]/g, '')); setOrderMsg(null) }}
                    aria-label="지정가"
                  />
                  <span className="mock-qty-unit">원</span>
                  <button type="button" onClick={() => stepPrice(1)} aria-label="한 호가 올리기">+</button>
                </div>
                {orderPrice > 0 && price > 0 && (
                  <span className={`mock-price-gap ${tone(orderPrice - price)}`}>
                    현재가 대비 {signedRate(((orderPrice - price) / price) * 100)}
                    {' · '}
                    {side === 'BUY'
                      ? (orderPrice >= price ? '지금 가격 이상이라 장중엔 바로 체결돼요' : '이 가격까지 내려오면 체결')
                      : (orderPrice <= price ? '지금 가격 이하라 장중엔 바로 체결돼요' : '이 가격까지 오르면 체결')}
                  </span>
                )}
              </div>
            )}

            <label className="mock-field">
              <span>수량</span>
              <div className="mock-qty">
                <button type="button" onClick={() => stepQuantity(-1)} aria-label="1주 빼기">−</button>
                <input
                  type="number"
                  inputMode="numeric"
                  min="1"
                  placeholder="0"
                  value={quantity}
                  onChange={(e) => { setQuantity(e.target.value.replace(/[^0-9]/g, '')); setOrderMsg(null) }}
                />
                <span className="mock-qty-unit">주</span>
                <button type="button" onClick={() => stepQuantity(1)} aria-label="1주 더하기">+</button>
              </div>
            </label>

            <div className="mock-quick">
              {QUICK_RATES.map((q) => (
                <button key={q.label} type="button" onClick={() => pickQuick(q.value)} disabled={maxQty < 1}>
                  {q.label}
                </button>
              ))}
            </div>

            <dl className="mock-order-info">
              {side === 'BUY' ? (
                <div><dt>주문 가능</dt><dd>{won(cash)}원 · 최대 {won(maxQty)}주</dd></div>
              ) : (
                <div>
                  <dt>매도 가능</dt>
                  <dd>
                    {won(sellable)}주
                    {holding && sellable !== holding.quantity && ` (보유 ${won(holding.quantity)}주)`}
                    {holding && ` · 평균 ${won(holding.avgPrice)}원`}
                  </dd>
                </div>
              )}
              <div className="mock-order-total">
                <dt>{isLimit ? (side === 'BUY' ? '묶어둘 금액' : '예상 금액') : '예상 금액'}</dt>
                <dd>{won(orderPrice * qty)}원</dd>
              </div>
            </dl>

            <button
              type="button"
              className={`mock-submit ${side === 'BUY' ? 'mock-submit-buy' : 'mock-submit-sell'}`}
              disabled={!selected || qty < 1 || ordering || (isLimit && orderPrice < 1)}
              onClick={submitOrder}
            >
              {ordering
                ? '주문 중...'
                : `${selected?.name ?? ''} ${qty > 0 ? `${won(qty)}주 ` : ''}${isLimit ? '지정가 ' : ''}${side === 'BUY' ? '매수' : '매도'}`}
            </button>

            {orderMsg && <p className={`mock-msg mock-msg-${orderMsg.type}`}>{orderMsg.text}</p>}

            <p className="mock-note">
              {isLimit
                ? (market?.open
                  ? '정규장(09:00~15:30)에 1분마다 가격을 확인해서 체결 · 오늘 장 마감까지 안 되면 자동 취소, 묶어둔 돈은 돌려받아요'
                  : `지금은 장 시간이 아니에요 · ${formatDay(market?.tradingDay)} 정규장(09:00~15:30)에 체결을 기다리고, 그날 장 마감까지 안 되면 자동 취소`)
                : '시장가 주문 · 수수료·세금 없음 · 주문하는 순간의 가격으로 바로 체결'}
            </p>
          </section>

          {/* ===== 미체결 주문 (주문 칸 바로 아래) ===== */}
          <section className="mock-card mock-pending">
            <div className="mock-card-head">
              <h2>미체결 주문</h2>
              <span className="mock-muted">{pendingOrders.length}건</span>
            </div>
            {pendingOrders.length === 0 ? (
              <p className="mock-empty mock-empty-small">체결을 기다리는 지정가 주문이 없어요.</p>
            ) : (
              <ul className="mock-pending-list">
                {pendingOrders.map((o) => {
                  const gap = o.price ? ((Number(o.limitPrice) - Number(o.price)) / Number(o.price)) * 100 : null
                  return (
                    <li key={o.orderId} className="mock-pending-item">
                      <div className="mock-pending-main">
                        <span className={`mock-side-chip ${o.side === 'BUY' ? 'mock-side-chip-buy' : 'mock-side-chip-sell'}`}>
                          {o.side === 'BUY' ? '매수' : '매도'}
                        </span>
                        <strong>{o.name}</strong>
                        <span className="mock-pending-qty">{won(o.quantity)}주</span>
                      </div>
                      <div className="mock-pending-sub">
                        <span>
                          지정가 <b>{won(o.limitPrice)}</b>
                          {gap !== null && <em className={tone(gap)}> ({signedRate(gap)})</em>}
                        </span>
                        <span className="mock-muted">{formatDay(o.orderDate)}까지</span>
                      </div>
                      <button type="button" className="mock-cancel" onClick={() => cancelOrder(o.orderId)}>취소</button>
                    </li>
                  )
                })}
              </ul>
            )}
          </section>

          {/* ===== 랭킹 ===== */}
          {rankingBox}
        </div>
      </div>
    </main>
  )
}

export default MockTradePage
