// ============================================
// 🎤 어느 화면에서든 챗봇이 무언가를 했을 때(하트 보내기 · 약속 잡기 등) 지금 보고 있는 화면에 알려 줌
// - VoiceCommand가 답을 받으면 aiActed() → 듣고 있던 화면(커플 · 내 약속)이 목록 · 숫자를 다시 불러옴
// ============================================
const EVENT = 'sklabs:ai-acted'

export const aiActed = (tools) => window.dispatchEvent(new CustomEvent(EVENT, { detail: tools ?? [] }))

// 🎤 상단 버튼 등 어디서든 "말로 시키기" 시트를 열기
const OPEN = 'sklabs:voice-open'
export const openVoice = () => window.dispatchEvent(new CustomEvent(OPEN))
export const onOpenVoice = (fn) => {
  window.addEventListener(OPEN, fn)
  return () => window.removeEventListener(OPEN, fn)
}

// 해제 함수를 돌려줌 (useEffect에서 그대로 return)
export const onAiActed = (fn) => {
  const handler = (e) => fn(e.detail)
  window.addEventListener(EVENT, handler)
  return () => window.removeEventListener(EVENT, handler)
}
