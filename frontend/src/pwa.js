// ============================================
// 📲 앱 설치(PWA) · 푸시 알림 도우미
// - registerPwa(): 서비스 워커 등록 + "앱 설치" 이벤트 붙잡아 두기 (main.jsx에서 한 번)
// - 설치: 크롬·엣지·안드로이드는 버튼 한 번 / 아이폰·아이패드는 "공유 → 홈 화면에 추가" 안내
// - 푸시: 이 브라우저 구독 → POST /api/push/subscribe (서버 공개키는 GET /api/push/key)
//   아이폰은 홈 화면에 추가한 앱에서만 푸시가 돼요 (iOS 16.4+)
// ============================================

let installEvent = null
const listeners = new Set()
const emit = () => listeners.forEach((fn) => fn())

export const onPwaChange = (fn) => {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

export const isStandalone = () =>
  window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true

export const isIos = () =>
  /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)

export function registerPwa() {
  if (!('serviceWorker' in navigator)) return
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault()          // 브라우저 기본 배너 대신 메뉴의 "앱 설치"로
    installEvent = e
    emit()
  })
  window.addEventListener('appinstalled', () => {
    installEvent = null
    emit()
  })
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {})
  })
}

// 'installed' | 'prompt'(버튼으로 설치) | 'ios'(안내) | null(이 브라우저는 메뉴에서 직접)
export const installMode = () => {
  if (isStandalone()) return 'installed'
  if (installEvent) return 'prompt'
  if (isIos()) return 'ios'
  return null
}

export async function promptInstall() {
  if (!installEvent) return false
  installEvent.prompt()
  const { outcome } = await installEvent.userChoice
  installEvent = null
  emit()
  return outcome === 'accepted'
}

// ===== 푸시 =====
const pushSupported = () => 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window

// 'unsupported' | 'ios-install'(홈 화면에 추가해야 함) | 'denied' | 'off' | 'on'
export async function pushState() {
  if (!pushSupported()) return isIos() && !isStandalone() ? 'ios-install' : 'unsupported'
  if (Notification.permission === 'denied') return 'denied'
  const reg = await navigator.serviceWorker.getRegistration()
  const sub = await reg?.pushManager.getSubscription()
  return sub ? 'on' : 'off'
}

const toKey = (b64) => {
  const s = (b64 + '='.repeat((4 - (b64.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')
  return Uint8Array.from(atob(s), (c) => c.charCodeAt(0))
}

const post = (url, body) =>
  fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body ?? {}) })
    .then((r) => r.json())
    .catch(() => ({ success: false, message: '서버에 연결하지 못했어요.' }))

export async function enablePush() {
  if (!pushSupported()) throw new Error('이 브라우저는 푸시 알림을 지원하지 않아요.')
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') throw new Error('알림을 허용해야 받을 수 있어요.')

  const key = await fetch('/api/push/key').then((r) => r.json())
  if (!key.success) throw new Error(key.message || '서버 키를 받지 못했어요.')

  const reg = await navigator.serviceWorker.register('/sw.js')
  await navigator.serviceWorker.ready
  let sub = await reg.pushManager.getSubscription()
  if (!sub) sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: toKey(key.publicKey) })

  const json = await post('/api/push/subscribe', sub.toJSON())
  if (!json.success) {
    await sub.unsubscribe().catch(() => {})
    throw new Error(json.message)
  }
}

export async function disablePush() {
  const reg = await navigator.serviceWorker.getRegistration()
  const sub = await reg?.pushManager.getSubscription()
  if (!sub) return
  await post('/api/push/unsubscribe', { endpoint: sub.endpoint })
  await sub.unsubscribe().catch(() => {})
}

// 로그인한 사람이 이미 켠 브라우저면 서버 기록을 다시 맞춤 (다른 계정으로 로그인했을 때 그 계정으로)
export async function syncPush() {
  if (!pushSupported() || Notification.permission !== 'granted') return
  const reg = await navigator.serviceWorker.getRegistration()
  const sub = await reg?.pushManager.getSubscription()
  if (sub) await post('/api/push/subscribe', sub.toJSON())
}

export const sendTestPush = () => post('/api/push/test')

// ===== 기기 · 브라우저 구분 (설치 안내용) =====
export const isAndroid = () => /Android/i.test(navigator.userAgent)
export const isMobile = () => isIos() || isAndroid() || /Mobi/i.test(navigator.userAgent)

// 앱 안 브라우저 (여기서는 설치가 안 됨 → 바깥 브라우저로)
export const inAppBrowser = () => {
  const ua = navigator.userAgent
  if (/KAKAOTALK/i.test(ua)) return '카카오톡'
  if (/NAVER\(inapp/i.test(ua)) return '네이버 앱'
  if (/Instagram/i.test(ua)) return '인스타그램'
  if (/FBAN|FBAV/i.test(ua)) return '페이스북'
  if (/Line\//i.test(ua)) return '라인'
  if (/everytimeApp|DaumApps|Whale\/.*Mobile.*inapp/i.test(ua)) return '앱'
  return null
}

// 바깥 브라우저로 열기 주소 (카카오톡은 공식 주소, 안드로이드는 크롬 intent, 아이폰 다른 앱은 null → 링크 복사)
export const openOutsideUrl = (url) => {
  if (/KAKAOTALK/i.test(navigator.userAgent)) return `kakaotalk://web/openExternal?url=${encodeURIComponent(url)}`
  if (isAndroid()) {
    const u = new URL(url)
    return `intent://${u.host}${u.pathname}${u.search}#Intent;scheme=https;package=com.android.chrome;end`
  }
  return null
}
