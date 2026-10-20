// ============================================
// 📸 공유 카드 → PNG (1080 × 1350, 인스타 세로 비율)
// - 화면의 카드와 같은 내용을 canvas에 직접 그림 (라이브러리 없이)
// ============================================
import { TYPES } from '../../pages/persona/personaData'

const W = 1080
const H = 1350
const FONT = '"Pretendard", "Apple SD Gothic Neo", "Noto Sans KR", "Malgun Gothic", sans-serif'

const roundRect = (ctx, x, y, w, h, r) => {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

export const cardStats = (c) => [
  { icon: '🎯', label: '예측 적중', value: c.predictGraded > 0 ? `${c.predictHits} / ${c.predictGraded}` : `${c.predictVotes}개 참여` },
  { icon: '📰', label: '퀴즈 정답', value: `${c.quizScore}개` },
  { icon: '📅', label: '이번 주 출석', value: `${c.attendDays}일` },
  { icon: '💰', label: '모의투자', value: c.returnRate == null ? '-' : `${c.returnRate > 0 ? '+' : ''}${Number(c.returnRate).toFixed(2)}%` },
]

export const periodLabel = (c) => {
  const f = (d) => `${Number(d.slice(5, 7))}.${Number(d.slice(8, 10))}`
  return `${f(c.from)} ~ ${f(c.to)}`
}

export function drawShareCard(c) {

  const canvas = document.createElement('canvas')
  canvas.width = W
  canvas.height = H
  const ctx = canvas.getContext('2d')

  // 배경
  const bg = ctx.createLinearGradient(0, 0, W, H)
  bg.addColorStop(0, '#1e3a8a')
  bg.addColorStop(0.55, '#6d28d9')
  bg.addColorStop(1, '#db2777')
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, W, H)

  ctx.fillStyle = '#fff'
  ctx.textBaseline = 'alphabetic'
  ctx.font = `800 52px ${FONT}`
  ctx.fillText('SKLabs', 80, 130)
  ctx.font = `500 34px ${FONT}`
  ctx.globalAlpha = 0.8
  ctx.textAlign = 'right'
  ctx.fillText(periodLabel(c), W - 80, 128)
  ctx.textAlign = 'left'
  ctx.globalAlpha = 1

  ctx.font = `800 76px ${FONT}`
  ctx.fillText(`${c.nickname}님의`, 80, 270)
  ctx.fillText('이번 주 기록', 80, 360)

  if (c.attendStreak > 1) {
    ctx.font = `700 38px ${FONT}`
    ctx.fillStyle = '#fde68a'
    ctx.fillText(`🔥 ${c.attendStreak}일 연속 출석 중`, 80, 430)
    ctx.fillStyle = '#fff'
  }

  // 숫자 4칸
  cardStats(c).forEach((s, i) => {
    const x = 80 + (i % 2) * 470
    const y = 480 + Math.floor(i / 2) * 270
    ctx.fillStyle = 'rgba(255,255,255,0.14)'
    roundRect(ctx, x, y, 450, 240, 36)
    ctx.fill()
    ctx.fillStyle = '#fff'
    ctx.font = `500 34px ${FONT}`
    ctx.globalAlpha = 0.85
    ctx.fillText(`${s.icon} ${s.label}`, x + 40, y + 80)
    ctx.globalAlpha = 1
    ctx.font = `800 72px ${FONT}`
    ctx.fillText(s.value, x + 40, y + 185)
  })

  // 배지
  ctx.font = `700 38px ${FONT}`
  ctx.fillText(`🏅 배지 ${c.badgeCount} / ${c.badgeTotal}`, 80, 1110)
  ctx.font = `64px ${FONT}`
  ;(c.badges || []).forEach((b, i) => ctx.fillText(b.icon, 520 + i * 100, 1118))

  const persona = c.personaType && TYPES[c.personaType]
  if (persona) {
    ctx.font = `600 34px ${FONT}`
    ctx.globalAlpha = 0.9
    ctx.fillText(`투자 성향 ${persona.icon} ${persona.name}`, 80, 1180)
    ctx.globalAlpha = 1
  }

  ctx.font = `600 32px ${FONT}`
  ctx.globalAlpha = 0.75
  ctx.fillText('sklabs1037.com · 모의투자 · 예측 게임 · 뉴스 퀴즈', 80, 1270)
  ctx.globalAlpha = 1

  return canvas
}
