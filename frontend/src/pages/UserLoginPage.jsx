// ============================================
// 로그인 화면 (주소: /login)
// - 백엔드 API
//   POST /api/user/login   로그인 { email, password }
// - 로그인 성공하면 서버가 세션을 만들고, 브라우저에 번호표 쿠키(JSESSIONID)를 줌
//   → 이후 요청마다 브라우저가 쿠키를 자동으로 같이 보냄 (따로 코드 필요 없음)
// ============================================
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import './UserSignUpPage.css'   // 회원가입과 같은 카드 디자인(signup-xxx)을 그대로 사용
import './UserLoginPage.css'    // 로그인 화면에만 필요한 추가 스타일

function UserLoginPage() {

  // 페이지 이동 함수 (로그인 성공 후 메인으로)
  const navigate = useNavigate()

  // 입력값
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  // 로그인 요청 중인지 (버튼 연타 방지) / 에러 문구
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  // ============================================
  // 로그인 버튼
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
        {errorMessage && <p className="signup-msg error signup-error-box">{errorMessage}</p>}

        {/* 로그인 버튼 */}
        <button type="submit" className="btn btn-primary signup-submit" disabled={submitting}>
          {submitting ? '로그인 중...' : '로그인'}
        </button>

        {/* 회원가입으로 이동 */}
        <p className="login-signup-link">
          아직 회원이 아니신가요? <Link to="/signup">회원가입</Link>
        </p>

      </form>
    </main>
  )
}

export default UserLoginPage