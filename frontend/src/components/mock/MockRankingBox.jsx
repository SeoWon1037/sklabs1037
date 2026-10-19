// ============================================
// 모의투자 수익률 TOP 3 (메인 화면 오른쪽 위, 로그인 박스 바로 아래)
// - 1·2·3등만 한눈에 + 로그인했고 3등 밖이면 맨 아래에 내 순위
// - 1분마다 자동 새로고침 (다른 탭을 보고 있을 땐 건너뜀)
// - 백엔드 API: GET /api/mock/ranking?size=3 (누구나)
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './MockRankingBox.css'

const SIZE = 3
const REFRESH_MS = 60 * 1000
const MEDALS = { 1: '🥇', 2: '🥈', 3: '🥉' }

const signedRate = (value) => {
  const n = Number(value ?? 0)
  return `${n > 0 ? '+' : ''}${n.toFixed(2)}%`
}
const tone = (value) => (Number(value) > 0 ? 'up' : Number(value) < 0 ? 'down' : '')

function MockRankingBox() {

  const [data, setData] = useState(null)   // null = 불러오는 중

  useEffect(() => {
    const load = () =>
      fetch(`/api/mock/ranking?size=${SIZE}`)
        .then((res) => res.json())
        .then((json) => json.success && setData(json))
        .catch(() => setData((prev) => prev ?? { ranking: [], participants: 0 }))

    load()
    const timer = setInterval(() => document.visibilityState === 'visible' && load(), REFRESH_MS)
    return () => clearInterval(timer)
  }, [])

  const list = data?.ranking ?? []
  const me = data?.me
  const meOutside = me && !list.some((r) => r.me)

  return (
    <section className="home-box home-rank">
      <div className="home-box-head">
        <h2 className="home-box-title">🏆 모의투자 TOP 3</h2>
        <Link to="/mock" className="home-rank-go">
          {data ? `${data.participants}명 참여 · ` : ''}전체 순위 →
        </Link>
      </div>

      {!data && <p className="home-rank-empty">불러오는 중...</p>}

      {data && list.length === 0 && (
        <p className="home-rank-empty">
          아직 참여한 사람이 없어요.<br />
          <Link to="/mock">가상 1,000만 원으로 1등 해보기 →</Link>
        </p>
      )}

      {list.length > 0 && (
        <ol className="home-rank-list">
          {list.map((r, i) => (
            <li key={`${r.rank}-${r.nickname}-${i}`} className={`home-rank-item ${r.me ? 'home-rank-me' : ''}`}>
              <span className="home-rank-no">{MEDALS[r.rank] ?? r.rank}</span>
              <span className="home-rank-name">
                {r.nickname}
                {r.me && <em>나</em>}
              </span>
              <strong className={`home-rank-rate ${tone(r.returnRate)}`}>{signedRate(r.returnRate)}</strong>
            </li>
          ))}
          {meOutside && (
            <li className="home-rank-item home-rank-me home-rank-mine">
              <span className="home-rank-no">{me.rank}</span>
              <span className="home-rank-name">{me.nickname}<em>나</em></span>
              <strong className={`home-rank-rate ${tone(me.returnRate)}`}>{signedRate(me.returnRate)}</strong>
            </li>
          )}
        </ol>
      )}
    </section>
  )
}

export default MockRankingBox
