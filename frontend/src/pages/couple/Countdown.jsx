// ============================================
// ⏱ 남은 시간 (일 · 시간 · 분 · 초, 1초마다)
// - date: "2026-12-07" (한국 시간) / time: "19:00" (없으면 그날 0시)
// - 그때가 됐으면 done을 보여 줌
// ============================================
import { useEffect, useState } from 'react'

const pad = (n) => String(n).padStart(2, '0')
const leftUntil = (date, time) => new Date(`${date}T${time || '00:00'}:00+09:00`).getTime() - Date.now()

function Countdown({ date, time, done }) {

  const [left, setLeft] = useState(() => leftUntil(date, time))

  useEffect(() => {
    const tick = () => setLeft(leftUntil(date, time))
    const timer = setInterval(tick, 1000)
    return () => clearInterval(timer)
  }, [date, time])

  if (left <= 0) return <b className="cp-count cp-count-done">{done}</b>

  const s = Math.floor(left / 1000)
  const days = Math.floor(s / 86400)
  return (
    <b className="cp-count" role="timer" aria-label={`${days}일 ${Math.floor((s % 86400) / 3600)}시간 남음`}>
      {days > 0 && <>{days}<i>일</i> </>}{pad(Math.floor((s % 86400) / 3600))}<i>시간</i> {pad(Math.floor((s % 3600) / 60))}<i>분</i> {pad(s % 60)}<i>초</i>
    </b>
  )
}

export default Countdown
