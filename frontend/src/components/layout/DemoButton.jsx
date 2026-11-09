// ============================================
// 🧪 로그인 없이 체험하기 버튼 (메인 로그인 칸)
// - POST /api/user/demo → 체험 계정 세션 → 모의투자 화면으로 (새로 불러서 상단바도 체험 상태로)
// ============================================
import { useState } from 'react'

function DemoButton({ className = '' }) {

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const start = async () => {
    setBusy(true)
    setError('')
    const data = await fetch('/api/user/demo', { method: 'POST' })
      .then((r) => r.json())
      .catch(() => ({ success: false, message: '서버에 연결할 수 없습니다.' }))
    if (data.success) {
      window.location.assign('/mock')
      return
    }
    setBusy(false)
    setError(data.message)
  }

  return (
    <>
      <button type="button" className={className} onClick={start} disabled={busy}>
        {busy ? '들어가는 중...' : '🧪 로그인 없이 체험하기'}
      </button>
      {error && <p className="home-login-desc" role="alert">{error}</p>}
    </>
  )
}

export default DemoButton
