// ============================================
// 🎯 오를까 내릴까 (주소: /predict) - 매일 예측 게임
// - 다음 거래일 종가가 전날보다 오를지 내릴지 5종목 맞히기 (마감: 그 거래일 09:00)
// - 투표하면 다른 사람들 선택 비율이 보임 (투표 전에는 숨김 → 따라 찍기 방지)
// - 평일 21:10 자동 채점 → 지난 라운드 결과, 내 적중률·연속 적중, 주간·전체 랭킹
// - 비교 기준: 모멘텀 봇(최근 5일 + 면 오른다), 사람들 다수결
// - 백엔드: GET /api/predict, POST /api/predict/vote { itemId, pick }
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './PredictPage.css'

const won = (v) => (v == null ? '-' : Math.round(Number(v)).toLocaleString('ko-KR'))
const signed = (v) => (v == null ? '-' : `${Number(v) > 0 ? '+' : ''}${Number(v).toFixed(2)}%`)
const tone = (v) => (v == null || Number(v) === 0 ? '' : Number(v) > 0 ? 'pd-up' : 'pd-down')

const dayLabel = (text) => {
  const d = new Date(`${text}T00:00:00+09:00`)
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${'일월화수목금토'[d.getDay()]})`
}

// 남은 시간 "5시간 12분"
const leftText = (ms) => {
  if (ms <= 0) return '마감'
  const m = Math.floor(ms / 60000)
  const d = Math.floor(m / 1440)
  const h = Math.floor((m % 1440) / 60)
  if (d > 0) return `${d}일 ${h}시간`
  return h > 0 ? `${h}시간 ${m % 60}분` : `${m % 60}분`
}

const RESULT = { UP: { text: '▲ 올랐어요', cls: 'pd-up' }, DOWN: { text: '▼ 내렸어요', cls: 'pd-down' }, FLAT: { text: '보합 (점수 없음)', cls: '' }, VOID: { text: '휴장 (무효)', cls: '' } }

function PredictPage() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [board, setBoard] = useState('week')
  const [now, setNow] = useState(() => Date.now())

  const load = useCallback(() =>
    fetch('/api/predict')
      .then((res) => res.json())
      .then((json) => {
        if (!json.success) throw new Error(json.message)
        setData(json)
      })
      .catch((e) => setError(e.message || '서버에 연결할 수 없습니다.')), [])

  useEffect(() => {
    let ignore = false
    fetch('/api/predict')
      .then((res) => res.json())
      .then((json) => {
        if (ignore) return
        if (json.success) setData(json)
        else setError(json.message)
      })
      .catch(() => { if (!ignore) setError('서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [])

  // 마감까지 남은 시간 (1분마다)
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60000)
    return () => clearInterval(timer)
  }, [])

  const vote = async (item, pick) => {
    if (!data.loggedIn) {
      setNotice('로그인하면 예측에 참여할 수 있어요.')
      return
    }
    // 먼저 화면에 반영
    setData((d) => ({ ...d, items: d.items.map((i) => (i.itemId === item.itemId ? { ...i, myPick: pick } : i)) }))
    try {
      const res = await fetch('/api/predict/vote', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ itemId: item.itemId, pick }),
      })
      const json = await res.json()
      if (!json.success) throw new Error(json.message)
      await load()   // 다른 사람들 비율 보이게
    } catch (e) {
      setNotice(e.message || '저장하지 못했어요.')
      await load()
    }
  }

  const left = data ? data.round.deadline - now : 0
  const done = data?.items.filter((i) => i.myPick).length || 0

  return (
    <main className="pd-page">
      <header className="pd-hero">
        <p className="pd-eyebrow">매일 예측 게임</p>
        <h1>🎯 오를까 내릴까</h1>
        {data && (
          <p className="pd-desc">
            <b>{dayLabel(data.round.date)}</b> 종가가 전 거래일보다 오를까요, 내릴까요? 5종목을 맞혀 보세요.
            <span className="pd-deadline">⏰ 마감 {dayLabel(data.round.date)} 09:00 · {leftText(left)} 남음</span>
          </p>
        )}
      </header>

      {error && <p className="pd-empty">{error}</p>}
      {!data && !error && <p className="pd-empty">불러오는 중...</p>}
      {notice && (
        <p className="pd-notice" role="status">
          {notice} {!data?.loggedIn && <Link to="/login">로그인 →</Link>}
        </p>
      )}

      {data && (
        <>
          {/* ===== 이번 라운드 ===== */}
          <section className="pd-round">
            <div className="pd-round-head">
              <h2>이번 문제</h2>
              <span className="pd-muted">{data.loggedIn ? `${done} / ${data.items.length} 선택` : '로그인하면 참여할 수 있어요'}</span>
            </div>

            {data.items.length === 0 && <p className="pd-empty">문제를 준비하고 있어요. 시세가 쌓이면 열려요.</p>}

            <ul className="pd-items">
              {data.items.map((i) => {
                const total = (i.up || 0) + (i.down || 0)
                const upPct = total ? Math.round((i.up / total) * 100) : 0
                return (
                  <li key={i.itemId} className="pd-item">
                    <div className="pd-stock">
                      <Link to={`/stock?symbol=${i.symbol}`} className="pd-name">{i.name}</Link>
                      <span className="pd-muted">{i.symbol} · {won(i.lastClose)}원 · 5일 <span className={tone(i.ret5)}>{signed(i.ret5)}</span></span>
                    </div>
                    <div className="pd-buttons">
                      <button type="button" className={`pd-btn pd-btn-up ${i.myPick === 'UP' ? 'pd-on' : ''}`} onClick={() => vote(i, 'UP')} aria-pressed={i.myPick === 'UP'}>
                        🔴 오른다
                      </button>
                      <button type="button" className={`pd-btn pd-btn-down ${i.myPick === 'DOWN' ? 'pd-on' : ''}`} onClick={() => vote(i, 'DOWN')} aria-pressed={i.myPick === 'DOWN'}>
                        🔵 내린다
                      </button>
                    </div>
                    {i.up != null && (
                      <div className="pd-crowd" aria-label={`오른다 ${upPct}%, 내린다 ${100 - upPct}%`}>
                        <div className="pd-crowd-bar">
                          <span className="pd-crowd-up" style={{ width: `${total ? upPct : 50}%` }} />
                          <span className="pd-crowd-down" style={{ width: `${total ? 100 - upPct : 50}%` }} />
                        </div>
                        <span className="pd-muted">사람들 {total}명 · 오른다 {upPct}% / 내린다 {total ? 100 - upPct : 0}%</span>
                      </div>
                    )}
                  </li>
                )
              })}
            </ul>
            <p className="pd-rule">마감 전에는 몇 번이든 바꿀 수 있어요. 결과는 그날 저녁 9시쯤 채점돼요. 보합이거나 휴장이면 점수 없어요.</p>
          </section>

          <div className="pd-grid">
            {/* ===== 내 기록 ===== */}
            <section className="pd-card">
              <h2>내 기록</h2>
              {!data.loggedIn && <p className="pd-muted"><Link to="/login">로그인</Link>하면 적중률과 연속 적중이 기록돼요.</p>}
              {data.me && (
                <div className="pd-stats">
                  <div><span>적중률</span><b>{data.me.accuracy != null ? `${data.me.accuracy}%` : '-'}</b></div>
                  <div><span>맞힘</span><b>{data.me.hits} / {data.me.graded}</b></div>
                  <div><span>연속 적중</span><b>{data.me.streak > 0 ? `🔥 ${data.me.streak}` : '0'}</b></div>
                  <div><span>최고 연속</span><b>{data.me.bestStreak}</b></div>
                </div>
              )}
            </section>

            {/* ===== 사람들 vs 봇 ===== */}
            <section className="pd-card">
              <h2>사람들 vs 모멘텀 봇</h2>
              <div className="pd-vs">
                <div><span>🙋 사람들 다수결</span><b>{data.crowd.accuracy != null ? `${data.crowd.accuracy}%` : '-'}</b><small>{data.crowd.hits} / {data.crowd.graded}</small></div>
                <div><span>🤖 모멘텀 봇</span><b>{data.bot.accuracy != null ? `${data.bot.accuracy}%` : '-'}</b><small>{data.bot.hits} / {data.bot.graded}</small></div>
              </div>
              <p className="pd-rule">모멘텀 봇은 "최근 5일 올랐으면 오른다"는 규칙 하나로만 찍어요. 사람들이 이 단순한 규칙보다 잘 맞힐까요?</p>
            </section>
          </div>

          {/* ===== 지난 라운드 ===== */}
          {data.last && (
            <section className="pd-card pd-last">
              <h2>지난 결과 · {dayLabel(data.last.date)}</h2>
              <ul>
                {data.last.items.map((i) => (
                  <li key={i.itemId}>
                    <span className="pd-name">{i.name}</span>
                    <span className={(RESULT[i.result] || {}).cls}>{(RESULT[i.result] || {}).text} {i.changeRate != null && signed(i.changeRate)}</span>
                    <span className="pd-muted">
                      {i.myPick ? <>내 선택 {i.myPick === 'UP' ? '오른다' : '내린다'} {i.correct === true ? '✅' : i.correct === false ? '❌' : ''}</> : '참여 안 함'}
                      {' · '}봇 {i.botPick === 'UP' ? '오른다' : '내린다'} {i.result === i.botPick ? '✅' : ['UP', 'DOWN'].includes(i.result) ? '❌' : ''}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {/* ===== 랭킹 ===== */}
          <section className="pd-card">
            <div className="pd-round-head">
              <h2>🏆 랭킹</h2>
              <div className="pd-tabs" role="tablist">
                {[['week', '이번 주'], ['all', '전체']].map(([v, label]) => (
                  <button key={v} type="button" role="tab" aria-selected={board === v} className={`pd-tab ${board === v ? 'pd-tab-on' : ''}`} onClick={() => setBoard(v)}>{label}</button>
                ))}
              </div>
            </div>
            {data.leaderboard[board].length === 0 ? (
              <p className="pd-muted">{board === 'week' ? '이번 주 3문제 이상 채점된 사람이 아직 없어요.' : `${data.minGradedAll}문제 이상 채점되면 올라가요.`}</p>
            ) : (
              <ol className="pd-ranking">
                {data.leaderboard[board].map((r) => (
                  <li key={`${board}-${r.rank}`}>
                    <span className="pd-rank">{['🥇', '🥈', '🥉'][r.rank - 1] || r.rank}</span>
                    <span className="pd-rank-name">{r.nickname}</span>
                    <span className="pd-rank-value"><b>{r.hits}개</b> <small>{r.accuracy}% · {r.graded}문제</small></span>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </>
      )}
    </main>
  )
}

export default PredictPage
