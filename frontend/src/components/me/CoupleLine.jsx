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
  const [sent, setSent] = useState('')

  // ♥ 누르면 하트 날리기 (커플 화면으로 가지 않고 바로)
  const heart = async (e) => {
    e.preventDefault()
    e.stopPropagation()
    try {
      const res = await fetch('/api/couple/heart', { method: 'POST' })
      const json = await res.json()
      setSent(json.success ? `💗 오늘 ${json.sent.today}번째 하트!` : json.message)
    } catch {
      setSent('하트를 보내지 못했어요.')
    }
    setTimeout(() => setSent(''), 2000)
  }

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
      <span className="home-couple-row">
        <Link to="/couple" className="home-couple">
          {c.next.dDay === 0 ? '🎉 ' : '💕 '}
          <b>{c.partnerName}</b>님과 <b>{c.days.toLocaleString()}일</b>
          {c.next.dDay === 0 && (c.next.label.endsWith('주년') ? ` · 오늘 ${c.next.label}!` : ' 축하해요!')}
        </Link>
        <button type="button" className="home-couple-heart" onClick={heart} aria-label={`${c.partnerName}님에게 하트 보내기`} title="하트 보내기">♥</button>
        {sent && <span className="home-couple-sent">{sent}</span>}
      </span>
    )
  }

  if (data.received > 0) return <Link to="/couple" className="home-couple">💌 커플 신청이 와 있어요</Link>

  return null
}

export default CoupleLine
