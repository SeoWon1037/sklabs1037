// ============================================
// 📸 내 성적 공유 카드 (화면용) - /activity 의 "카드 만들기", /s/:code 공유 페이지
// ============================================
import { TYPES } from '../../pages/persona/personaData'
import { cardStats, periodLabel } from './shareImage'
import './ShareCard.css'

function ShareCard({ card }) {

  const persona = card.personaType && TYPES[card.personaType]

  return (
    <div className="sc-card">
      <div className="sc-top">
        <strong>SKLabs</strong>
        <span>{periodLabel(card)}</span>
      </div>
      <h2 className="sc-title">{card.nickname}님의<br />이번 주 기록</h2>
      {card.attendStreak > 1 && <p className="sc-streak">🔥 {card.attendStreak}일 연속 출석 중</p>}
      <div className="sc-stats">
        {cardStats(card).map((s) => (
          <div key={s.label} className="sc-stat">
            <span>{s.icon} {s.label}</span>
            <b>{s.value}</b>
          </div>
        ))}
      </div>
      <div className="sc-badges">
        <span>🏅 배지 {card.badgeCount} / {card.badgeTotal}</span>
        <span className="sc-badge-icons">
          {(card.badges || []).map((b) => <i key={b.name} title={b.name}>{b.icon}</i>)}
        </span>
      </div>
      {persona && <p className="sc-persona">투자 성향 {persona.icon} {persona.name}</p>}
      <p className="sc-foot">sklabs1037.com</p>
    </div>
  )
}

export default ShareCard
