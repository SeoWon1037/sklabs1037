// ============================================
// 📅 오늘의 SKLabs (메인 오른쪽 칸 맨 위, 로그인했을 때만)
// - 메인을 열면 출석 도장 + 오늘 할 일 4칸 (한 일은 ✓)
//   🎯 예측 · 📰 뉴스 퀴즈 · 🤖 AI 매매 일지 보기 · 💬 토론 한 줄
// - "AI 매매 일지 보기"는 서버가 모르는 일이라 이 브라우저가 기억 (AI 리그 화면을 열면 오늘 날짜 저장)
// - 백엔드: GET /api/me/today
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { LEAGUE_SEEN_KEY } from './todayKeys'
import './TodayCard.css'

// 칸이 좁아서 짧은 이름
const SHORT = { predict: '예측', quiz: '뉴스 퀴즈', league: 'AI 매매', board: '토론 한 줄' }

const leagueSeen = (date) => {
  try { return localStorage.getItem(LEAGUE_SEEN_KEY) === date } catch { return false }
}

function TodayCard() {

  const [data, setData] = useState(null)

  useEffect(() => {
    let ignore = false
    fetch('/api/me/today')
      .then((res) => res.json())
      .then((json) => { if (!ignore && json.success) setData(json) })
      .catch(() => {})
    return () => { ignore = true }
  }, [])

  if (!data) return null

  const tasks = data.tasks.map((t) => (t.key === 'league' ? { ...t, done: leagueSeen(data.date) } : t))
  const done = tasks.filter((t) => t.done).length

  return (
    <section className="home-box today" aria-label="오늘의 SKLabs">
      <div className="today-head">
        <strong>📅 오늘의 SKLabs <em>{done}/{tasks.length}</em></strong>
        <span className="today-streak">{data.attendStreak > 1 ? `🔥 ${data.attendStreak}일 연속` : '✅ 출석 완료'}</span>
      </div>
      <ul className="today-tasks">
        {tasks.map((t) => (
          <li key={t.key}>
            <Link to={t.link} className={`today-task ${t.done ? 'today-done' : ''}`} title={`${t.title} · ${t.sub}`}>
              <span className="today-check" aria-hidden="true">{t.done ? '✓' : t.icon}</span>
              <span className="today-name">{SHORT[t.key] || t.title}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

export default TodayCard
