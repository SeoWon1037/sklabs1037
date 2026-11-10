// ============================================
// 🤝 친구 초대 카드 (내 활동 화면)
// - 내 초대 링크 복사 · 공유, 지금까지 초대한 사람
// - 친구가 이 링크로 들어와 가입하면(3일 안) 두 사람 모두 배지: 나 🤝(5명이면 📣) · 친구 🎈
// - 백엔드: GET /api/invite
// ============================================
import { useEffect, useState } from 'react'

function InviteCard() {

  const [data, setData] = useState(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    let ignore = false
    fetch('/api/invite')
      .then((r) => r.json())
      .then((j) => { if (!ignore && j.success) setData(j) })
      .catch(() => {})
    return () => { ignore = true }
  }, [])

  if (!data) return null

  const link = `${window.location.origin}/?ref=${data.code}`
  const text = 'SKLabs에서 가상 3,000만 원으로 모의투자 같이 해요!'

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
      setTimeout(() => setCopied(false), 1800)
    } catch { /* 복사를 못 하면 주소를 길게 눌러 복사 */ }
  }
  const share = async () => {
    try { await navigator.share({ title: 'SKLabs', text, url: link }) } catch { /* 닫으면 그대로 */ }
  }

  return (
    <section className="act-card act-invite">
      <h2>🤝 친구 초대 <small>{data.count}명 초대함</small></h2>
      <p className="act-invite-desc">
        친구가 내 링크로 들어와 가입하면 두 사람 모두 배지를 받아요. (나 🤝, {data.many}명이면 📣 · 친구 🎈)
        {data.inviterName && <> 나는 <b>{data.inviterName}</b>님의 초대로 왔어요.</>}
      </p>
      <div className="act-invite-row">
        <input readOnly value={link} aria-label="내 초대 링크" onFocus={(e) => e.target.select()} />
        <button type="button" className="act-invite-btn" onClick={copy}>{copied ? '복사했어요' : '링크 복사'}</button>
        {typeof navigator.share === 'function' && <button type="button" className="act-invite-btn" onClick={share}>공유</button>}
      </div>
      {data.invited.length > 0 && (
        <ul className="act-invite-list" aria-label="내가 초대한 사람">
          {data.invited.map((u) => <li key={`${u.nickname}-${u.at}`}>{u.nickname}</li>)}
        </ul>
      )}
    </section>
  )
}

export default InviteCard
