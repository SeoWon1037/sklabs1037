// ============================================
// 📊 화면 열기 통계 (App에서 주소가 바뀔 때마다)
// - 브라우저마다 무작위 번호 하나(localStorage)를 만들어 같이 보냄 → 서버가 해시해서 "하루 방문자 수"만 셈
// - 주소의 첫 칸만 의미 있음 (서버가 /stock?symbol=… → /stock 으로 묶음), 관리자 화면은 안 보냄
// - 실패해도 화면에는 아무 영향 없음 (sendBeacon / keepalive)
// ============================================
const KEY = 'sk-vid'

function visitorId() {
  try {
    let v = localStorage.getItem(KEY)
    if (!v) {
      v = (crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`).replace(/[^A-Za-z0-9-]/g, '')
      localStorage.setItem(KEY, v)
    }
    return v
  } catch {
    return null
  }
}

let last = ''

export function trackPage(pathname) {
  if (!pathname || pathname.startsWith('/admin')) return
  const first = `/${pathname.split('/')[1] ?? ''}`
  if (first === last) return   // 같은 화면 안에서 주소만 바뀐 건 한 번만 (/stock?symbol=… 바꾸기)
  last = first
  const body = JSON.stringify({ p: pathname, v: visitorId() })
  try {
    if (navigator.sendBeacon?.('/api/track', new Blob([body], { type: 'application/json' }))) return
  } catch {
    // 아래 fetch로
  }
  fetch('/api/track', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true }).catch(() => {})
}
