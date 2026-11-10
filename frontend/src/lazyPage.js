// ============================================
// 화면을 "들어갈 때" 받아오기 (코드 나누기)
// - 전에는 모든 화면의 코드를 첫 접속 때 한 파일(약 930KB)로 받았음 → 이제 첫 화면에 필요한 것만 받고, 나머지는 그 화면에 들어갈 때
// - 배포하면 조각 파일 이름이 바뀜 → 예전 화면을 켜 둔 채 다른 화면으로 가면 조각을 못 받을 수 있음
//   → 그럴 때는 한 번만 새로고침해서 새 버전으로 (계속 실패하면 새로고침을 되풀이하지 않고 오류로)
// ============================================
import { lazy } from 'react'

const KEY = 'sklabs.chunk.reload'

export const lazyPage = (load) => lazy(() =>
  load()
    .then((mod) => {
      try { sessionStorage.removeItem(KEY) } catch { /* 무시 */ }
      return mod
    })
    .catch((e) => {
      let reloaded = true
      try {
        reloaded = Boolean(sessionStorage.getItem(KEY))
        if (!reloaded) sessionStorage.setItem(KEY, '1')
      } catch { /* 저장을 못 쓰면 새로고침하지 않음 */ }
      if (!reloaded) {
        window.location.reload()
        return new Promise(() => {})   // 새로고침될 때까지 기다림
      }
      throw e
    }),
)

// 자주 가는 화면은 첫 화면이 다 뜬 뒤 한가할 때 미리 받아둠 (눌렀을 때 바로 열리게)
export const preloadWhenIdle = (loaders) => {
  const run = () => loaders.forEach((load) => load().catch(() => {}))
  if ('requestIdleCallback' in window) window.requestIdleCallback(run, { timeout: 5000 })
  else setTimeout(run, 2500)
}
