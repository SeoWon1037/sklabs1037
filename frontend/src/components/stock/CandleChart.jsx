// ============================================
// 캔들 차트 (TradingView Lightweight Charts)
// - 위 칸: 캔들(시가·고가·저가·종가) + 이동평균선(선택) + 기간 최고/최저 표시
// - 아래 칸: 거래량 막대 (가격축과 섞이지 않게 별도 칸)
// - 마우스를 올리면 위쪽 범례에 그날 시가/고가/저가/종가/거래량
// - candles: [{ date, open, high, low, close, volume }] 오래된 날짜부터 (숫자)
// ============================================
import { useEffect, useRef, useState } from 'react'
import {
  createChart,
  createSeriesMarkers,
  CandlestickSeries,
  HistogramSeries,
  LineSeries,
  CrosshairMode,
} from 'lightweight-charts'
import { useTheme } from '../../theme/useTheme'

// 한국 주식 관례: 상승 빨강 / 하락 파랑
const UP = '#e5484d'
const DOWN = '#3b82f6'

// 이동평균선
const MA_LINES = [
  { days: 5, color: '#f59e0b' },
  { days: 20, color: '#a855f7' },
]

// 거래량 칸 높이 (px)
const VOLUME_PANE_HEIGHT = 110

// 테마별 차트 색 (CSS 변수와 같은 값)
const THEME_COLORS = {
  dark: { text: '#a1a1aa', grid: '#1f1f22', border: '#27272a' },
  light: { text: '#71717a', grid: '#f4f4f5', border: '#e4e4e7' },
}

const formatPrice = (value) => Math.round(value).toLocaleString('ko-KR')

// 거래량: 32,388,349 → 3,239만
const formatVolume = (value) =>
  value >= 1e8 ? `${(value / 1e8).toFixed(1)}억` : value >= 1e4 ? `${Math.round(value / 1e4).toLocaleString('ko-KR')}만` : value.toLocaleString('ko-KR')

// 차트가 주는 시간 값(문자열 / {year, month, day}) → "2026-10-16"
const toDateKey = (time) => {
  if (typeof time === 'string') return time
  if (time && typeof time === 'object') {
    return `${time.year}-${String(time.month).padStart(2, '0')}-${String(time.day).padStart(2, '0')}`
  }
  return null
}

// 아래 날짜 눈금: 2026년 / 10월 / 10/16
const tickFormatter = (time, tickType) => {
  const [y, m, d] = toDateKey(time).split('-').map(Number)
  if (tickType === 0) return `${y}년`
  if (tickType === 1) return `${m}월`
  return `${m}/${d}`
}

// 이동평균: 최근 n개 종가의 평균
const movingAverage = (candles, n) => {
  const result = []
  let sum = 0
  candles.forEach((c, i) => {
    sum += c.close
    if (i >= n) sum -= candles[i - n].close
    if (i >= n - 1) result.push({ time: c.date, value: sum / n })
  })
  return result
}

