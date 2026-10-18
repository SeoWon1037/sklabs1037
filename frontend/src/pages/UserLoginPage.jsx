// ============================================
// 로그인 화면 (주소: /login)
// - 백엔드 API
//   POST /api/user/login          이메일 로그인 { email, password }
//   GET  /api/oauth/naver/login   네이버 로그인 (팝업으로 열기)
//   GET  /api/oauth/kakao/login   카카오 로그인 (팝업으로 열기)
// - 로그인 성공하면 서버가 세션을 만들고, 브라우저에 번호표 쿠키(JSESSIONID)를 줌
// ============================================
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import './UserSignUpPage.css'   // 회원가입과 같은 카드 디자인(signup-xxx)을 그대로 사용
import './UserLoginPage.css'    // 로그인 화면에만 필요한 추가 스타일

// ★ 소셜 로그인 실패 이유 → 화면에 보여줄 문구 (네이버/카카오 공통)
const OAUTH_ERROR_MESSAGES = {
  cancel: '소셜 로그인을 취소했습니다.',
  invalid: '잘못된 요청입니다. 다시 시도해주세요.',
  email_exists: '이미 다른 방법으로 가입된 이메일입니다. 처음 가입한 방법으로 로그인해주세요.',
  email_missing: '이메일 제공에 동의해야 가입할 수 있습니다. (인증된 이메일만 가능)',
  fail: '소셜 로그인에 실패했습니다. 잠시 후 다시 시도해주세요.',
}

// 팝업 창 크기
const POPUP_WIDTH = 500
const POPUP_HEIGHT = 650

