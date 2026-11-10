// ============================================
// 🤝 친구 초대: 링크(/?ref=코드)로 들어온 사람의 코드를 기억했다가, 로그인한 뒤 서버로 보냄
// - 가입 화면 · 소셜 로그인을 거쳐도 되도록 이 브라우저에 잠깐 저장 (7일)
// - 서버가 받거나(성공) 받을 수 없다고 답하면(이미 받음 · 3일 지남 등) 지움, 연결이 안 되면 다음에 다시
// - 백엔드: POST /api/invite/accept { code }
// ============================================
const KEY = 'sklabs.invite.ref'
const KEEP_MS = 7 * 24 * 3600 * 1000

// 주소에 ?ref= 가 있으면 기억
export function rememberRef() {
  try {
    const code = new URLSearchParams(window.location.search).get('ref')
    if (code && /^[0-9A-Za-z]{2,12}$/.test(code)) localStorage.setItem(KEY, JSON.stringify({ code, at: Date.now() }))
  } catch { /* 저장을 못 쓰는 브라우저면 초대만 안 됨 */ }
}

let sending = false

// 로그인돼 있을 때 호출: 기억해 둔 코드가 있으면 서버로
export function acceptRef() {
  if (sending) return
  let saved
  try {
    saved = JSON.parse(localStorage.getItem(KEY) ?? 'null')
    if (saved && Date.now() - saved.at > KEEP_MS) {
      localStorage.removeItem(KEY)
      saved = null
    }
  } catch {
    saved = null
  }
  if (!saved?.code) return

  sending = true
  fetch('/api/invite/accept', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code: saved.code }) })
    .then((r) => {
      // 받았거나(200) 받을 수 없음(400·404) → 더 보낼 필요 없음. 로그인 풀림(401) · 서버 문제(5xx)는 다음에 다시
      if (r.ok || r.status === 400 || r.status === 404) {
        try { localStorage.removeItem(KEY) } catch { /* 무시 */ }
      }
    })
    .catch(() => { /* 다음에 다시 */ })
    .finally(() => { sending = false })
}
