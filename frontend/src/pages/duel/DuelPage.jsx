// ============================================
// ⚔️ 친구와 1:1 대결
//   /duel        규칙 + 대결 만들기 + 내 대결 목록
//   /duel/:code  초대 받기 · 실시간 점수판 · 결과
// - 점수 = 🎯 예측 적중 ×10 + 📰 퀴즈 정답 ×5 + 📅 출석 하루 ×3 (받은 날부터 7일)
// - 백엔드: GET/POST /api/duel, GET /api/duel/{code}, POST /api/duel/{code}/accept|cancel
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import './DuelPage.css'

const md = (d) => (d ? `${Number(d.slice(5, 7))}/${Number(d.slice(8, 10))}` : '')

const STATUS = { WAITING: '친구 기다리는 중', ACTIVE: '진행 중', DONE: '끝', EXPIRED: '만료' }
const RESULT = { WIN: '🏆 승리', LOSE: '패배', DRAW: '🤝 무승부' }

function Rules() {
  return (
    <ul className="dl-rules">
      <li><span>🎯</span>예측 적중 <b>×10점</b></li>
      <li><span>📰</span>뉴스 퀴즈 정답 <b>×5점</b></li>
      <li><span>📅</span>출석 하루 <b>×3점</b></li>
    </ul>
  )
}

