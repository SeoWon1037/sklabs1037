// ============================================
// 회원가입 화면 (주소: /signup)
// - 백엔드 API
//   POST /api/user/signup/send-code    인증번호 발송 (중복 체크 포함)  { email }
//   POST /api/user/signup/verify-code  인증번호 확인                  { email, code }
//   POST /api/user/signup              회원가입                       { email, password, nickname }
// ============================================
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import confetti from 'canvas-confetti'   // ★ 색종이(폭죽) 효과 라이브러리
import './UserSignUpPage.css'

// 인증번호 유효 시간 (초) = 5분 (백엔드 EXPIRE_DAY와 맞춤)
const CODE_LIMIT_SEC = 300

// 재발송 대기 시간 (초) = 1분 (백엔드 1분 제한과 맞춤)
const RESEND_WAIT_SEC = 60

// ★ 가입 성공 시 색종이 터뜨리기
const fireConfetti = () => {

  // disableForReducedMotion: 운영체제에서 "애니메이션 줄이기"를 켠 사람에겐 안 보여줌 (어지러움 방지)
  const options = { disableForReducedMotion: true }

  // 1. 가운데서 크게 한 번 팡!
  confetti({ ...options, particleCount: 120, spread: 90, origin: { y: 0.6 } })

  // 2. 1.5초 동안 양쪽 아래에서 계속 뿜기
  const end = Date.now() + 1500

  const frame = () => {
    confetti({ ...options, particleCount: 4, angle: 60,  spread: 60, origin: { x: 0, y: 0.7 } })   // 왼쪽 → 오른쪽 위로
    confetti({ ...options, particleCount: 4, angle: 120, spread: 60, origin: { x: 1, y: 0.7 } })   // 오른쪽 → 왼쪽 위로

    // 1.5초 안 지났으면 다음 화면 그릴 때 또 실행
    if (Date.now() < end) requestAnimationFrame(frame)
  }
  frame()
}

