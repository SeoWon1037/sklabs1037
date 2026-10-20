// ============================================
// 🤖 AI 주간 코치 (/activity 맨 위)
// - 매주 월요일 아침, AI가 지난주 기록을 보고 한 줄 제목 · 돌아보기 · 잘한 점 · 팁 · 이번 주 미션 3개
// - 미션 진행률은 서버가 실제 기록으로 계산 (3개 다 채우면 🎖️ 미션 클리어 배지)
// - 이번 주 리포트가 없으면 "지금 받아보기" (한 주에 한 번)
// - 백엔드: GET /api/me/coach, POST /api/me/coach
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './CoachCard.css'

const md = (d) => `${Number(d.slice(5, 7))}/${Number(d.slice(8, 10))}`

function CoachCard() {

  const [data, setData] = useState(null)
  const [making, setMaking] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let ignore = false
    fetch('/api/me/coach')
      .then((res) => res.json())
      .then((json) => { if (!ignore && json.success) setData(json) })
      .catch(() => {})
    return () => { ignore = true }
  }, [])

  const make = async () => {
    setMaking(true)
    setError('')
    try {
      const res = await fetch('/api/me/coach', { method: 'POST' })
      const json = await res.json()
      if (!json.success) throw new Error(json.message)
      setData(json)
    } catch (e) {
      setError(e.message || '리포트를 만들지 못했어요.')
    } finally {
      setMaking(false)
    }
  }

  if (!data) return null

  const r = data.report

  if (!r) {
    return (
      <section className="coach coach-empty">
        <p className="coach-eyebrow">🤖 AI 주간 코치 · {md(data.weekStart)} ~ {md(data.weekEnd)}</p>
        <h2>지난주 기록을 보고 이번 주 미션 3개를 골라드려요</h2>
        <p className="coach-muted">매주 월요일 아침 8시에 자동으로 와요. 이번 주 것은 지금 바로 받아볼 수 있어요.</p>
        {data.aiReady && (
          <button type="button" className="btn btn-primary coach-make" onClick={make} disabled={making}>
            {making ? 'AI가 지난주 기록을 보는 중... (10초쯤)' : '✨ 이번 주 리포트 받기'}
          </button>
        )}
        {error && <p className="coach-error">{error}</p>}
      </section>
    )
  }

  const done = r.missions.filter((m) => m.done).length

  return (
    <section className="coach">
      <p className="coach-eyebrow">🤖 AI 주간 코치 · {md(data.weekStart)} ~ {md(data.weekEnd)}</p>
      <h2>{r.headline}</h2>
      <p className="coach-review">{r.review}</p>
      <div className="coach-notes">
        <p><b>👍 잘한 점</b>{r.strength}</p>
        <p><b>💡 이번 주 팁</b>{r.tip}</p>
      </div>

      <div className="coach-missions-head">
        <strong>이번 주 미션</strong>
        <span>{done} / {r.missions.length} 완료</span>
      </div>
      <ul className="coach-missions">
        {r.missions.map((m) => (
          <li key={m.key}>
            <Link to={m.link} className={`coach-mission ${m.done ? 'coach-done' : ''}`}>
              <span className="coach-icon" aria-hidden="true">{m.done ? '✓' : m.icon}</span>
              <span className="coach-mission-body">
                <b>{m.title}</b>
                {m.why && <small>{m.why}</small>}
                <span className="coach-bar" aria-label={`${m.current} / ${m.target}`}>
                  <span style={{ width: `${(m.current / m.target) * 100}%` }} />
                </span>
              </span>
              <span className="coach-count">{m.current}/{m.target}</span>
            </Link>
          </li>
        ))}
      </ul>
      {data.allDone && <p className="coach-clear">🎖️ 이번 주 미션 클리어! 배지를 받았어요</p>}
    </section>
  )
}

export default CoachCard
