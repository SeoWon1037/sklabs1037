// ============================================
// 💕 메인 로그인 카드 이름 아래 한 줄
// - 커플이면 "○○님과 200일 ♥" (기념일 당일이면 🎉)
// - 커플이 아니고 받은 신청이 있으면 "💌 커플 신청이 와 있어요"
// - 백엔드: GET /api/couple/summary
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

function CoupleLine() {

  const [data, setData] = useState(null)

  useEffect(() => {
    let ignore = false
    fetch('/api/couple/summary')
      .then((res) => res.json())
      .then((json) => { if (!ignore && json.success) setData(json) })
      .catch(() => {})
    return () => { ignore = true }
  }, [])

  if (!data) return null

  const c = data.couple
  if (c) {
    return (
      <Link to="/couple" className="home-couple">
        {c.next.dDay === 0 ? '🎉 ' : '💕 '}
        <b>{c.partnerName}</b>님과 <b>{c.days.toLocaleString()}일</b> ♥
        {c.next.dDay === 0 && (c.next.label.endsWith('주년') ? ` · 오늘 ${c.next.label}!` : ' 축하해요!')}
      </Link>
    )
  }

  if (data.received > 0) return <Link to="/couple" className="home-couple">💌 커플 신청이 와 있어요</Link>

  return null
}

export default CoupleLine
