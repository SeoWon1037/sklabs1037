// ============================================
// 주가 라인 차트 (라이브러리 없이 SVG로 직접 그림)
// - 작은 모드: 종목 카드의 미니 차트
// - 상세 모드(detailed): 최고/최저 표시 + 마우스를 올리면 날짜/가격 표시
// - points: [{ date, close }] 오래된 날짜부터
// ============================================
import { useId, useState } from 'react'

// 가로 좌표 기준 크기 (실제 화면 폭에 맞게 늘어남)
const W = 600

const formatPrice = (value) => Number(value).toLocaleString('ko-KR')

function StockChart({ points, height = 60, detailed = false, color = '#3b82f6' }) {

  // 그라데이션 id가 차트끼리 겹치지 않게 (특수문자는 제거)
  const gradientId = `grad-${useId().replace(/[^a-zA-Z0-9-_]/g, '')}`

  // 마우스가 가리키는 데이터 위치 (null이면 표시 안 함)
  const [hoverIndex, setHoverIndex] = useState(null)

  if (!points || points.length < 2) {
    return <div className="chart chart-empty" style={{ height }}>데이터 없음</div>
  }

  // ===== 좌표 계산 =====
  const closes = points.map((p) => Number(p.close))
  const min = Math.min(...closes)
  const max = Math.max(...closes)
  const padding = (max - min || 1) * 0.08          // 위아래 여백
  const low = min - padding
  const high = max + padding

  const toX = (i) => (i / (points.length - 1)) * W
  const toY = (value) => height - ((value - low) / (high - low)) * height

  const line = closes.map((v, i) => `${i === 0 ? 'M' : 'L'}${toX(i).toFixed(2)},${toY(v).toFixed(2)}`).join(' ')
  const area = `${line} L${W},${height} L0,${height} Z`

  // ===== 마우스 위치 → 가장 가까운 날짜 =====
  const handleMouseMove = (e) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const ratio = Math.min(Math.max((e.clientX - rect.left) / rect.width, 0), 1)
    setHoverIndex(Math.round(ratio * (points.length - 1)))
  }

  const hovered = hoverIndex !== null ? points[hoverIndex] : null
  const hoverLeft = hoverIndex !== null ? (hoverIndex / (points.length - 1)) * 100 : 0

  return (
    <div
      className={`chart ${detailed ? 'chart-detailed' : ''}`}
      style={{ height }}
      onMouseMove={detailed ? handleMouseMove : undefined}
      onMouseLeave={detailed ? () => setHoverIndex(null) : undefined}
    >
      <svg viewBox={`0 0 ${W} ${height}`} preserveAspectRatio="none" width="100%" height={height}>
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.25" />
            <stop offset="100%" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={area} fill={`url(#${gradientId})`} />
        <path d={line} fill="none" stroke={color} strokeWidth={detailed ? 2 : 1.5} vectorEffect="non-scaling-stroke" />
      </svg>

      {/* 상세 모드: 기간 최고/최저 */}
      {detailed && (
        <>
          <span className="chart-label chart-label-max">최고 {formatPrice(max)}</span>
          <span className="chart-label chart-label-min">최저 {formatPrice(min)}</span>
        </>
      )}

      {/* 마우스 위치 표시 */}
      {hovered && (
        <>
          <div className="chart-cursor" style={{ left: `${hoverLeft}%` }} />
          <div
            className="chart-dot"
            style={{ left: `${hoverLeft}%`, top: `${(toY(Number(hovered.close)) / height) * 100}%`, background: color }}
          />
          <div className="chart-tooltip" style={{ left: `${Math.min(Math.max(hoverLeft, 12), 88)}%` }}>
            <span>{hovered.date}</span>
            <strong>{formatPrice(hovered.close)}원</strong>
          </div>
        </>
      )}
    </div>
  )
}

export default StockChart