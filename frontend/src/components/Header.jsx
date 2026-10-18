// ============================================
// 상단바 (모든 화면 맨 위에 공통으로 표시)
// 왼쪽: 로고 + 메뉴 탭(뉴스 / 주식 종목 / AI 비서 / 관리자는 "관리자" 추가)
// 오른쪽: 테마 전환 + (로그아웃 상태) 로그인·회원가입 / (로그인 상태) 닉네임·로그아웃
// ============================================
import { useEffect, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useTheme } from '../theme/useTheme'
import './Header.css'

// 상단 메뉴 탭
const MENUS = [
  { to: '/', label: '뉴스', end: true },     // end: 정확히 "/"일 때만 선택 표시 (안 그러면 모든 주소에서 선택됨)
  { to: '/stock', label: '주식 종목' },
  { to: '/ai', label: 'AI 비서' },
]

// 관리자에게만 보이는 탭
const ADMIN_MENU = { to: '/admin', label: '관리자' }

function Header() {

  // 공통 테마에서 현재 테마와 전환 함수 꺼내기
  const { theme, toggleTheme } = useTheme()

  // 현재 주소 정보 (주소가 바뀔 때마다 로그인 상태를 다시 확인하려고)
  const location = useLocation()
  const navigate = useNavigate()

  // 로그인한 회원 정보 (null이면 로그아웃 상태) + 관리자 여부
  const [user, setUser] = useState(null)
  const [isAdmin, setIsAdmin] = useState(false)

  // 화면이 처음 뜰 때 + 페이지가 바뀔 때마다 "나 로그인돼 있어?" + "관리자야?" 확인
  useEffect(() => {

    fetch('/api/user/me')
      .then((res) => res.json())
      .then((data) => {
        setUser(data.loggedIn ? data.user : null)

        if (!data.loggedIn) {
          setIsAdmin(false)
          return
        }

        return fetch('/api/admin/check')
          .then((res) => res.json())
          .then((check) => setIsAdmin(Boolean(check.admin)))
      })
      .catch(() => {
        setUser(null)   // 서버 꺼져 있으면 그냥 로그아웃 상태로
        setIsAdmin(false)
      })

  }, [location.pathname])

  // 로그아웃 버튼
  const handleLogout = async () => {
    try {
      await fetch('/api/user/logout', { method: 'POST' })
    } finally {
      setUser(null)
      setIsAdmin(false)
      navigate('/')
    }
  }

  const menus = isAdmin ? [...MENUS, ADMIN_MENU] : MENUS

  return (
    <header className="header">
      <div className="header-inner">

        {/* 왼쪽: 로고 + 메뉴 탭 */}
        <div className="header-left">
          <Link to="/" className="header-logo">SKLabs</Link>

          <nav className="header-menu">
            {menus.map((m) => (
              <NavLink
                key={m.to}
                to={m.to}
                end={m.end}
                className={({ isActive }) => `header-menu-link ${isActive ? 'header-menu-active' : ''}`}
              >
                {m.label}
              </NavLink>
            ))}
          </nav>
        </div>

        {/* 오른쪽 버튼 영역 */}
        <div className="header-actions">
          <button
            type="button"
            className="theme-toggle"
            onClick={toggleTheme}
            aria-label={theme === 'dark' ? '라이트 모드로 전환' : '다크 모드로 전환'}
          >
            {theme === 'dark' ? '☀️' : '🌙'}
          </button>

          {user ? (
            // ===== 로그인 상태 =====
            <>
              <span className="header-user">{user.nickname}님</span>
              <button type="button" className="btn btn-ghost" onClick={handleLogout}>
                로그아웃
              </button>
            </>
          ) : (
            // ===== 로그아웃 상태 =====
            <>
              <Link to="/login" className="btn btn-ghost">로그인</Link>
              <Link to="/signup" className="btn btn-primary">회원가입</Link>
            </>
          )}
        </div>
      </div>
    </header>
  )
}

export default Header