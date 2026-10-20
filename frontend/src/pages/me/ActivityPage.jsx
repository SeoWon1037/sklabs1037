// ============================================
// 🏅 내 활동 · 업적 (주소: /activity)
// - 모의투자 / 예측 / 퀴즈 / 토론 / 탐험 / 출석 기록을 한 화면에
// - 배지 22개: 조건을 채우면 이 화면을 열 때 서버가 확인해서 지급 (새로 받은 배지는 축하 + 🔔 알림)
// - 백엔드: GET /api/me/activity
// ============================================
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import './ActivityPage.css'

const GROUPS = ['출석', '모의투자', '예측', '퀴즈', '커뮤니티', '탐험']

const pct = (v) => (v == null ? '-' : `${v > 0 ? '+' : ''}${Number(v).toFixed(2)}%`)

// 오늘 기준 최근 5주(35일) 달력 칸 (KST 날짜 문자열)
const lastDays = (n) => {
  const kst = new Date(Date.now() + 9 * 3600 * 1000)
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(kst)
    d.setUTCDate(kst.getUTCDate() - (n - 1 - i))
    return d.toISOString().slice(0, 10)
  })
}

const dateLabel = (ms) => {
  if (!ms) return ''
  const d = new Date(ms)
  return `${d.getFullYear()}.${d.getMonth() + 1}.${d.getDate()}`
}

function Stat({ label, value, sub }) {
  return (
    <div className="act-stat">
      <span>{label}</span>
      <b>{value}</b>
      {sub && <small>{sub}</small>}
    </div>
  )
}

function ActivityPage() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [needLogin, setNeedLogin] = useState(false)
  const [showNew, setShowNew] = useState(true)

  useEffect(() => {
    let ignore = false
    fetch('/api/me/activity')
      .then((res) => {
        if (res.status === 401) {
          if (!ignore) setNeedLogin(true)
          return null
        }
        return res.json()
      })
      .then((json) => {
        if (ignore || !json) return
        if (json.success) setData(json)
        else setError(json.message || '불러오지 못했어요.')
      })
      .catch(() => { if (!ignore) setError('서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [])

  const calendar = useMemo(() => {
    const set = new Set(data?.attend?.recent || [])
    return lastDays(35).map((d) => ({ d, on: set.has(d) }))
  }, [data])

  if (needLogin) {
    return (
      <div className="act-page">
        <h1>🏅 내 활동 · 업적</h1>
        <div className="act-login">
          <p>로그인하면 출석·모의투자·예측·퀴즈 기록과 배지를 모아볼 수 있어요.</p>
          <Link to="/login" className="btn btn-primary">로그인하기</Link>
        </div>
      </div>
    )
  }

  if (error) return <div className="act-page"><p className="act-muted">{error}</p></div>
  if (!data) return <div className="act-page"><p className="act-muted">불러오는 중...</p></div>

  const { mock, predict, quiz, board, explore, attend, badges } = data
  const fresh = badges.filter((b) => data.newBadges.includes(b.code))
  const hitRate = predict.graded > 0 ? Math.round((predict.hits / predict.graded) * 100) : null

  return (
    <div className="act-page">
      <header className="act-hero">
        <p className="act-eyebrow">MY ACTIVITY</p>
        <h1>🏅 내 활동 · 업적</h1>
        <div className="act-summary">
          <div><b>{data.earnedCount}</b><span>/ {badges.length} 배지</span></div>
          <div><b>{attend.streak}</b><span>일 연속 출석</span></div>
          <div><b>{attend.days}</b><span>일 출석</span></div>
        </div>
        <div className="act-progress" aria-label={`배지 ${data.earnedCount}개 획득`}>
          <span style={{ width: `${(data.earnedCount / badges.length) * 100}%` }} />
        </div>
      </header>

      {showNew && fresh.length > 0 && (
        <div className="act-new" role="status">
          <p className="act-new-title">🎉 새 배지를 받았어요!</p>
          <div className="act-new-list">
            {fresh.map((b) => (
              <div key={b.code} className="act-new-badge">
                <span>{b.icon}</span>
                <b>{b.name}</b>
              </div>
            ))}
          </div>
          <button type="button" className="act-new-close" onClick={() => setShowNew(false)}>확인</button>
        </div>
      )}

      <section className="act-cards">
        <article className="act-card">
          <h2><Link to="/mock">💰 모의투자</Link></h2>
          <div className="act-stats">
            <Stat label="거래" value={`${mock.tradeCount}회`} />
            <Stat label="내 수익률" value={pct(mock.returnRate)} sub={mock.bestAiReturn != null ? `AI 1등 ${pct(mock.bestAiReturn)}` : null} />
          </div>
        </article>
        <article className="act-card">
          <h2><Link to="/predict">🎯 예측 게임</Link></h2>
          <div className="act-stats">
            <Stat label="참여" value={`${predict.votes}개`} />
            <Stat label="적중률" value={hitRate == null ? '-' : `${hitRate}%`} sub={`${predict.graded}개 중 ${predict.hits}개`} />
            <Stat label="최고 연속" value={`${predict.bestStreak}연속`} />
          </div>
        </article>
        <article className="act-card">
          <h2><Link to="/quiz">📰 뉴스 퀴즈</Link></h2>
          <div className="act-stats">
            <Stat label="참여" value={`${quiz.plays}일`} />
            <Stat label="만점" value={`${quiz.perfect}번`} />
            <Stat label="최고 연속" value={`${quiz.bestStreak}일`} />
          </div>
        </article>
        <article className="act-card">
          <h2><Link to="/stock">💬 토론방</Link></h2>
          <div className="act-stats">
            <Stat label="쓴 글" value={`${board.posts}개`} />
            <Stat label="받은 좋아요" value={`${board.likesReceived}개`} />
            <Stat label="댓글" value={`${board.comments}개`} />
          </div>
        </article>
        <article className="act-card">
          <h2>🧭 탐험</h2>
          <div className="act-stats">
            <Stat label="투자 성향" value={explore.personaType ? '완료' : <Link to="/persona">하러 가기</Link>} />
            <Stat label="AI 비서 질문" value={`${explore.aiChats}번`} />
            <Stat label="성분 분석" value={`${explore.beautyLogs}번`} />
            <Stat label="관심종목" value={`${explore.watchCount}개`} />
          </div>
        </article>
        <article className="act-card">
          <h2>📅 출석</h2>
          <div className="act-cal" aria-label="최근 5주 출석">
            {calendar.map((c) => (
              <span key={c.d} className={c.on ? 'act-cal-on' : ''} title={`${c.d}${c.on ? ' 출석' : ''}`} />
            ))}
          </div>
          <p className="act-muted">최근 5주 · 최고 {attend.bestStreak}일 연속</p>
        </article>
      </section>

      <section className="act-badges">
        <h2>배지</h2>
        {GROUPS.map((g) => (
          <div key={g} className="act-group">
            <h3>{g}</h3>
            <div className="act-badge-grid">
              {badges.filter((b) => b.group === g).map((b) => (
                <div key={b.code} className={`act-badge ${b.earnedAt ? 'act-earned' : 'act-locked'}`}>
                  <span className="act-badge-icon" aria-hidden="true">{b.icon}</span>
                  <b>{b.name}</b>
                  <small>{b.desc}</small>
                  {b.earnedAt && <em>{dateLabel(b.earnedAt)}</em>}
                </div>
              ))}
            </div>
          </div>
        ))}
      </section>
    </div>
  )
}

export default ActivityPage
