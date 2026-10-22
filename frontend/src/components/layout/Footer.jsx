// ============================================
// 하단 정보 (푸터) — 모든 화면 맨 아래 공통
// - 만든이 + 관리자 문의(메일) + GitHub 코드 링크
// - 데이터 출처 + 투자 유의 문구
// - AI 비서 화면(채팅이 화면을 꽉 채움)과 소셜 로그인 팝업에서는 숨김
// ============================================
import { Link, useLocation } from 'react-router-dom'
import './Footer.css'

// 관리자 문의 메일 · GitHub 주소는 src/site.js 한 곳에서
import { CONTACT_EMAIL, GITHUB_URL } from '../../site'

// 데이터 출처
const SOURCES = [
  { label: '시세', value: '토스증권' },
  { label: '뉴스', value: 'Google News' },
  { label: '날씨', value: 'Open-Meteo' },
  { label: 'AI', value: 'Anthropic Claude' },
]

// 푸터를 숨길 주소
const HIDE_PATHS = ['/messages', '/ai', '/oauth/result']

function Footer() {

  const location = useLocation()

  if (HIDE_PATHS.includes(location.pathname)) return null

  const mailLink = `mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent('[SKLabs] 문의')}`

  return (
    <footer className="footer">
      <div className="footer-inner">

        {/* ===== 위: 로고 + 링크 ===== */}
        <div className="footer-top">
          <span className="footer-logo">SKLabs</span>

          <nav className="footer-links">
            <Link to="/about" className="footer-link">👤 만든 사람</Link>
            <span className="footer-bar">|</span>
            <Link to="/ideas" className="footer-link">💡 기능 제안</Link>
            <span className="footer-bar">|</span>
            <Link to="/app" className="footer-link">📲 앱 설치</Link>
            <span className="footer-bar">|</span>
            <a href={mailLink} className="footer-link">✉️ 관리자 문의</a>
            <span className="footer-bar">|</span>
            <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="footer-link">GitHub</a>
          </nav>
        </div>

        {/* ===== 데이터 출처 ===== */}
        <p className="footer-sources">
          {SOURCES.map((s, i) => (
            <span key={s.label}>
              {i > 0 && <span className="footer-dot">·</span>}
              {s.label} <strong>{s.value}</strong>
            </span>
          ))}
        </p>

        {/* ===== 투자 유의 문구 ===== */}
        <p className="footer-notice">
          SKLabs에서 제공하는 시세·뉴스·AI 답변은 참고용이며, 투자 판단의 책임은 본인에게 있습니다.
          AI 답변은 사실과 다를 수 있습니다.
        </p>

        {/* ===== 만든이 ===== */}
        <p className="footer-copy">
          © {new Date().getFullYear()} SKLabs · Made by 서원
        </p>

      </div>
    </footer>
  )
}

export default Footer