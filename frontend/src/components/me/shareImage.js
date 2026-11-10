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

// ============================================
// 공통: 캔버스 → 휴대폰은 공유 창(카톡·인스타), 아니면 PNG 내려받기
// ============================================
export function saveCanvas(canvas, filename, text) {
  canvas.toBlob(async (blob) => {
    if (!blob) return
    const file = new File([blob], filename, { type: 'image/png' })
    if (navigator.canShare?.({ files: [file] })) {
      try {
        await navigator.share({ files: [file], text })
        return
      } catch { /* 취소하면 내려받기로 */ }
    }
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = filename
    a.click()
    URL.revokeObjectURL(a.href)
  }, 'image/png')
}

const newCard = (colors) => {
  const canvas = document.createElement('canvas')
  canvas.width = W
  canvas.height = H
  const ctx = canvas.getContext('2d')
  const bg = ctx.createLinearGradient(0, 0, W, H)
  colors.forEach((c, i) => bg.addColorStop(i / (colors.length - 1), c))
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, W, H)
  ctx.fillStyle = '#fff'
  ctx.textBaseline = 'alphabetic'
  ctx.font = `800 52px ${FONT}`
  ctx.fillText('SKLabs', 80, 130)
  return { canvas, ctx }
}

// 폭에 맞을 때까지 글자를 줄여서 한 줄로
const fitText = (ctx, text, x, y, maxWidth, weight, size) => {
  let s = size
  do {
    ctx.font = `${weight} ${s}px ${FONT}`
    s -= 4
  } while (ctx.measureText(text).width > maxWidth && s > 24)
  ctx.fillText(text, x, y)
}

// 폭에 맞춰 여러 줄로 (낱말 단위)
const wrapText = (ctx, text, x, y, maxWidth, lineHeight, maxLines) => {
  const words = String(text).split(' ')
  let line = ''
  let lines = 0
  for (const w of words) {
    const next = line ? `${line} ${w}` : w
    if (ctx.measureText(next).width > maxWidth && line) {
      ctx.fillText(line, x, y + lines * lineHeight)
      lines += 1
      line = w
      if (lines >= maxLines) return
    } else line = next
  }
  if (line) ctx.fillText(line, x, y + lines * lineHeight)
}

const won = (n) => `${Math.round(Number(n)).toLocaleString('ko-KR')}원`
const dayText = (d) => `${Number(d.slice(0, 4))}년 ${Number(d.slice(5, 7))}월 ${Number(d.slice(8, 10))}일`

// ⏳ "그때 샀더라면" 결과 카드 (r: GET /api/whatif 응답)
export function drawWhatIfCard(r) {

  const up = Number(r.profit) >= 0
  const { canvas, ctx } = newCard(up ? ['#7f1d1d', '#be123c', '#f97316'] : ['#0f172a', '#1e3a8a', '#2563eb'])

  ctx.font = `500 34px ${FONT}`
  ctx.globalAlpha = 0.8
  ctx.textAlign = 'right'
  ctx.fillText('그때 샀더라면', W - 80, 128)
  ctx.textAlign = 'left'
  ctx.globalAlpha = 1

  ctx.font = `600 44px ${FONT}`
  ctx.globalAlpha = 0.9
  ctx.fillText(`${dayText(r.buyDate)}에`, 80, 270)
  ctx.globalAlpha = 1
  fitText(ctx, r.stock.name, 80, 370, W - 160, 800, 88)
  ctx.font = `600 44px ${FONT}`
  ctx.globalAlpha = 0.9
  ctx.fillText(`${won(r.amount)}어치 샀더라면`, 80, 450)
  ctx.globalAlpha = 1

  ctx.fillStyle = 'rgba(255,255,255,0.14)'
  roundRect(ctx, 80, 520, W - 160, 420, 44)
  ctx.fill()
  ctx.fillStyle = '#fff'
  ctx.font = `500 38px ${FONT}`
  ctx.globalAlpha = 0.85
  ctx.fillText(`지금 (${dayText(r.nowDate)} 종가 기준)`, 130, 610)
  ctx.globalAlpha = 1
  fitText(ctx, won(r.value), 130, 740, W - 260, 800, 112)
  fitText(ctx, `${up ? '+' : ''}${Number(r.rate).toFixed(2)}%  (${up ? '+' : '−'}${won(Math.abs(r.profit))})`, 130, 850, W - 260, 700, 56)

  ctx.font = `500 36px ${FONT}`
  ctx.globalAlpha = 0.9
  ctx.fillText(`산 날 종가 ${won(r.buyPrice)}  →  최근 종가 ${won(r.nowPrice)}`, 80, 1030)
  if (r.bench) ctx.fillText(`같은 돈을 ${r.bench.name}에 넣었다면 ${Number(r.bench.rate) >= 0 ? '+' : ''}${Number(r.bench.rate).toFixed(2)}%`, 80, 1090)
  ctx.globalAlpha = 0.7
  ctx.font = `500 28px ${FONT}`
  ctx.fillText('배당 · 세금 · 수수료 제외. 과거 수익률이 앞으로의 수익을 뜻하지 않아요.', 80, 1190)
  ctx.font = `600 32px ${FONT}`
  ctx.globalAlpha = 0.85
  ctx.fillText('sklabs1037.com/whatif · 나도 계산해보기', 80, 1270)
  ctx.globalAlpha = 1

  return canvas
}

// 🧭 투자 성향 결과 카드 (code: 유형 코드)
export function drawPersonaCard(code) {

  const t = TYPES[code]
  const { canvas, ctx } = newCard(['#0f766e', '#0e7490', '#4338ca'])

  ctx.font = `500 34px ${FONT}`
  ctx.globalAlpha = 0.8
  ctx.textAlign = 'right'
  ctx.fillText('투자 성향 테스트', W - 80, 128)
  ctx.globalAlpha = 1

  ctx.textAlign = 'center'
  ctx.font = `220px ${FONT}`
  ctx.fillText(t.icon, W / 2, 470)
  ctx.font = `600 44px ${FONT}`
  ctx.globalAlpha = 0.9
  ctx.fillText('나의 투자 성향은', W / 2, 590)
  ctx.globalAlpha = 1
  fitText(ctx, t.name, W / 2, 700, W - 160, 800, 96)
  ctx.font = `600 40px ${FONT}`
  ctx.globalAlpha = 0.85
  ctx.fillText(t.short, W / 2, 770)
  ctx.globalAlpha = 1
  ctx.textAlign = 'left'

  ctx.fillStyle = 'rgba(255,255,255,0.14)'
  roundRect(ctx, 80, 840, W - 160, 300, 44)
  ctx.fill()
  ctx.fillStyle = '#fff'
  ctx.font = `500 38px ${FONT}`
  wrapText(ctx, t.desc, 130, 920, W - 260, 58, 4)

  ctx.font = `600 32px ${FONT}`
  ctx.globalAlpha = 0.85
  ctx.fillText('sklabs1037.com/persona · 너는 어떤 유형이야?', 80, 1270)
  ctx.globalAlpha = 1

  return canvas
}
