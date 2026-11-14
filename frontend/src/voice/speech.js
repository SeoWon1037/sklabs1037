// ============================================
// 🎤 듣기 · 🔊 말하기 (브라우저에 들어 있는 기능만 씀 — 음성 파일이 우리 서버로 가지 않음)
// - 듣기: Web Speech API(SpeechRecognition). 크롬 · 삼성 인터넷 · 엣지 · 사파리에서 됨
//   ★ 아이폰에서 "홈 화면에 추가"한 앱과 카카오톡 안 화면에서는 안 될 수 있음 → canListen()이 false면
//     화면들이 🎤 대신 글자 입력칸을 보여줌 (아이폰 키보드의 🎤 받아쓰기로 말할 수 있음)
// - 말하기: speechSynthesis (거의 모든 브라우저). 아이폰은 사용자가 화면을 누른 직후에만 소리가 남
// ============================================
const Recognition = typeof window !== 'undefined' ? (window.SpeechRecognition || window.webkitSpeechRecognition) : null

// 우리 언어 코드 → 브라우저 음성 코드
export const SPEECH_LANG = { ko: 'ko-KR', en: 'en-US', ja: 'ja-JP', zh: 'zh-CN', es: 'es-ES', vi: 'vi-VN' }

export const canListen = () => Boolean(Recognition)
export const canSpeak = () => typeof window !== 'undefined' && 'speechSynthesis' in window && typeof window.SpeechSynthesisUtterance === 'function'

const ERRORS = {
  'not-allowed': '마이크 권한을 허용해주세요.',
  'service-not-allowed': '이 브라우저에서는 음성 인식을 쓸 수 없어요.',
  'no-speech': '말소리가 들리지 않았어요.',
  'audio-capture': '마이크를 찾지 못했어요.',
  network: '인터넷 연결을 확인해주세요.',
}

/**
 * 한 번 듣기
 * @param {{ lang?: string, onInterim?: (text) => void, onFinal: (text) => void, onError?: (message) => void, onEnd?: () => void }} options
 * @returns {{ stop: () => void }} stop(): 그만 듣기 (그때까지 들은 말로 onFinal)
 */
export function listen({ lang = 'ko', onInterim, onFinal, onError, onEnd }) {
  if (!Recognition) {
    onError?.('이 브라우저에서는 음성 인식을 쓸 수 없어요.')
    onEnd?.()
    return { stop: () => {} }
  }

  const r = new Recognition()
  let heard = ''
  let failed = false
  r.lang = SPEECH_LANG[lang] ?? lang
  r.interimResults = true
  r.continuous = false
  r.maxAlternatives = 1
  r.onresult = (e) => {
    heard = Array.from(e.results).map((x) => x[0].transcript).join('').trim()
    onInterim?.(heard)
  }
  r.onerror = (e) => {
    if (e.error === 'aborted') return
    failed = true
    onError?.(ERRORS[e.error] ?? '음성 인식을 하지 못했어요.')
  }
  let ended = false
  r.onend = () => {
    if (ended) return          // 끝났다는 신호가 두 번 와도 한 번만 처리
    ended = true
    if (!failed && heard) onFinal(heard)
    else if (!failed && !heard) onError?.(ERRORS['no-speech'])
    onEnd?.()
  }

  try {
    r.start()
  } catch {
    onError?.('음성 인식을 시작하지 못했어요.')
    onEnd?.()
  }
  return { stop: () => { try { r.stop() } catch { /* 이미 끝남 */ } } }
}

// 소리 내어 읽을 수 있게 다듬기: 기호 · 링크 · 그림 글자를 빼고, 너무 길면 문장 끝에서 자름
export function speechText(raw, max = 600) {
  let s = String(raw ?? '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')          // [글](주소) → 글
    .replace(/https?:\/\/\S+/g, '')
    .replace(/[*_`]/g, '')                               // **굵게** 같은 꾸밈 기호
    .replace(/[#>|~]/g, ' ')
    .replace(/\p{Extended_Pictographic}/gu, '')        // 그림 글자
    .replace(/\u{FE0F}|\u{200D}/gu, '')
    .replace(/\s+/g, ' ')
    .trim()
  if (s.length > max) {
    const cut = s.slice(0, max)
    const end = Math.max(cut.lastIndexOf('. '), cut.lastIndexOf('요. '), cut.lastIndexOf('다. '), cut.lastIndexOf('? '))
    s = (end > max * 0.5 ? cut.slice(0, end + 1) : cut) + ' 이하 생략.'
  }
  return s
}

// 읽어주기 (앞에 읽던 것은 멈춤) → 읽기 시작했으면 true
export function speak(text, lang = 'ko', onEnd) {
  if (!canSpeak()) return false
  const s = speechText(text)
  if (!s) return false
  try {
    window.speechSynthesis.cancel()
    const u = new window.SpeechSynthesisUtterance(s)
    const code = SPEECH_LANG[lang] ?? lang
    u.lang = code
    const voice = window.speechSynthesis.getVoices().find((v) => v.lang === code) ?? window.speechSynthesis.getVoices().find((v) => v.lang?.startsWith(code.slice(0, 2)))
    if (voice) u.voice = voice
    u.rate = 1
    if (onEnd) {
      u.onend = onEnd
      u.onerror = onEnd
    }
    window.speechSynthesis.speak(u)
    return true
  } catch {
    return false
  }
}

export function stopSpeaking() {
  try { window.speechSynthesis?.cancel() } catch { /* 무시 */ }
}
