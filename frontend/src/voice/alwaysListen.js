// ============================================
// 🎤 항상 듣기 (한 번 켜면 이 기기에서 계속 켜져 있음 — localStorage)
// - 켜 두면 사이트가 열려 있는 동안 마이크가 계속 듣고 있다가, "챗봇"을 먼저 부른 말만 실행함
//     "챗봇아, 모의투자 열어줘" → 모의투자 화면 / "챗봇, 오늘 날씨 어때?" → AI 챗봇이 답함 / "챗봇" → "네" 하고 다음 말을 들음
//   부르지 않은 말(옆 사람 말 · TV 소리)은 버림 → 엉뚱한 질문이 AI로 가지 않음
// - AI 챗봇 화면에서는 부르지 않아도 됨 (대화 중이므로 말하면 바로 질문)
// - 브라우저 한계: 사이트(탭)가 열려 있고 보이는 동안만. 처음 한 번은 마이크 권한을 허용해야 함.
//   아이폰 홈 화면 앱 · 카카오톡 안 화면처럼 음성 인식이 없는 곳에서는 못 씀
// ============================================
export const ALWAYS_KEY = 'sklabs.voice.always'
const EVENT = 'sklabs:voice-always'

export const alwaysOn = () => {
  try { return localStorage.getItem(ALWAYS_KEY) === '1' } catch { return false }
}

export const setAlways = (on) => {
  try { localStorage.setItem(ALWAYS_KEY, on ? '1' : '0') } catch { /* 기억 못 해도 이번 화면에서는 동작 */ }
  window.dispatchEvent(new CustomEvent(EVENT, { detail: on }))
}

// 설정이 바뀌면 알려줌 → 해제 함수
export const onAlwaysChange = (fn) => {
  const handler = (e) => fn(Boolean(e.detail))
  window.addEventListener(EVENT, handler)
  return () => window.removeEventListener(EVENT, handler)
}

// 부르는 말: 맨 앞에 와야 함 (음성 인식이 "챗봇"을 조금 다르게 적는 경우 포함)
const WAKE = /^(?:(?:헤이|하이|저기|안녕|야|오케이)\s*)?(?:(?:ai|에이아이)\s*)?(?:챗\s?봇|쳇\s?봇|챗\s?bot|채팅\s?봇|챗못|챗복|책봇|잿봇)(?:아|야|님|씨)?[\s,.!?]*/i

/**
 * @param {string} heard 들은 말
 * @returns {string | null} 부르지 않았으면 null, 불렀으면 뒤에 이어진 말 (부르기만 했으면 '')
 */
export function wakeCommand(heard) {
  const text = String(heard ?? '').replace(/\s+/g, ' ').trim()
  const m = text.match(WAKE)
  if (!m) return null
  return text.slice(m[0].length).trim()
}