function UserSignUpPage() {

  // 페이지 이동 함수
  const navigate = useNavigate()

  // ============================================
  // 입력값
  // ============================================
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')                       // 인증번호 6자리
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [nickname, setNickname] = useState('')

  // ============================================
  // 이메일 인증 상태
  // ============================================
  const [codeSent, setCodeSent] = useState(false)    // 인증번호를 보냈는지 (true면 인증번호 칸 표시)
  const [verified, setVerified] = useState(false)    // 인증 완료했는지 (true여야 가입 가능)
  const [remainSec, setRemainSec] = useState(0)      // 남은 시간 (초)
  const [sending, setSending] = useState(false)      // 발송 요청 중인지 (버튼 연타 방지)

  // 이메일 아래 안내 문구 (emailOk: true = 초록, false = 빨강)
  const [emailMessage, setEmailMessage] = useState('')
  const [emailOk, setEmailOk] = useState(false)

  // 인증번호 아래 안내 문구
  const [codeMessage, setCodeMessage] = useState('')
  const [codeOk, setCodeOk] = useState(false)

  // 가입 요청 중인지 / 화면 맨 아래 에러 문구
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  // ★ 가입 완료 여부 (true면 입력폼 대신 환영 화면 표시)
  const [done, setDone] = useState(false)

  // ============================================
  // 타이머: 1초마다 남은 시간 1씩 줄이기
  // ============================================
  useEffect(() => {

    // 발송 전이거나, 인증 완료했거나, 시간이 다 됐으면 멈춤
    if (!codeSent || verified || remainSec <= 0) return

    const timer = setInterval(() => {
      setRemainSec((sec) => sec - 1)
    }, 1000)

    // 화면이 바뀌거나 다시 실행될 때 이전 타이머 정리 (안 하면 타이머가 여러 개 쌓임)
    return () => clearInterval(timer)

  }, [codeSent, verified, remainSec])

  // 남은 시간 → "04:32" 모양으로
  const formatTime = (sec) => {
    const m = String(Math.floor(sec / 60)).padStart(2, '0')
    const s = String(sec % 60).padStart(2, '0')
    return `${m}:${s}`
  }

  // 재발송 가능 여부 (보낸 지 1분 지났거나, 시간이 만료됐으면 가능)
  const canResend = remainSec <= CODE_LIMIT_SEC - RESEND_WAIT_SEC

  // ============================================
  // 이메일 입력이 바뀔 때 → 인증 처음부터 다시
  // ============================================
  const handleEmailChange = (e) => {
    setEmail(e.target.value)
    setCodeSent(false)
    setVerified(false)
    setCode('')
    setRemainSec(0)
    setEmailMessage('')
    setCodeMessage('')
  }

  // ============================================
  // 인증번호 받기 / 재발송 버튼
  // ============================================
  const handleSendCode = async () => {

    // 1. 이메일 형식 체크 (글자@글자.글자)
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailPattern.test(email)) {
      setEmailOk(false)
      setEmailMessage('이메일 형식이 올바르지 않습니다.')
      return
    }

    // 2. 백엔드에 발송 요청 (중복 체크도 백엔드에서 같이 함)
    setSending(true)

    try {
      const res = await fetch('/api/user/signup/send-code', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      })
      const data = await res.json()

      setEmailOk(data.success)
      setEmailMessage(data.message)

      if (data.success) {
        setCodeSent(true)               // 인증번호 칸 보여주기
        setRemainSec(CODE_LIMIT_SEC)    // 타이머 5분 시작
        setCode('')
        setCodeMessage('')
      }

    } catch {
      setEmailOk(false)
      setEmailMessage('서버에 연결할 수 없습니다.')
    } finally {
      setSending(false)
    }
  }

  // ============================================
  // 인증번호 확인 버튼
  // ============================================
  const handleVerifyCode = async () => {

    if (code.length !== 6) {
      setCodeOk(false)
      setCodeMessage('인증번호 6자리를 입력해주세요.')
      return
    }

    try {
      const res = await fetch('/api/user/signup/verify-code', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, code }),
      })
      const data = await res.json()

      setCodeOk(data.success)
      setCodeMessage(data.message)

      if (data.success) {
        setVerified(true)    // 타이머 멈추고, 이메일 칸 잠금
      }

    } catch {
      setCodeOk(false)
      setCodeMessage('서버에 연결할 수 없습니다.')
    }
  }

  // ============================================
  // 가입하기 버튼
  // ============================================
  const handleSubmit = async (e) => {

    // form의 기본 동작(페이지 새로고침) 막기
    e.preventDefault()
    setErrorMessage('')

    // 1. 화면에서 먼저 검사
    if (!verified) {
      setErrorMessage('이메일 인증을 완료해주세요.')
      return
    }
    if (password.length < 8) {
      setErrorMessage('비밀번호는 8자 이상 입력해주세요.')
      return
    }
    if (password !== passwordConfirm) {
      setErrorMessage('비밀번호가 일치하지 않습니다.')
      return
    }
    if (nickname.trim() === '') {
      setErrorMessage('닉네임을 입력해주세요.')
      return
    }

    // 2. 백엔드에 회원가입 요청 (백엔드에서도 인증 여부를 다시 검사함)
    setSubmitting(true)

    try {
      const res = await fetch('/api/user/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        // JSON 이름 = 백엔드 UserSignUpVO 필드 이름 (자동으로 VO에 채워짐)
        body: JSON.stringify({ email, password, nickname }),
      })
      const data = await res.json()

      if (data.success) {
        setDone(true)      // ★ alert 대신 환영 화면으로 전환
        fireConfetti()     // ★ 색종이 팡!
      } else {
        setErrorMessage(data.message)
      }

    } catch {
      setErrorMessage('서버에 연결할 수 없습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  // ============================================
  // ★ 가입 완료 화면 (done이 true일 때)
  // ============================================
  if (done) {
    return (
      <main className="signup-page">
        <div className="signup-card signup-done">
          <div className="signup-done-icon">🎉</div>
          <h1 className="signup-title">{nickname}님, 환영합니다!</h1>
          <p className="signup-done-email">{email}</p>
          <p className="signup-done-text">회원가입이 완료되었어요.</p>
          <button
            type="button"
            className="btn btn-primary signup-submit"
            onClick={() => navigate('/')}
          >
            메인으로 가기
          </button>
        </div>
      </main>
    )
  }

  // ============================================
  // 입력폼 화면
  // ============================================
  return (
    <main className="signup-page">
      <form className="signup-card" onSubmit={handleSubmit}>

        <h1 className="signup-title">회원가입</h1>

        {/* ===== 이메일 + 인증번호 받기 ===== */}
        <label className="signup-label" htmlFor="email">이메일</label>
        <div className="signup-row">
          <input
            id="email"
            type="email"
            // autoComplete="email": 브라우저에 "이메일 칸"이라고 알려줌
            // (저장된 다른 사이트 아이디가 자동으로 채워지는 걸 방지)
            autoComplete="email"
            className="signup-input"
            placeholder="example@email.com"
            value={email}
            onChange={handleEmailChange}
            readOnly={verified}   // 인증 완료 후에는 이메일 변경 불가
          />
          <button
            type="button"
            className="btn btn-ghost signup-check-btn"
            onClick={handleSendCode}
            // 발송 중이거나, 인증 완료했거나, 재발송 대기 1분 안 지났으면 비활성화
            disabled={sending || verified || (codeSent && !canResend)}
          >
            {sending ? '발송 중...' : codeSent ? '재발송' : '인증번호 받기'}
          </button>
        </div>
        {emailMessage && (
          <p className={emailOk ? 'signup-msg ok' : 'signup-msg error'}>
            {emailOk ? '✓ ' : ''}{emailMessage}
          </p>
        )}

        {/* ===== 인증번호 입력 (발송 후에만 보임) ===== */}
        {codeSent && (
          <>
            <label className="signup-label" htmlFor="code">인증번호</label>
            <div className="signup-row">
              <div className="signup-code-wrap">
                <input
                  id="code"
                  type="text"
                  // 휴대폰에서 숫자 키패드가 뜨게 + 인증번호 자동 입력 지원
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  className="signup-input"
                  placeholder="6자리 숫자"
                  value={code}
                  // 숫자만 입력되게 (숫자가 아닌 글자는 지움)
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                  readOnly={verified}
                />
                {/* 남은 시간 (인증 완료 전까지만 표시) */}
                {!verified && (
                  <span className="signup-timer">{formatTime(remainSec)}</span>
                )}
              </div>
              <button
                type="button"
                className="btn btn-ghost signup-check-btn"
                onClick={handleVerifyCode}
                // 인증 완료했거나 시간 만료면 비활성화
                disabled={verified || remainSec <= 0}
              >
                {verified ? '인증 완료' : '확인'}
              </button>
            </div>

            {/* 시간 만료 안내 */}
            {!verified && remainSec <= 0 && (
              <p className="signup-msg error">인증 시간이 만료되었습니다. 재발송을 눌러주세요.</p>
            )}

            {/* 확인 결과 안내 */}
            {codeMessage && (
              <p className={codeOk ? 'signup-msg ok' : 'signup-msg error'}>
                {codeOk ? '✓ ' : ''}{codeMessage}
              </p>
            )}
          </>
        )}

        {/* ===== 비밀번호 ===== */}
        <label className="signup-label" htmlFor="password">비밀번호</label>
        <input
          id="password"
          type="password"
          // autoComplete="new-password": "새 비밀번호를 만드는 칸"이라고 알려줌
          // (저장된 비밀번호 자동 채우기 방지)
          autoComplete="new-password"
          className="signup-input"
          placeholder="8자 이상"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        {/* ===== 비밀번호 확인 ===== */}
        <label className="signup-label" htmlFor="passwordConfirm">비밀번호 확인</label>
        <input
          id="passwordConfirm"
          type="password"
          // 비밀번호 칸과 같은 이유 (새 비밀번호 확인용)
          autoComplete="new-password"
          className="signup-input"
          placeholder="비밀번호를 한 번 더 입력"
          value={passwordConfirm}
          onChange={(e) => setPasswordConfirm(e.target.value)}
        />
        {passwordConfirm && password !== passwordConfirm && (
          <p className="signup-msg error">비밀번호가 일치하지 않습니다.</p>
        )}

        {/* ===== 닉네임 ===== */}
        <label className="signup-label" htmlFor="nickname">닉네임</label>
        <input
          id="nickname"
          type="text"
          className="signup-input"
          placeholder="화면에 표시될 이름"
          maxLength={50}   // DB 컬럼 nickname varchar(50)에 맞춤
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
        />

        {/* 전체 에러 문구 */}
        {errorMessage && <p className="signup-msg error signup-error-box">{errorMessage}</p>}

        {/* 가입 버튼 */}
        <button type="submit" className="btn btn-primary signup-submit" disabled={submitting}>
          {submitting ? '가입 중...' : '가입하기'}
        </button>

      </form>
    </main>
  )
}

export default UserSignUpPage