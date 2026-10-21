// ============================================
// 🤖 AI vs 사람 투자 리그 (주소: /league)
// - 성격이 다른 AI 투자자 3명이 평일 09:10에 직접 종목을 골라 모의투자 (사람과 같은 규칙·같은 랭킹)
// - 위: 사람 vs AI 점수판 / 가운데: AI별 성적·보유 종목·수익률 추이 / 아래: 매매 일지 (왜 샀는지)
// - 백엔드: GET /api/league (1분 캐시)
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './LeaguePage.css'
import { LEAGUE_SEEN_KEY } from '../../components/me/todayKeys'

const AVATAR = { NEWS: '📰', MOMENTUM: '🚀', STEADY: '🛡️' }

const signed = (v) => {
  if (v == null) return '-'
  const n = Number(v)
  return `${n > 0 ? '+' : ''}${n.toFixed(2)}%`
}

const tone = (v) => (v == null || Number(v) === 0 ? '' : Number(v) > 0 ? 'lg-up' : 'lg-down')

const won = (v) => (v == null ? '-' : Math.round(Number(v)).toLocaleString('ko-KR'))

// 날짜 "2026-10-20" → "10월 20일 (화)"
const dayLabel = (text) => {
  const d = new Date(`${text}T00:00:00+09:00`)
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${'일월화수목금토'[d.getDay()]})`
}

// 수익률 추이 작은 선 그래프 (0% 기준선)
function Sparkline({ points }) {

  const values = (points || []).map((p) => Number(p.returnRate) || 0)
  // 기록이 2일 미만: 빈 상자 안에 안내 글을 넣으면 그래프와 글이 겹친 것처럼 보여서(QA 봇 지적) → 점선 기준선 + 아래 한 줄
  if (values.length < 2) {
    return (
      <div className="lg-spark-wait">
        <svg className="lg-spark" viewBox="0 0 240 56" preserveAspectRatio="none" aria-hidden="true">
          <line x1="0" x2="240" y1="28" y2="28" className="lg-spark-zero" />
        </svg>
        <p>첫 매매 후 하루가 지나면 수익률 선이 그려져요</p>
      </div>
    )
  }

  const w = 240
  const h = 56
  const min = Math.min(0, ...values)
  const max = Math.max(0, ...values)
  const span = max - min || 1
  const x = (i) => (i / (values.length - 1)) * w
  const y = (v) => h - ((v - min) / span) * (h - 6) - 3
  const path = values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ')
  const last = values[values.length - 1]

  return (
    <svg className="lg-spark" viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" role="img" aria-label={`수익률 추이, 지금 ${signed(last)}`}>
      <line x1="0" x2={w} y1={y(0)} y2={y(0)} className="lg-spark-zero" />
      <path d={path} className={`lg-spark-line ${last >= 0 ? 'lg-spark-up' : 'lg-spark-down'}`} />
    </svg>
  )
}

function LeaguePage() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let ignore = false
    fetch('/api/league')
      .then((res) => res.json())
      .then((json) => {
        if (ignore) return
        if (json.success) {
          setData(json)
          // 📅 오늘의 SKLabs "AI 매매 일지 보기" 체크 (이 브라우저에 오늘 날짜 저장)
          try { localStorage.setItem(LEAGUE_SEEN_KEY, new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10)) } catch { /* 저장 못 해도 괜찮음 */ }
        } else setError(json.message || '불러오지 못했어요.')
      })
      .catch(() => { if (!ignore) setError('서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [])

  // 날짜별로 일지 묶기
  const days = []
  for (const log of data?.logs || []) {
    const last = days[days.length - 1]
    if (last && last.date === log.tradeDate) last.logs.push(log)
    else days.push({ date: log.tradeDate, logs: [log] })
  }

  const aiBest = data?.traders?.length ? [...data.traders].sort((a, b) => Number(b.returnRate) - Number(a.returnRate))[0] : null
  const humanAvg = data?.human?.avgReturn
  const aiAvg = data?.aiAvgReturn
  // AI가 아직 한 번도 매매 안 했으면(랭킹에 없음) 우세 표시 안 함 → "첫 매매 전"
  const aiStarted = data?.traders?.some((t) => t.rank != null)
  const leader = !aiStarted || humanAvg == null || aiAvg == null ? null : Number(aiAvg) > Number(humanAvg) ? 'AI' : Number(aiAvg) < Number(humanAvg) ? '사람' : null

  return (
    <main className="lg-page">

      <header className="lg-hero">
        <p className="lg-eyebrow">모의투자 리그</p>
        <h1>🤖 AI vs 사람</h1>
        <p className="lg-desc">
          성격이 다른 AI 투자자 3명이 평일 오전 9시 10분, 시세와 뉴스를 읽고 직접 종목을 골라요.
          사람과 똑같이 3,000만 원으로 시작하고, 같은 시장가 규칙·같은 랭킹에서 경쟁해요.
        </p>
      </header>

      {error && <p className="lg-empty">{error}</p>}
      {!data && !error && <p className="lg-empty">불러오는 중...</p>}

      {data && (
        <>
          {/* ===== 점수판 ===== */}
          <section className="lg-score" aria-label="사람 vs AI 점수판">
            <div className="lg-side">
              <span className="lg-side-label">🙋 사람 평균</span>
              <strong className={`lg-side-value ${tone(humanAvg)}`}>{signed(humanAvg)}</strong>
              <span className="lg-side-sub">
                {data.human.participants}명 참가
                {data.human.best && <> · 1등 {data.human.best.nickname} {signed(data.human.best.returnRate)}</>}
              </span>
            </div>
            <div className="lg-vs">
              <span>VS</span>
              {leader && <small>{leader} 우세</small>}
              {data && !aiStarted && <small className="lg-vs-wait">AI 첫 매매 전</small>}
            </div>
            <div className="lg-side lg-side-ai">
              <span className="lg-side-label">🤖 AI 평균</span>
              <strong className={`lg-side-value ${tone(aiAvg)}`}>{signed(aiAvg)}</strong>
              <span className="lg-side-sub">
                {data.traders.length}명 참가
                {aiBest && <> · 1등 {aiBest.nickname} {signed(aiBest.returnRate)}</>}
              </span>
            </div>
          </section>

          {/* ===== AI 투자자들 ===== */}
          <section className="lg-traders">
            {data.traders.map((t) => (
              <article key={t.code} className="lg-card">
                <div className="lg-card-head">
                  <span className="lg-avatar" aria-hidden="true">{AVATAR[t.code] || '🤖'}</span>
                  <div className="lg-card-name">
                    <strong>{t.nickname}</strong>
                    <span>{t.rank ? `전체 ${t.rank}위 / ${data.participants}명` : '첫 매매 전'}</span>
                  </div>
                  <strong className={`lg-card-rate ${tone(t.returnRate)}`}>{signed(t.returnRate)}</strong>
                </div>

                <p className="lg-persona" title={t.persona}>{t.persona}</p>

                <Sparkline points={t.points} />

                <div className="lg-holdings">
                  {t.holdings.length === 0 && <span className="lg-muted">아직 보유 종목 없음</span>}
                  {t.holdings.slice(0, 6).map((h) => (
                    <Link key={h.symbol} to={`/stock?symbol=${h.symbol}`} className="lg-chip">
                      {h.name} <b>{Number(h.weight).toFixed(0)}%</b>
                      <span className={tone(h.returnRate)}>{signed(h.returnRate)}</span>
                    </Link>
                  ))}
                </div>

                <p className="lg-rules">
                  총자산 {won(t.totalAsset)}원 · 현금 {Number(t.cashWeight).toFixed(0)}%
                  {t.rules && <> · 규칙: 최대 {t.rules.maxPositions}종목, 한 종목 {t.rules.maxWeightPct}% 이하, 현금 {t.rules.minCashPct}% 이상</>}
                </p>
              </article>
            ))}
          </section>

          {/* ===== 매매 일지 ===== */}
          <section className="lg-diary">
            <h2>📒 AI 매매 일지</h2>
            <p className="lg-muted lg-diary-note">
              AI가 쓴 판단 기록이에요. 투자 권유가 아니고, 서버 규칙(종목 수·비중·현금)을 넘는 주문은 서버가 걸러요.
            </p>

            {days.length === 0 && <p className="lg-empty">첫 매매는 평일 오전 9시 10분에 시작돼요.</p>}

            {days.map((d) => (
              <div key={d.date} className="lg-day">
                <h3 className="lg-day-title">{dayLabel(d.date)}</h3>
                {d.logs.map((l) => (
                  <article key={l.logId} className="lg-log">
                    <div className="lg-log-head">
                      <span aria-hidden="true">{AVATAR[l.code] || '🤖'}</span>
                      <strong>{l.nickname}</strong>
                      {l.status === 'HOLD' && <span className="lg-tag">관망</span>}
                    </div>
                    {l.marketView && <p className="lg-view">“{l.marketView}”</p>}
                    {l.diary && <p className="lg-text">{l.diary}</p>}
                    {l.orders.length > 0 && (
                      <ul className="lg-orders">
                        {l.orders.map((o, i) => (
                          <li key={i} className={o.status === 'OK' ? '' : 'lg-order-skip'}>
                            <span className={`lg-side-tag ${o.side === 'BUY' ? 'lg-buy' : 'lg-sell'}`}>{o.side === 'BUY' ? '매수' : '매도'}</span>
                            <span className="lg-order-main">
                              <b>{o.name || o.symbol}</b>
                              {o.status === 'OK' && <> {won(o.quantity)}주 · {won(o.price)}원</>}
                              {o.reason && <span className="lg-order-reason">{o.reason}</span>}
                              {o.status !== 'OK' && o.note && <span className="lg-order-note">⛔ {o.note}</span>}
                            </span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </article>
                ))}
              </div>
            ))}
          </section>

          <Link to="/mock" className="lg-cta">🙋 나도 3,000만 원으로 참가하기 →</Link>
        </>
      )}
    </main>
  )
}

export default LeaguePage
