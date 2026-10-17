// ============================================
// 상단바 (모든 화면 맨 위에 공통으로 표시)
// 왼쪽: 로고 / 오른쪽: 테마 전환, 로그인, 회원가입
// ============================================
import { Link } from 'react-router-dom'
import { useTheme } from '../theme/useTheme'
import './Header.css'

function Header() {
  // 공통 테마에서 현재 테마와 전환 함수 꺼내기
  const { theme, toggleTheme } = useTheme()

  return (
    <header className="header">
      <div className="header-inner">
        {/*
          로고: 누르면 메인으로
          Link: <a>와 비슷하지만 페이지를 새로고침하지 않고 화면만 바꿔줌 (더 빠름)
        */}
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

          {/* 로그인: 아직 화면이 없어서 모양만 */}
          <button type="button" className="btn btn-ghost">로그인</button>

          {/* 회원가입: 누르면 /signup 화면으로 이동 */}
          <Link to="/signup" className="btn btn-primary">회원가입</Link>
        </nav>
      </div>
    </header>
  )
}

export default Header