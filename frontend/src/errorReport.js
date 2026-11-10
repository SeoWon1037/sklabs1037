// ============================================
// 🐞 화면 오류 보고: 브라우저에서 난 오류를 서버로 보냄 → 관리자 > 🐞 화면 오류에서 봄
// - 자바스크립트 오류(error) · 처리 안 된 실패(unhandledrejection) · 화면 그리기 실패(ErrorBoundary) · 화면 코드 못 받음(lazyPage)
// - 한 번 접속에 5개까지, 같은 메시지는 한 번만 (오류가 되풀이돼도 서버로 쏟아지지 않게)
// - 주소는 경로만 보냄 (검색어 같은 ?뒤 내용은 보내지 않음)
// - 보내다 실패해도 아무 일도 하지 않음 (오류 보고가 또 오류를 만들지 않게)
// - 백엔드: POST /api/track/error { kind, message, stack, path }
// ============================================
const MAX_PER_VISIT = 5
const sent = new Set()

export function reportError(kind, error, extraStack) {
  try {
    const message = String(error?.message ?? error ?? '').slice(0, 300)
    if (!message || sent.has(message) || sent.size >= MAX_PER_VISIT) return
    sent.add(message)

    const stack = [error?.stack, extraStack].filter(Boolean).join('\n').slice(0, 3000)
    const body = JSON.stringify({ kind, message, stack, path: window.location.pathname })
    fetch('/api/track/error', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true }).catch(() => {})
  } catch { /* 보고는 실패해도 됨 */ }
}

// 앱이 시작할 때 한 번: 어디서도 잡지 못한 오류를 받음
export function installErrorReport() {
  window.addEventListener('error', (e) => {
    if (e.error || e.message) reportError('ERROR', e.error ?? e.message)
  })
  window.addEventListener('unhandledrejection', (e) => {
    reportError('PROMISE', e.reason)
  })
}
