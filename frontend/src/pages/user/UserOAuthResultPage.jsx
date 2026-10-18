// ============================================
// 소셜 로그인 결과 화면 (주소: /oauth/result?status=...)
// - 백엔드가 네이버 로그인 처리 후 이 주소로 보내줌
// - 팝업 안에서 열렸으면 → 원래 창(로그인 화면)에 결과를 알려주고 스스로 닫힘
// - 팝업이 아니면(팝업 차단 등) → 직접 다음 화면으로 이동
// ============================================
import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

function UserOAuthResultPage() {

  const navigate = useNavigate()

  // 주소의 ?status=success 같은 값 꺼내기
  const [searchParams] = useSearchParams()
  const status = searchParams.get('status') || 'fail'

  useEffect(() => {

    // window.opener: 이 창을 열어준 원래 창 (팝업일 때만 있음)
    const opener = window.opener

    if (opener && !opener.closed) {

      // 1. 팝업인 경우 → 원래 창에 결과 전달 후 닫기
      //    두 번째 값(origin): 같은 사이트(우리 화면)에게만 전달 (다른 사이트가 엿보지 못하게)
      opener.postMessage({ type: 'OAUTH_RESULT', status }, window.location.origin)
      window.close()

    } else {

      // 2. 팝업이 아닌 경우 → 성공이면 메인, 실패면 로그인 화면(에러 표시)
      navigate(status === 'success' ? '/' : `/login?error=${status}`, { replace: true })

    }

  }, [status, navigate])

  return (
    <main className="main">
      <p>로그인 처리 중...</p>
    </main>
  )
}

export default UserOAuthResultPage