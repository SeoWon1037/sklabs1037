// ============================================
// 아이디 찾기 / 비밀번호 재설정 화면 (주소: /find, /find?tab=password)
// - 아이디 찾기: 닉네임 → 가린 이메일 + 가입 방식
// - 비밀번호 재설정: ① 이메일로 코드 받기 → ② 코드 확인 → ③ 새 비밀번호
// ============================================
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import './UserFindPage.css'

const PROVIDER_NAMES = { LOCAL: '이메일 가입', NAVER: '네이버 로그인', KAKAO: '카카오 로그인' }

// POST 요청 공통
const postJson = async (url, body) => {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return res.json()
}

function UserFindPage() {

  const [searchParams] = useSearchParams()
  const [tab, setTab] = useState(searchParams.get('tab') === 'password' ? 'password' : 'id')

  return (
    <main className="find-page">
      <div className="find-card">
        <h1 className="find-title">계정 찾기</h1>

        <div className="find-tabs">
          <button type="button" className={`find-tab ${tab === 'id' ? 'find-tab-active' : ''}`} onClick={() => setTab('id')}>
            아이디 찾기
          </button>
          <button type="button" className={`find-tab ${tab === 'password' ? 'find-tab-active' : ''}`} onClick={() => setTab('password')}>
            비밀번호 재설정
          </button>
        </div>

        {tab === 'id' ? <FindIdForm /> : <ResetPasswordForm />}

        <div className="find-links">
          <Link to="/login">로그인</Link>
          <span>·</span>
          <Link to="/signup">회원가입</Link>
        </div>
      </div>
    </main>
  )
}

// ============================================
// 아이디 찾기
// ============================================
function FindIdForm() {

  const [nickname, setNickname] = useState('')
  const [result, setResult] = useState(null)     // null: 아직 안 찾음 / []: 없음 / [...]: 결과
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (nickname.trim() === '' || loading) return

    setLoading(true)
    setMessage('')

    try {
      const res = await fetch(`/api/user/find/id?nickname=${encodeURIComponent(nickname.trim())}`)
      const data = await res.json()
      if (data.success) setResult(data.users)
      else setMessage(data.message)
    } catch {
      setMessage('서버에 연결할 수 없습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <form className="find-form" onSubmit={handleSubmit}>
      <label className="find-label" htmlFor="find-nickname">가입할 때 사용한 닉네임</label>
      <input
        id="find-nickname"
        className="find-input"
        value={nickname}
        onChange={(e) => setNickname(e.target.value)}
        placeholder="닉네임"
        maxLength={50}
      />
      <button type="submit" className="btn btn-primary find-submit" disabled={loading || nickname.trim() === ''}>
        {loading ? '찾는 중...' : '아이디 찾기'}
      </button>

      {message && <p className="find-message find-error">{message}</p>}

      {result && result.length === 0 && (
        <p className="find-message">해당 닉네임으로 가입된 계정이 없습니다.</p>
      )}

      {result && result.length > 0 && (
        <ul className="find-results">
          {result.map((u, i) => (
            <li key={i} className="find-result">
              <strong>{u.maskedEmail}</strong>
              <span>{PROVIDER_NAMES[u.provider] ?? u.provider} · {u.insDay?.slice(0, 10)} 가입</span>
            </li>
          ))}
        </ul>
      )}
    </form>
  )
}

// ============================================
// 비밀번호 재설정 (3단계)
// ============================================
function ResetPasswordForm() {

  const [step, setStep] = useState(1)          // 1: 이메일, 2: 코드, 3: 새 비밀번호, 4: 완료
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [passwordCheck, setPasswordCheck] = useState('')
  const [message, setMessage] = useState({ text: '', error: false })
  const [loading, setLoading] = useState(false)

  // 요청 공통: 성공하면 다음 단계로
  const run = async (url, body, nextStep) => {
    setLoading(true)
    try {
      const data = await postJson(url, body)
      setMessage({ text: data.message, error: !data.success })
      if (data.success && nextStep) setStep(nextStep)
    } catch {
      setMessage({ text: '서버에 연결할 수 없습니다.', error: true })
    } finally {
      setLoading(false)
    }
  }

  const sendCode = (e) => {
    e.preventDefault()
    run('/api/user/find/reset/send-code', { email }, 2)
  }

  const verifyCode = (e) => {
    e.preventDefault()
    run('/api/user/find/reset/verify', { email, code }, 3)
  }

  const savePassword = (e) => {
    e.preventDefault()
    if (password !== passwordCheck) {
      setMessage({ text: '비밀번호가 서로 다릅니다.', error: true })
      return
    }
    run('/api/user/find/reset/password', { password }, 4)
  }

  return (
    <div className="find-form">

      {/* 단계 표시 */}
      <ol className="find-steps">
        {['이메일', '인증코드', '새 비밀번호'].map((label, i) => (
          <li key={label} className={`find-step ${step >= i + 1 ? 'find-step-done' : ''}`}>{i + 1}. {label}</li>
        ))}
      </ol>

      {step === 1 && (
        <form onSubmit={sendCode} className="find-form">
          <label className="find-label" htmlFor="reset-email">가입한 이메일</label>
          <input id="reset-email" type="email" className="find-input" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="example@naver.com" />
          <button type="submit" className="btn btn-primary find-submit" disabled={loading || email.trim() === ''}>
            {loading ? '보내는 중...' : '인증코드 받기'}
          </button>
        </form>
      )}

      {step === 2 && (
        <form onSubmit={verifyCode} className="find-form">
          <label className="find-label" htmlFor="reset-code">메일로 받은 6자리 코드 (5분 안에 입력)</label>
          <input id="reset-code" className="find-input" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} placeholder="123456" maxLength={6} inputMode="numeric" />
          <button type="submit" className="btn btn-primary find-submit" disabled={loading || code.length !== 6}>
            {loading ? '확인 중...' : '확인'}
          </button>
          <button type="button" className="btn btn-ghost find-submit" onClick={() => run('/api/user/find/reset/send-code', { email })} disabled={loading}>
            코드 다시 받기
          </button>
        </form>
      )}

      {step === 3 && (
        <form onSubmit={savePassword} className="find-form">
          <label className="find-label" htmlFor="reset-pw">새 비밀번호 (영문 + 숫자, 8~20자)</label>
          <input id="reset-pw" type="password" className="find-input" value={password} onChange={(e) => setPassword(e.target.value)} maxLength={20} autoComplete="new-password" />
          <label className="find-label" htmlFor="reset-pw2">새 비밀번호 확인</label>
          <input id="reset-pw2" type="password" className="find-input" value={passwordCheck} onChange={(e) => setPasswordCheck(e.target.value)} maxLength={20} autoComplete="new-password" />
          <button type="submit" className="btn btn-primary find-submit" disabled={loading || password === ''}>
            {loading ? '저장 중...' : '비밀번호 변경'}
          </button>
        </form>
      )}

      {step === 4 && (
        <Link to="/login" className="btn btn-primary find-submit">로그인하러 가기</Link>
      )}

      {message.text && <p className={`find-message ${message.error ? 'find-error' : ''}`}>{message.text}</p>}
    </div>
  )
}

export default UserFindPage