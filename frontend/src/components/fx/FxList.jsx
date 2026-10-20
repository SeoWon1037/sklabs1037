// ============================================
// 💱 오늘의 환율 (메인 "오늘의 환율" 탭)
// - 달러 · 엔(100엔) · 유로 · 위안 · 파운드 → 원화, 전날 대비
// - 백엔드: GET /api/fx (서버 30분 캐시, 고시 환율이라 하루 한 번 바뀜)
// ============================================
import { useEffect, useState } from 'react'

const fmt = (v) => Number(v).toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

// 최근 흐름 작은 선 (10일)
function Spark({ values, cls }) {
  if (!values || values.length < 2) return null
  const min = Math.min(...values)
  const max = Math.max(...values)
  const pts = values
    .map((v, i) => `${(i / (values.length - 1)) * 40},${max === min ? 8 : 15 - ((v - min) / (max - min)) * 14}`)
    .join(' ')
  return (
    <svg className={`fx-spark ${cls}`} viewBox="0 0 40 16" aria-hidden="true">
      <polyline points={pts} fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
    </svg>
  )
}

function FxList() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let ignore = false
    fetch('/api/fx')
      .then((res) => res.json())
      .then((json) => {
        if (ignore) return
        if (json.success) setData(json)
        else setError(json.message || '환율을 불러오지 못했어요.')
      })
      .catch(() => { if (!ignore) setError('서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [])

  return (
    <>
      <ul className="home-quotes">
        {!data && <li className="home-quote-empty">{error || '환율을 불러오는 중...'}</li>}
        {data?.rates.map((r) => {
          const cls = r.change > 0 ? 'up' : r.change < 0 ? 'down' : ''
          return (
            <li key={r.code}>
              <div className="home-quote fx-row">
                <span className="home-quote-rank fx-flag" aria-hidden="true">{r.flag}</span>
                <span className="home-quote-name">
                  {r.code}
                  <small>{r.name}{r.unit > 1 ? ` ${r.unit}` : ''}</small>
                </span>
                <Spark values={r.history} cls={cls} />
                <span className="home-quote-price">{fmt(r.rate)}</span>
                <span className={`home-quote-rate ${cls}`}>
                  {r.change == null ? '-' : `${r.change > 0 ? '▲' : r.change < 0 ? '▼' : ''} ${Math.abs(r.change).toFixed(2)}`}
                </span>
              </div>
            </li>
          )
        })}
      </ul>
      {data && (
        <p className="fx-note">
          {data.date.slice(5)} 고시 기준 · 전날 대비 · 원
          {data.stale && ' · 잠시 연결이 안 돼 지난 값'}
        </p>
      )}
    </>
  )
}

export default FxList
