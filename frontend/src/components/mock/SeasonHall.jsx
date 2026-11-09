// ============================================
// 🏆 모의투자 주간 시즌 명예의 전당 (수익률 랭킹 > [🏆 지난 시즌])
// - 매주 토요일 09:00 그 주(월~금) 수익률 TOP 3 (사람만), 최근 8주
// - 백엔드: GET /api/mock/seasons?weeks=8
// ============================================
import { useEffect, useMemo, useState } from 'react'

const MEDAL = { 1: '🥇', 2: '🥈', 3: '🥉' }
const signed = (v) => `${Number(v) > 0 ? '+' : ''}${Number(v).toFixed(2)}%`
const range = (start) => {
  const d = new Date(`${start}T00:00:00`)
  const f = new Date(d.getTime() + 4 * 86400000)
  return `${d.getMonth() + 1}/${d.getDate()} ~ ${f.getMonth() + 1}/${f.getDate()}`
}

function SeasonHall() {

  const [rows, setRows] = useState(null)

  useEffect(() => {
    let ignore = false
    fetch('/api/mock/seasons?weeks=8')
      .then((r) => r.json())
      .then((j) => { if (!ignore) setRows(j.success ? j.seasons : []) })
      .catch(() => { if (!ignore) setRows([]) })
    return () => { ignore = true }
  }, [])

  const weeks = useMemo(() => {
    const map = new Map()
    ;(rows ?? []).forEach((r) => map.set(r.weekStart, [...(map.get(r.weekStart) ?? []), r]))
    return [...map.entries()]
  }, [rows])

  if (rows === null) return <p className="mock-empty">불러오는 중...</p>
  if (weeks.length === 0) return <p className="mock-empty">첫 시즌은 이번 주 토요일 아침에 마감돼요. 이번 주 1등에 도전해보세요! 👑</p>

  return (
    <div className="season-hall">
      {weeks.map(([start, list]) => (
        <div key={start} className="season-week">
          <p className="season-week-title">{range(start)}</p>
          <ol>
            {list.map((r) => (
              <li key={`${start}-${r.nickname}`}>
                <span>{MEDAL[r.rank] ?? r.rank}</span>
                <b>{r.nickname}</b>
                <em className={Number(r.returnRate) >= 0 ? 'mock-up' : 'mock-down'}>{signed(r.returnRate)}</em>
              </li>
            ))}
          </ol>
        </div>
      ))}
    </div>
  )
}

export default SeasonHall
