// ============================================
// 모의투자 배너 (메인 화면 왼쪽 맨 위, 네이버 메인 광고 띠 자리)
// - 왼쪽 그림 칸 / 가운데 수익률 TOP 3 / 오른쪽 "모의투자 하러 가기" 버튼
// - 로그인했고 거래한 적이 있으면 내 순위·수익률도 같이
// - 1분마다 자동 새로고침 (다른 탭을 보고 있을 땐 건너뜀)
// - 백엔드 API: GET /api/mock/ranking?size=3 (누구나)
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './MockBanner.css'

const REFRESH_MS = 60 * 1000
const MEDALS = { 1: '🥇', 2: '🥈', 3: '🥉' }

const signedRate = (value) => {
  const n = Number(value ?? 0)
  return `${n > 0 ? '+' : ''}${n.toFixed(2)}%`
}
const tone = (value) => (Number(value) > 0 ? 'up' : Number(value) < 0 ? 'down' : '')

function MockBanner() {

  const [data, setData] = useState(null)   // null = 불러오는 중

  useEffect(() => {
    const load = () =>
      fetch('/api/mock/ranking?size=3')
        .then((res) => res.json())
        .then((json) => json.success && setData(json))
        .catch(() => setData((prev) => prev ?? { ranking: [], participants: 0 }))

    load()
    const timer = setInterval(() => document.visibilityState === 'visible' && load(), REFRESH_MS)
    return () => clearInterval(timer)
  }, [])

  const list = data?.ranking ?? []
  const me = data?.me

  return (
    <section className="home-banner">

      {/* 왼쪽: 그림 칸 */}
      <Link to="/mock" className="home-banner-art" aria-label="모의투자로 이동">
        <span className="home-banner-emoji">💰</span>
        <span className="home-banner-art-text">가상 3,000만 원</span>
      </Link>

      {/* 가운데: 제목 + TOP 3 */}
      <div className="home-banner-body">
        <div className="home-banner-title">
          <strong>🏆 모의투자 수익률 TOP 3</strong>
          {data && <span>{data.participants}명 참여</span>}
          {me && (
            <span className="home-banner-mine">
              내 순위 <strong>{me.rank}위</strong> <b className={tone(me.returnRate)}>{signedRate(me.returnRate)}</b>
            </span>
          )}
        </div>

        {list.length > 0 ? (
          <ol className="home-banner-podium">
            {list.map((r, i) => (
              <li key={`${r.rank}-${r.nickname}-${i}`} className={r.me ? 'home-banner-me' : ''}>
                <span className="home-banner-medal">{MEDALS[r.rank] ?? r.rank}</span>
                <span className="home-banner-name">{r.nickname}</span>
                <b className={tone(r.returnRate)}>{signedRate(r.returnRate)}</b>
              </li>
            ))}
          </ol>
        ) : (
          <p className="home-banner-desc">
            {data ? '아직 참여한 사람이 없어요. 실제 시세로 사고팔고 1등에 도전해보세요!' : '불러오는 중...'}
          </p>
        )}
      </div>

      {/* 오른쪽: 버튼 */}
      <Link to="/mock" className="home-banner-cta">모의투자 하러 가기 ›</Link>
    </section>
  )
}

export default MockBanner
