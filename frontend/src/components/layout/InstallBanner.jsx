// ============================================
// 📲 휴대폰 앱 설치 안내 한 줄 (화면 아래에 잠깐)
// - 휴대폰 · 두 번째 방문부터 · 아직 설치 안 함 · 닫은 적 없음일 때만
// - [방법 보기] → /app, ✕ → 다시 안 뜸 (이 브라우저에 기억)
// - /app · 소셜 로그인 팝업 · 관리자 화면에서는 안 뜸
// ============================================
import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { isMobile, isStandalone } from '../../pwa'
import './InstallBanner.css'

const CLOSED_KEY = 'sklabs.install.banner'
const VISITS_KEY = 'sklabs.visits'
const COUNTED_KEY = 'sklabs.visit.counted'

const read = (store, key) => {
  try { return store.getItem(key) } catch { return null }
}
const write = (store, key, value) => {
  try { store.setItem(key, value) } catch { /* 저장이 막힌 브라우저 */ }
}

// 탭(세션)마다 한 번만 방문 수 +1
const visits = () => {
  let n = Number(read(localStorage, VISITS_KEY)) || 0
  if (!read(sessionStorage, COUNTED_KEY)) {
    n += 1
    write(localStorage, VISITS_KEY, String(n))
    write(sessionStorage, COUNTED_KEY, '1')
  }
  return n
}

function InstallBanner() {

  const { pathname } = useLocation()
  // 처음 한 번만 판단 (방문 수는 탭마다 한 번만 올라가서 두 번 불려도 같음)
  const [show, setShow] = useState(() => {
    const n = visits()
    return isMobile() && !isStandalone() && n >= 2 && read(localStorage, CLOSED_KEY) !== 'closed'
  })

  if (!show || pathname === '/app' || pathname.startsWith('/oauth') || pathname.startsWith('/admin')) return null

  const close = () => {
    write(localStorage, CLOSED_KEY, 'closed')
    setShow(false)
  }

  return (
    <div className="ib" role="region" aria-label="앱 설치 안내">
      <img src="/icon-192.png" alt="" />
      <p><b>SKLabs 앱으로 설치</b><span>아이콘으로 바로 열고 알림도 받아요</span></p>
      <Link to="/app" className="ib-go" onClick={close}>방법 보기</Link>
      <button type="button" className="ib-x" onClick={close} aria-label="닫기">✕</button>
    </div>
  )
}

export default InstallBanner
