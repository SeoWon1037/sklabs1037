// ============================================
// 모의투자 수익률 그래프 (라이브러리 없이 SVG)
// - points: [{ date, totalAsset, returnRate, live? }] 오래된 날짜부터
// - 0% 기준선을 점선으로, 선 색은 마지막 수익률 기준 (+ 빨강 / - 파랑)
// - 마우스를 올리면 그날 수익률·총자산
// - 📈 benchName이 있으면 회색 점선 = "기간 첫날 내 총자산을 지수(KODEX 200)에 넣었다면" 수익률
//   → 두 선이 같은 점에서 출발해서, 지수보다 잘했는지 한눈에
// ============================================
import { useId, useState } from 'react'

const W = 600            // 가로 좌표 기준 (화면 폭에 맞게 늘어남)
const UP = '#e5484d'
const DOWN = '#3b82f6'

const won = (v) => Math.round(Number(v)).toLocaleString('ko-KR')
const signedRate = (v) => `${Number(v) > 0 ? '+' : ''}${Number(v).toFixed(2)}%`

const BENCH = '#9ca3af'

// 기간 첫날(종가가 있는 첫 점) 기준으로 지수에 넣었다면의 수익률 (없으면 null)
function benchRates(points) {
  const base = points.findIndex((p) => p.close != null)
  if (base < 0) return null
  const r0 = Number(points[base].returnRate)
  const c0 = Number(points[base].close)
  return points.map((p, i) => (i < base || p.close == null ? null : ((1 + r0 / 100) * (Number(p.close) / c0) - 1) * 100))
}

function ReturnChart({ points, height = 170, benchName = null }) {

  const gradientId = `ret-${useId().replace(/[^a-zA-Z0-9-_]/g, '')}`
  const [hoverIndex, setHoverIndex] = useState(null)

  const rates = points.map((p) => Number(p.returnRate))
  const last = rates[rates.length - 1] ?? 0
  const color = last >= 0 ? UP : DOWN
  const bench = benchName && points.length > 1 ? benchRates(points) : null
  const benchVals = (bench ?? []).filter((v) => v != null)
  const benchLast = bench ? bench[bench.length - 1] : null

  // 0%가 항상 보이게 범위에 포함
  const min = Math.min(0, ...rates, ...benchVals)
  const max = Math.max(0, ...rates, ...benchVals)
  const pad = (max - min || 1) * 0.15
  const low = min - pad
  const high = max + pad

  const toX = (i) => (points.length === 1 ? W / 2 : (i / (points.length - 1)) * W)
  const toY = (v) => height - ((v - low) / (high - low)) * height
  const zeroY = toY(0)

  const line = rates.map((v, i) => `${i === 0 ? 'M' : 'L'}${toX(i).toFixed(2)},${toY(v).toFixed(2)}`).join(' ')
  const area = `${line} L${toX(rates.length - 1)},${zeroY} L${toX(0)},${zeroY} Z`
  const benchLine = bench
    ? bench.map((v, i) => (v == null ? null : [i, v])).filter(Boolean)
      .map(([i, v], k) => `${k === 0 ? 'M' : 'L'}${toX(i).toFixed(2)},${toY(v).toFixed(2)}`).join(' ')
    : ''

  const handleMove = (e) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const ratio = Math.min(Math.max((e.clientX - rect.left) / rect.width, 0), 1)
    setHoverIndex(Math.round(ratio * (points.length - 1)))
  }

  const hovered = hoverIndex !== null ? points[hoverIndex] : null
  const hoverLeft = hoverIndex !== null ? (toX(hoverIndex) / W) * 100 : 0

  const gap = benchLast != null ? last - benchLast : null

  return (
    <>
    <div
      className="ret-chart"
      style={{ height }}
      onMouseMove={handleMove}
      onMouseLeave={() => setHoverIndex(null)}
    >
      <svg viewBox={`0 0 ${W} ${height}`} preserveAspectRatio="none" width="100%" height={height}>
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.22" />
            <stop offset="100%" stopColor={color} stopOpacity="0.02" />
          </linearGradient>
        </defs>
        <line x1="0" x2={W} y1={zeroY} y2={zeroY} className="ret-chart-zero" vectorEffect="non-scaling-stroke" />
        {points.length > 1 && <path d={area} fill={`url(#${gradientId})`} />}
        {benchLine && (
          <path d={benchLine} fill="none" stroke={BENCH} strokeWidth="1.6" strokeDasharray="5 4" vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
        )}
        {points.length > 1 && (
          <path d={line} fill="none" stroke={color} strokeWidth="2.5" vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
        )}
      </svg>

      <span className="ret-chart-zero-label" style={{ top: `${(zeroY / height) * 100}%` }}>0%</span>

      {/* 마지막 점 (지금) */}
      <span
        className="ret-chart-dot ret-chart-dot-now"
        style={{ left: `${(toX(points.length - 1) / W) * 100}%`, top: `${(toY(last) / height) * 100}%`, background: color }}
      />

      {hovered && (
        <>
          <div className="ret-chart-cursor" style={{ left: `${hoverLeft}%` }} />
          <span
            className="ret-chart-dot"
            style={{ left: `${hoverLeft}%`, top: `${(toY(Number(hovered.returnRate)) / height) * 100}%`, background: color }}
          />
          <div className="ret-chart-tip" style={{ left: `${Math.min(Math.max(hoverLeft, 14), 86)}%` }}>
            <span>{hovered.live ? '지금' : hovered.date.slice(5).replace('-', '.')}</span>
            <strong style={{ color: Number(hovered.returnRate) >= 0 ? UP : DOWN }}>{signedRate(hovered.returnRate)}</strong>
            <small>{won(hovered.totalAsset)}원</small>
            {bench?.[hoverIndex] != null && <small>{benchName} {signedRate(bench[hoverIndex])}</small>}
          </div>
        </>
      )}
    </div>
    {benchLast != null && (
      <div className="ret-legend">
        <span><i style={{ background: color }} />나 {signedRate(last)}</span>
        <span><i className="ret-legend-dash" />{benchName}에 넣었다면 {signedRate(benchLast)}</span>
        <strong className={gap >= 0 ? 'ret-beat' : 'ret-lag'}>
          {gap >= 0 ? `지수보다 ${gap.toFixed(2)}%p 앞서요 👍` : `지수보다 ${Math.abs(gap).toFixed(2)}%p 뒤처져요`}
        </strong>
      </div>
    )}
    </>
  )
}

export default ReturnChart
