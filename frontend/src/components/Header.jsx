// ============================================
// 상단바 (모든 화면 맨 위에 공통으로 표시)
// 왼쪽: 로고 / 오른쪽: 테마 전환 + (로그아웃 상태) 로그인·회원가입 / (로그인 상태) AI 비서·닉네임·로그아웃
// ============================================
import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useTheme } from '../theme/useTheme'
import './Header.css'

function Header() {

  // 공통 테마에서 현재 테마와 전환 함수 꺼내기
  const { theme, toggleTheme } = useTheme()

  // 현재 주소 정보 (주소가 바뀔 때마다 로그인 상태를 다시 확인하려고)
  const location = useLocation()
  const navigate = useNavigate()

  // 로그인한 회원 정보 (null이면 로그아웃 상태)
  const [user, setUser] = useState(null)

  // 화면이 처음 뜰 때 + 페이지가 바뀔 때마다 "나 로그인돼 있어?" 확인
  useEffect(() => {

    fetch('/api/user/me')
      .then((res) => res.json())
      .then((data) => setUser(data.loggedIn ? data.user : null))
      .catch(() => setUser(null))   // 서버 꺼져 있으면 그냥 로그아웃 상태로

  }, [location.pathname])

  // 로그아웃 버튼
  const handleLogout = async () => {
    try {
      await fetch('/api/user/logout', { method: 'POST' })
    } finally {
      setUser(null)
      navigate('/')
    }
  }

  return (
    <header className="header">
      <div className="header-inner">
        {/* 로고: 누르면 메인으로 */}
        <Link to="/" className="header-logo">SKLabs</Link>

        {/* 오른쪽 버튼 영역 */}
        <nav className="header-actions">
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
              <Link to="/ai" className="btn btn-primary">🤖 AI 비서</Link>
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
        </nav>
      </div>
    </header>
  )
}

export default Header