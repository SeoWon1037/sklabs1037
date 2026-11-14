// ============================================
// 🎤 항상 듣기 (한 번 켜면 이 기기에서 계속 켜져 있음 — localStorage)
// - 설치한 앱(홈 화면 앱)에서는 따로 켜지 않아도 처음부터 켜져 있음 (끄면 그 뒤로는 꺼진 채로)
// - 켜 두면 사이트가 열려 있는 동안 마이크가 계속 듣고 있다가, 말소리가 들리면 챗봇 창이 저절로 열리고
//   말한 내용이 그대로 입력되어 바로 실행됨 ("모의투자 열어줘" → 모의투자 화면 / "오늘 날씨 어때?" → 챗봇이 답함)
//   "챗봇아, …"라고 불러도 됨 (부르는 말은 떼고 실행, "챗봇"만 부르면 "네" 하고 다음 말을 들음)
// - 주의: 옆 사람 말 · TV 소리도 질문으로 들어갈 수 있음 (한 글자짜리 소리는 무시). 조용히 두려면 끄기
// - AI 챗봇 화면에서도 같음 (말하면 바로 질문)
// - 브라우저 한계: 사이트(탭)가 열려 있고 보이는 동안만. 처음 한 번은 마이크 권한을 허용해야 함.
//   아이폰 홈 화면 앱 · 카카오톡 안 화면처럼 음성 인식이 없는 곳에서는 못 씀
// ============================================
export const ALWAYS_KEY = 'sklabs.voice.always'
const EVENT = 'sklabs:voice-always'

// 설치한 앱(홈 화면에 추가 · PC에 설치)으로 열었는지
export const isApp = () => {
  try {
    return window.matchMedia?.('(display-mode: standalone)').matches === true || window.navigator.standalone === true
  } catch { return false }
}

// 저장된 값('1' 켬 · '0' 끔 · 없음)과 앱 여부로 정하기 (순수 계산)
// - 한 번이라도 직접 켜거나 끈 적이 있으면 그 값을 따름
// - 정한 적이 없으면: 앱에서는 켜진 채로 시작, 브라우저에서는 꺼진 채로 시작
export const decideAlways = (stored, app) => (stored === '1' ? true : stored === '0' ? false : app === true)

export const alwaysOn = () => {
  let stored = null
  try { stored = localStorage.getItem(ALWAYS_KEY) } catch { /* 저장소를 못 쓰면 정한 적 없는 것으로 */ }
  return decideAlways(stored, isApp())
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
