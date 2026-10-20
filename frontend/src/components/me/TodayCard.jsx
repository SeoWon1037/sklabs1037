// ============================================
// 📅 오늘의 SKLabs (메인 로그인 카드 안)
// - 로그인하고 메인을 열면 출석 도장 + 오늘 할 일 4개 체크리스트
//   🎯 예측 · 📰 뉴스 퀴즈 · 🤖 AI 매매 일지 보기 · 💬 토론 한 줄
// - "AI 매매 일지 보기"는 서버가 모르는 일이라 이 브라우저가 기억 (AI 리그 화면을 열면 오늘 날짜 저장)
// - 백엔드: GET /api/me/today
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './TodayCard.css'

import { LEAGUE_SEEN_KEY } from './todayKeys'

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
    <section className="today" aria-label="오늘의 SKLabs">
      <div className="today-head">
        <strong>📅 오늘의 SKLabs</strong>
        <span className="today-streak">{data.attendStreak > 1 ? `🔥 ${data.attendStreak}일 연속 출석` : '✅ 출석 완료'}</span>
      </div>
      <div className="today-bar" aria-label={`오늘 할 일 ${done} / ${tasks.length}`}>
        <span style={{ width: `${(done / tasks.length) * 100}%` }} />
      </div>
      <ul className="today-tasks">
        {tasks.map((t) => (
          <li key={t.key}>
            <Link to={t.link} className={`today-task ${t.done ? 'today-done' : ''}`}>
              <span className="today-check" aria-hidden="true">{t.done ? '✓' : t.icon}</span>
              <span className="today-text">
                <b>{t.title}</b>
                <small>{t.sub}</small>
              </span>
              <span className="today-go" aria-hidden="true">›</span>
            </Link>
          </li>
        ))}
      </ul>
      {done === tasks.length && <p className="today-all">🎉 오늘 할 일을 다 했어요!</p>}
    </section>
  )
}

export default TodayCard