function CandleChart({ candles, showMA, height = 480 }) {

  const containerRef = useRef(null)
  const { theme } = useTheme()

  // 마우스가 가리키는 캔들 (없으면 가장 최근 캔들을 범례에 표시)
  const [hovered, setHovered] = useState(null)

  useEffect(() => {

    const el = containerRef.current
    if (!el || candles.length === 0) return

    const colors = THEME_COLORS[theme] ?? THEME_COLORS.dark

    // ===== 차트 만들기 =====
    const chart = createChart(el, {
      autoSize: true,
      layout: {
        background: { color: 'transparent' },
        textColor: colors.text,
        fontFamily: "'Pretendard Variable', Pretendard, sans-serif",
        panes: { separatorColor: colors.border },
      },
      grid: {
        vertLines: { color: colors.grid },
        horzLines: { color: colors.grid },
      },
      rightPriceScale: {
        borderColor: colors.border,
        scaleMargins: { top: 0.1, bottom: 0.08 },
      },
      timeScale: {
        borderColor: colors.border,
        tickMarkFormatter: tickFormatter,
      },
      crosshair: { mode: CrosshairMode.Normal },
      localization: { locale: 'ko-KR', priceFormatter: formatPrice },
    })

    // ===== 위 칸(0번 칸): 캔들 =====
    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: UP,
      downColor: DOWN,
      borderUpColor: UP,
      borderDownColor: DOWN,
      wickUpColor: UP,
      wickDownColor: DOWN,
    })
    candleSeries.setData(candles.map((c) => ({ time: c.date, open: c.open, high: c.high, low: c.low, close: c.close })))

    // ===== 위 칸: 이동평균선 =====
    if (showMA) {
      MA_LINES.forEach(({ days, color }) => {
        const line = chart.addSeries(LineSeries, {
          color,
          lineWidth: 1,
          priceLineVisible: false,
          lastValueVisible: false,
          crosshairMarkerVisible: false,
        })
        line.setData(movingAverage(candles, days))
      })
    }

    // ===== 아래 칸(1번 칸): 거래량 — 마지막 숫자 1이 "새 칸에 그려라"라는 뜻 =====
    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: 'volume' },
      lastValueVisible: false,
      priceLineVisible: false,
    }, 1)
    volumeSeries.setData(candles.map((c) => ({
      time: c.date,
      value: c.volume,
      color: c.close >= c.open ? 'rgba(229, 72, 77, 0.55)' : 'rgba(59, 130, 246, 0.55)',
    })))
    chart.panes()[1]?.setHeight(VOLUME_PANE_HEIGHT)

    // ===== 기간 최고 / 최저 표시 =====
    const maxCandle = candles.reduce((a, b) => (b.high > a.high ? b : a))
    const minCandle = candles.reduce((a, b) => (b.low < a.low ? b : a))
    const markers = [
      { time: maxCandle.date, position: 'aboveBar', color: UP, shape: 'arrowDown', text: `최고 ${formatPrice(maxCandle.high)}` },
      { time: minCandle.date, position: 'belowBar', color: DOWN, shape: 'arrowUp', text: `최저 ${formatPrice(minCandle.low)}` },
    ].sort((a, b) => (a.time < b.time ? -1 : 1))   // 표시는 날짜 순서여야 함
    createSeriesMarkers(candleSeries, markers)

    chart.timeScale().fitContent()

    // ===== 마우스 위치 → 범례 =====
    const byDate = new Map(candles.map((c) => [c.date, c]))
    chart.subscribeCrosshairMove((param) => {
      const key = param.time ? toDateKey(param.time) : null
      setHovered(key ? byDate.get(key) ?? null : null)
    })

    // 화면을 떠나거나 데이터가 바뀌면 차트 정리
    return () => chart.remove()

  }, [candles, showMA, theme])

  // 범례: 마우스가 가리키는 캔들, 없으면 가장 최근
  const shown = hovered ?? candles[candles.length - 1]
  const shownClass = shown && shown.close >= shown.open ? 'up' : 'down'

  return (
    <div className="candle-chart">
      {shown && (
        <div className="candle-legend">
          <span className="candle-legend-date">{shown.date}</span>
          <span>시가 <strong className={shownClass}>{formatPrice(shown.open)}</strong></span>
          <span>고가 <strong className="up">{formatPrice(shown.high)}</strong></span>
          <span>저가 <strong className="down">{formatPrice(shown.low)}</strong></span>
          <span>종가 <strong className={shownClass}>{formatPrice(shown.close)}</strong></span>
          <span>거래량 <strong>{formatVolume(shown.volume)}</strong></span>
          {showMA && (
            <span className="candle-legend-ma">
              <i style={{ background: MA_LINES[0].color }} />5일
              <i style={{ background: MA_LINES[1].color }} />20일
            </span>
          )}
        </div>
      )}
      <div ref={containerRef} style={{ height }} />
    </div>
  )
}

export default CandleChart