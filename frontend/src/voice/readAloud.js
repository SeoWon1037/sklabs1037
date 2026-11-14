// ============================================
// 📣 알림 읽어 주기 (사이트를 열어 둔 동안 새 알림이 오면 소리로)
// - 켜기/끄기: 내 약속 · 음성 브리핑 화면(/plans). 이 브라우저에만 기억 (localStorage)
// - 브리핑 · 약속 알림은 내용(문장)만, 나머지는 제목 + 내용을 읽음
// - 브라우저 규칙: 그 탭에서 화면을 한 번도 누르지 않았으면 소리가 안 날 수 있음 (특히 아이폰)
// ============================================
import { speak } from './speech'

export const READ_ALOUD_KEY = 'sklabs.notify.speak'

export const readAloudOn = () => {
  try { return localStorage.getItem(READ_ALOUD_KEY) === '1' } catch { return false }
}

// 알림 한 개 → 읽을 문장
export const notificationSpeech = (n) => {
  if (!n) return ''
  const body = String(n.body ?? '').trim()
  if ((n.kind === 'BRIEF' || n.kind === 'PLAN') && body) return body
  const title = String(n.title ?? '').replace(/·\s*눌러서 듣기/g, '').trim()
  return [title, body].filter(Boolean).join('. ')
}

// 안 읽은 알림 중 가장 새것을 읽음 (이미 읽어 준 것은 다시 읽지 않음)
let lastSpokenId = null
export const speakNewest = (items) => {
  const newest = (items ?? []).find((n) => !n.read)
  if (!newest || newest.id === lastSpokenId) return false
  lastSpokenId = newest.id
  const text = notificationSpeech(newest)
  return text ? speak(text) : false
}