function UserLoginPage() {

  // 페이지 이동 함수 (로그인 성공 후 메인으로)
  const navigate = useNavigate()

  // 주소의 ?error=xxx (팝업 차단돼서 페이지 이동 방식으로 돌아왔을 때)
  const [searchParams] = useSearchParams()
  const urlError = searchParams.get('error')

  // 입력값
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  // 로그인 요청 중인지 (버튼 연타 방지) / 에러 문구
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  // 화면에 보여줄 에러: 직접 생긴 에러가 우선, 없으면 주소의 소셜 로그인 에러
  const shownError = errorMessage || (urlError ? (OAUTH_ERROR_MESSAGES[urlError] || OAUTH_ERROR_MESSAGES.fail) : '')

  // ============================================
  // 팝업에서 보내는 결과 메시지 받기
  // ============================================
  useEffect(() => {

    const handleMessage = (event) => {

      // 우리 사이트에서 온 메시지만 받기 (다른 사이트가 보낸 가짜 메시지 무시)
      if (event.origin !== window.location.origin) return
      if (event.data?.type !== 'OAUTH_RESULT') return

      if (event.data.status === 'success') {
        navigate('/')   // 메인으로 → 상단바가 로그인 상태 다시 확인
      } else {
        setErrorMessage(OAUTH_ERROR_MESSAGES[event.data.status] || OAUTH_ERROR_MESSAGES.fail)
      }
    }

    window.addEventListener('message', handleMessage)

    // 화면을 떠날 때 듣기 중단 (안 하면 계속 쌓임)
    return () => window.removeEventListener('message', handleMessage)

  }, [navigate])

  // ============================================
  // ★ 소셜 로그인 버튼 → 팝업 열기 (provider: 'naver' 또는 'kakao')
  // ============================================
  const handleSocialLogin = (provider) => {

    setErrorMessage('')

    // 백엔드 주소: /api/oauth/naver/login 또는 /api/oauth/kakao/login
    const loginUrl = `/api/oauth/${provider}/login`

    // 화면 가운데에 팝업 띄우기
    const left = window.screenX + (window.outerWidth - POPUP_WIDTH) / 2
    const top = window.screenY + (window.outerHeight - POPUP_HEIGHT) / 2

    const popup = window.open(
      loginUrl,
      `${provider}Login`,
      `width=${POPUP_WIDTH},height=${POPUP_HEIGHT},left=${left},top=${top}`
    )

    // 팝업이 차단됐으면 → 현재 창에서 이동하는 방식으로
    if (!popup) {
      window.location.href = loginUrl
    }
  }

  // ============================================
  // 이메일 로그인 버튼
  // ============================================
  const handleSubmit = async (e) => {

    // form의 기본 동작(페이지 새로고침) 막기
    e.preventDefault()
    setErrorMessage('')

    // 1. 빈 값 체크
    if (email.trim() === '' || password === '') {
      setErrorMessage('이메일과 비밀번호를 입력해주세요.')
      return
    }

    // 2. 백엔드에 로그인 요청
    setSubmitting(true)

    try {
      const res = await fetch('/api/user/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      })
      const data = await res.json()

      if (data.success) {
        // 성공 → 메인으로 이동 (상단바가 주소 바뀐 걸 보고 로그인 상태를 다시 확인함)
        navigate('/')
      } else {
        // 실패 (401: 이메일 또는 비밀번호 틀림)
        setErrorMessage(data.message)
        setPassword('')   // 비밀번호 칸만 비워서 다시 입력하게
      }

    } catch {
      setErrorMessage('서버에 연결할 수 없습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  // ============================================
  // 화면
  // ============================================
  return (
    <main className="signup-page">
      <form className="signup-card" onSubmit={handleSubmit}>

        <h1 className="signup-title">로그인</h1>

        {/* 이메일 */}
        <label className="signup-label" htmlFor="email">이메일</label>
        <input
          id="email"
          type="email"
          // autoComplete="username": 로그인 아이디 칸이라고 알려줌 (저장된 계정 자동완성 허용)
          autoComplete="username"
          className="signup-input"
          placeholder="example@email.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />

        {/* 비밀번호 */}
        <label className="signup-label" htmlFor="password">비밀번호</label>
        <input
          id="password"
          type="password"
          // autoComplete="current-password": 기존 비밀번호 입력 칸 (저장된 비밀번호 자동완성 허용)
          autoComplete="current-password"
          className="signup-input"
          placeholder="비밀번호"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        {/* 에러 문구 */}
        {shownError && <p className="signup-msg error signup-error-box">{shownError}</p>}

        {/* 이메일 로그인 버튼 */}
        <button type="submit" className="btn btn-primary signup-submit" disabled={submitting}>
          {submitting ? '로그인 중...' : '로그인'}
        </button>

        {/* ===== 구분선 ===== */}
        <div className="login-divider"><span>또는</span></div>

        {/* 네이버 로그인 버튼 (type="button": 눌러도 form 제출 안 되게) */}
        <button type="button" className="login-social-btn login-naver-btn" onClick={() => handleSocialLogin('naver')}>
          <span className="login-naver-logo">N</span>
          네이버로 로그인
        </button>

        {/* ★ 카카오 로그인 버튼 */}
        <button type="button" className="login-social-btn login-kakao-btn" onClick={() => handleSocialLogin('kakao')}>
          {/* 카카오 말풍선 모양 아이콘 */}
          <svg className="login-kakao-logo" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 3C6.48 3 2 6.48 2 10.8c0 2.78 1.86 5.22 4.66 6.6l-.95 3.48c-.08.3.26.54.52.37l4.15-2.74c.53.06 1.07.09 1.62.09 5.52 0 10-3.48 10-7.8S17.52 3 12 3z" />
          </svg>
          카카오로 로그인
        </button>

        {/* 회원가입 / 계정 찾기 */}
        <div className="login-footer">
          <p className="login-signup-text">
            아직 회원이 아니신가요?
            <Link to="/signup" className="login-signup-link">회원가입</Link>
          </p>
          <Link to="/find" className="login-find-link">아이디 · 비밀번호 찾기</Link>
        </div>

      </form>
    </main>
  )
}

export default UserLoginPage