// ===== /duel =====
function DuelHome() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [making, setMaking] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    let ignore = false
    fetch('/api/duel')
      .then((res) => res.json())
      .then((json) => { if (!ignore) setData(json) })
      .catch(() => { if (!ignore) setError('서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [])

  const create = async () => {
    setMaking(true)
    setError('')
    try {
      const res = await fetch('/api/duel', { method: 'POST' })
      const json = await res.json()
      if (!json.success) throw new Error(json.message)
      navigate(`/duel/${json.code}`)
    } catch (e) {
      setError(e.message || '대결을 만들지 못했어요.')
      setMaking(false)
    }
  }

  return (
    <div className="dl-page">
      <header className="dl-hero">
        <p className="dl-eyebrow">1 : 1 DUEL</p>
        <h1>⚔️ 친구와 대결</h1>
        <p className="dl-desc">초대 링크를 보내고, 친구가 받은 날부터 <b>7일</b> 동안 점수를 모아요. 끝나면 승자에게 ⚔️ 배지!</p>
        <Rules />
        {data?.loggedIn === false ? (
          <Link to="/login" className="btn btn-primary dl-main-btn">로그인하고 대결 만들기</Link>
        ) : (
          <button type="button" className="btn btn-primary dl-main-btn" onClick={create} disabled={making || !data}>
            {making ? '만드는 중...' : '⚔️ 대결 만들기'}
          </button>
        )}
        {error && <p className="dl-error">{error}</p>}
      </header>

      {data?.duels?.length > 0 && (
        <section className="dl-list">
          <h2>내 대결</h2>
          <ul>
            {data.duels.map((d) => (
              <li key={d.code}>
                <Link to={`/duel/${d.code}`} className="dl-item">
                  <span className={`dl-chip dl-${d.status.toLowerCase()}`}>{d.result ? RESULT[d.result] : STATUS[d.status]}</span>
                  <span className="dl-item-main">
                    <b>{d.opponent ? `vs ${d.opponent}` : '초대 링크를 보내주세요'}</b>
                    <small>{d.startDate ? `${md(d.startDate)} ~ ${md(d.endDate)}` : '3일 안에 친구가 받아야 시작'}</small>
                  </span>
                  {d.status === 'DONE' && <span className="dl-item-score">{d.myScore} : {d.opponentScore}</span>}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

// ===== /duel/:code =====
function Side({ name, s, lead, me }) {
  return (
    <div className={`dl-side ${lead ? 'dl-lead' : ''}`}>
      <p className="dl-name">{name || '?'}{me && <em>나</em>}</p>
      <p className="dl-score">{s ? s.score : '-'}</p>
      {s && (
        <ul className="dl-break">
          <li>🎯 적중 {s.predictHits}</li>
          <li>📰 정답 {s.quizScore}</li>
          <li>📅 출석 {s.attendDays}</li>
        </ul>
      )}
    </div>
  )
}

function DuelView({ code }) {

  const [duel, setDuel] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [toast, setToast] = useState('')

  const load = useCallback(() => {
    return fetch(`/api/duel/${encodeURIComponent(code)}`)
      .then((res) => res.json())
      .then((json) => {
        if (json.success) setDuel(json.duel)
        else setError(json.message || '대결을 찾을 수 없어요.')
      })
      .catch(() => setError('서버에 연결할 수 없습니다.'))
  }, [code])

  useEffect(() => {
    let ignore = false
    fetch(`/api/duel/${encodeURIComponent(code)}`)
      .then((res) => res.json())
      .then((json) => {
        if (ignore) return
        if (json.success) setDuel(json.duel)
        else setError(json.message || '대결을 찾을 수 없어요.')
      })
      .catch(() => { if (!ignore) setError('서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [code])

  const post = async (action) => {
    setBusy(true)
    setError('')
    try {
      const res = await fetch(`/api/duel/${encodeURIComponent(code)}/${action}`, { method: 'POST' })
      const json = await res.json()
      if (!json.success) throw new Error(json.message)
      if (json.duel) setDuel(json.duel)
      else await load()
    } catch (e) {
      setError(e.message || '처리하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  const url = `${window.location.origin}/duel/${code}`
  const invite = async () => {
    const text = `${duel.hostName}님이 SKLabs 7일 대결을 신청했어요 ⚔️`
    if (typeof navigator.share === 'function') {
      try {
        await navigator.share({ title: 'SKLabs 대결', text, url })
        return
      } catch { /* 취소하면 복사 */ }
    }
    try {
      await navigator.clipboard.writeText(`${text} ${url}`)
      setToast('초대 링크를 복사했어요')
    } catch {
      setToast(url)
    }
    setTimeout(() => setToast(''), 2000)
  }

  if (error && !duel) return <div className="dl-page"><p className="dl-muted">{error}</p><Link to="/duel">← 대결 홈</Link></div>
  if (!duel) return <div className="dl-page"><p className="dl-muted">불러오는 중...</p></div>

  const hostLead = duel.host && duel.guest && duel.host.score > duel.guest.score
  const guestLead = duel.host && duel.guest && duel.guest.score > duel.host.score
  const myWin = duel.status === 'DONE' && duel.winner && duel.winner === duel.role
  const myLose = duel.status === 'DONE' && duel.winner && duel.role !== 'VIEWER' && duel.winner !== duel.role

  return (
    <div className="dl-page">
      <Link to="/duel" className="dl-back">← 대결 홈</Link>

      {duel.status === 'WAITING' && (
        <section className="dl-hero">
          <p className="dl-eyebrow">INVITATION</p>
          <h1>⚔️ {duel.hostName}님의 7일 대결</h1>
          <Rules />
          {duel.role === 'HOST' ? (
            <>
              <p className="dl-desc">친구가 이 링크를 열고 "대결 받기"를 누르면 그날부터 시작해요. (3일 안에)</p>
              <div className="dl-actions">
                <button type="button" className="btn btn-primary" onClick={invite}>📤 초대 링크 보내기</button>
                <button type="button" className="btn btn-ghost" onClick={() => post('cancel')} disabled={busy}>초대 취소</button>
              </div>
            </>
          ) : duel.canAccept ? (
            <button type="button" className="btn btn-primary dl-main-btn" onClick={() => post('accept')} disabled={busy}>
              {busy ? '시작하는 중...' : '⚔️ 대결 받기'}
            </button>
          ) : (
            <Link to="/login" className="btn btn-primary dl-main-btn">로그인하고 대결 받기</Link>
          )}
        </section>
      )}

      {duel.status === 'EXPIRED' && (
        <section className="dl-hero">
          <h1>⌛ 만료된 초대예요</h1>
          <p className="dl-desc">3일 동안 아무도 받지 않았거나 취소된 대결이에요.</p>
          <Link to="/duel" className="btn btn-primary dl-main-btn">새 대결 만들기</Link>
        </section>
      )}

      {(duel.status === 'ACTIVE' || duel.status === 'DONE') && (
        <section className="dl-board">
          <p className="dl-eyebrow">{md(duel.startDate)} ~ {md(duel.endDate)} · {duel.status === 'ACTIVE' ? `D-${Math.max(0, duel.daysLeft - 1)} (오늘 포함 ${duel.daysLeft}일 남음)` : '끝'}</p>
          {duel.status === 'DONE' && (
            <h1 className="dl-result">
              {myWin ? '🏆 이겼어요!' : myLose ? '아쉽게 졌어요' : duel.winner ? `🏆 ${duel.winner === 'HOST' ? duel.hostName : duel.guestName}님 승리` : '🤝 비겼어요'}
            </h1>
          )}
          <div className="dl-vs">
            <Side name={duel.hostName} s={duel.status === 'DONE' ? { ...duel.host, score: duel.hostScore } : duel.host} lead={hostLead} me={duel.role === 'HOST'} />
            <span className="dl-vs-mark">VS</span>
            <Side name={duel.guestName} s={duel.status === 'DONE' ? { ...duel.guest, score: duel.guestScore } : duel.guest} lead={guestLead} me={duel.role === 'GUEST'} />
          </div>
          {duel.status === 'ACTIVE' && (
            <>
              <p className="dl-muted dl-note">예측은 장 마감 뒤 저녁 9시쯤 채점돼요. 마지막 날 다음 날 0시 30분에 최종 결과가 나와요.</p>
              <div className="dl-actions">
                <Link to="/predict" className="btn btn-primary">🎯 예측하러 가기</Link>
                <Link to="/quiz" className="btn btn-ghost">📰 뉴스 퀴즈</Link>
              </div>
            </>
          )}
        </section>
      )}

      {error && <p className="dl-error">{error}</p>}
      {toast && <p className="dl-toast">{toast}</p>}
    </div>
  )
}

function DuelPage() {
  const { code } = useParams()
  return code ? <DuelView key={code} code={code} /> : <DuelHome />
}

export default DuelPage
