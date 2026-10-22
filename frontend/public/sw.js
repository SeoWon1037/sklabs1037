// ============================================
// 📲 SKLabs 서비스 워커 — 푸시 알림만
// - fetch(화면 파일 저장)는 일부러 안 함 → 배포 후 예전 화면이 남는 문제 없음
// - 서버가 보낸 { title, body, link, tag } → 알림 띄우기, 누르면 열린 창으로 이동 또는 새 창
// ============================================
self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()))

self.addEventListener('push', (e) => {
  let data
  try {
    data = e.data ? e.data.json() : {}
  } catch {
    data = { title: 'SKLabs', body: e.data ? e.data.text() : '' }
  }

  e.waitUntil(self.registration.showNotification(data.title || 'SKLabs', {
    body: data.body || '',
    icon: '/icon-192.png',
    badge: '/icon-192.png',
    tag: data.tag || 'sklabs',          // 같은 종류(하트 연타)는 알림 하나로 바뀜
    renotify: data.tag === 'HEART',
    data: { link: data.link || '/' },
  }))
})

self.addEventListener('notificationclick', (e) => {
  e.notification.close()
  const url = new URL(e.notification.data?.link || '/', self.location.origin).href

  e.waitUntil((async () => {
    const wins = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const win = wins.find((w) => new URL(w.url).origin === self.location.origin)
    if (win) {
      await win.focus()
      if ('navigate' in win) return win.navigate(url)
      return undefined
    }
    return self.clients.openWindow(url)
  })())
})
