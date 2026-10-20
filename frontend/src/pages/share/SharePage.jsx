// ============================================
// 📸 공유받은 성적 카드 (주소: /s/:code, 로그인 없이)
// - 만든 순간의 스냅샷만 보여줌 + "나도 해보기"
// - 백엔드: GET /api/share/{code}
// ============================================
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import ShareCard from '../../components/me/ShareCard'
import './SharePage.css'

function SharePage() {

  const { code } = useParams()
  const [card, setCard] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let ignore = false
    fetch(`/api/share/${encodeURIComponent(code)}`)
      .then((res) => res.json())
      .then((json) => {
        if (ignore) return
        if (json.success) setCard(json.card)
        else setError(json.message || '카드를 찾을 수 없어요.')
      })
      .catch(() => { if (!ignore) setError('서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [code])

  return (
    <div className="sp-page">
      {error && <p className="sp-muted">{error}</p>}
      {!card && !error && <p className="sp-muted">불러오는 중...</p>}
      {card && <ShareCard card={card} />}

      <div className="sp-cta">
        <h1>나도 SKLabs에서 해볼까요?</h1>
        <p className="sp-muted">매일 오를까 내릴까 예측하고, 뉴스 퀴즈 풀고, 가상 1,000만 원으로 모의투자해요.</p>
        <div className="sp-links">
          <Link to="/predict" className="btn btn-primary">🎯 예측 게임 해보기</Link>
          <Link to="/quiz" className="btn btn-ghost">📰 뉴스 퀴즈</Link>
          <Link to="/persona" className="btn btn-ghost">🧭 투자 성향 테스트</Link>
        </div>
      </div>
    </div>
  )
}

export default SharePage
