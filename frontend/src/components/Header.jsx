// ============================================
// 상단바 (모든 화면 맨 위에 공통으로 표시)
// 왼쪽: 로고 / 오른쪽: 테마 전환, 로그인, 회원가입
// ============================================
import { useTheme } from '../theme/useTheme'
import './Header.css'

function Header() {
  // 공통 테마에서 현재 테마와 전환 함수 꺼내기
  const { theme, toggleTheme } = useTheme()

  return (
    <header className="header">
      <div className="header-inner">
        {/* 로고: 누르면 메인으로 */}
        <a href="/" className="header-logo">SKLabs</a>

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

          {/* 아직 페이지가 없어서 모양만 (나중에 로그인/회원가입 화면과 연결) */}
          <button type="button" className="btn btn-ghost">로그인</button>
          <button type="button" className="btn btn-primary">회원가입</button>
        </nav>
      </div>
    </header>
  )
}

export default Header