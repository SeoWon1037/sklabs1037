// ============================================
// 모의투자 수익률 랭킹 TOP 5 (메인 화면 2×2 아래 가로 한 줄)
// - PC: 5칸 가로로 / 휴대폰: 한 줄씩
// - 로그인했고 TOP 5 밖이면 제목 옆에 내 순위
// - 1분마다 자동 새로고침
// - 백엔드 API: GET /api/mock/ranking?size=5 (누구나)
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './MockRankingBox.css'

const SIZE = 5
const REFRESH_MS = 60 * 1000

const won = (value) => Math.round(Number(value ?? 0)).toLocaleString('ko-KR')
const signedRate = (value) => {
  const n = Number(value ?? 0)
  return `${n > 0 ? '+' : ''}${n.toFixed(2)}%`
}
const tone = (value) => (Number(value) > 0 ? 'up' : Number(value) < 0 ? 'down' : '')
const MEDALS = { 1: '🥇', 2: '🥈', 3: '🥉' }

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
      <div className="home-rank-head">
        <div className="home-rank-title">
          <h2 className="home-box-title">🏆 모의투자 수익률 랭킹</h2>
          <span className="home-box-sub">
            {data ? `${data.participants}명 참여 · 가상 1,000만 원` : '불러오는 중...'}
          </span>
          {meOutside && (
            <span className="home-rank-mine">
              내 순위 <strong>{me.rank}위</strong> <b className={tone(me.returnRate)}>{signedRate(me.returnRate)}</b>
            </span>
          )}
        </div>
        <Link to="/mock" className="home-box-link home-rank-go">💰 모의투자 하러 가기 →</Link>
      </div>

      {data && list.length === 0 && (
        <p className="home-rank-empty">
          아직 참여한 사람이 없어요. <Link to="/mock">가상 1,000만 원으로 첫 주문 해보기 →</Link>
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
              <small className="home-rank-asset">{won(r.totalAsset)}원</small>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

export default MockRankingBox
