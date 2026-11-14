// ============================================
// 💕 커플 (주소: /couple)
// - 커플이면: 두 사람 · 함께한 날(시작일 = 1일) · 다가오는 기념일 D-day · 커플 끊기
// - 아니면: 상대 닉네임 + 시작일로 신청 / 받은 신청 수락·거절 / 보낸 신청 취소
// - 커플 정보는 두 사람에게만 보여요
// - 백엔드: GET /api/couple, POST /api/couple/request, /api/couple/{id}/accept|decline|cancel, /api/couple/end
// - ⏱ 다음 기념일 · 생일까지 남은 시간은 D-day 대신 일 · 시간 · 분 · 초로 1초마다 (Countdown)
// - 💌 한마디: POST /api/couple/note { text } → 상대에게 알림 · 푸시로 내용이 감, 최근 5개 표시 (couple.notes)
// - 🗓️ 함께하는 약속: CouplePlans (약속 잡기 · 목록 · 남은 시간)
// - 🎂 생일: POST /api/couple/birthday { who: me | partner, month, day } → 커플 정보 (couple.birthdays.me / partner: { month, day, dDay })
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import CoupleAlbum from './CoupleAlbum'
import CouplePlans from './CouplePlans'
import Countdown from './Countdown'
import { onAiActed } from '../../voice/aiActed'
import './CouplePage.css'

const dot = (d) => d.replaceAll('-', '.')

const post = (url, body) =>
  fetch(url, {
    method: 'POST',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  }).then((res) => res.json())

// 🎂 생일 한 줄: 입력 전에는 [생일 넣기], 입력 뒤에는 날짜와 D-day (두 사람 모두 서로의 생일을 넣고 고칠 수 있음)
const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1)
const daysIn = (month) => new Date(2024, month, 0).getDate()   // 2024 = 윤년 → 2월 29일도 고를 수 있음

function BirthdayRow({ who, name, birthday, busy, onSave }) {

  const [editing, setEditing] = useState(false)
  const [month, setMonth] = useState(birthday?.month ?? 1)
  const [day, setDay] = useState(birthday?.day ?? 1)

  const changeMonth = (m) => {
    setMonth(m)
    setDay((d) => Math.min(d, daysIn(m)))
  }

  if (editing) {
    return (
      <li className="cp-bday-edit">
        <span className="cp-bday-name">🎂 {name}</span>
        <div className="cp-bday-selects">
          <select value={month} onChange={(e) => changeMonth(Number(e.target.value))} aria-label={`${name} 생일 월`}>
            {MONTHS.map((m) => <option key={m} value={m}>{m}월</option>)}
          </select>
          <select value={day} onChange={(e) => setDay(Number(e.target.value))} aria-label={`${name} 생일 일`}>
            {Array.from({ length: daysIn(month) }, (_, i) => i + 1).map((d) => <option key={d} value={d}>{d}일</option>)}
          </select>
        </div>
        <div className="cp-bday-actions">
          <button type="button" className="btn btn-primary" disabled={busy} onClick={() => { onSave(who, month, day); setEditing(false) }}>저장</button>
          {birthday && <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => { onSave(who, null, null); setEditing(false) }}>지우기</button>}
          <button type="button" className="btn btn-ghost" onClick={() => setEditing(false)}>취소</button>
        </div>
      </li>
    )
  }

  return (
    <li>
      <span className="cp-bday-name">🎂 {name}</span>
      {birthday ? (
        <>
          <span className="cp-bday-date">{birthday.month}월 {birthday.day}일</span>
          <span className="cp-bday-dday"><Countdown date={birthday.date} done="오늘! 🎉" /></span>
          <button type="button" className="cp-bday-btn" onClick={() => setEditing(true)}>수정</button>
        </>
      ) : (
        <button type="button" className="cp-bday-btn cp-bday-add" onClick={() => setEditing(true)}>생일 넣기</button>
      )}
    </li>
  )
}

// 방금 · 5분 전 · 3시간 전 · 2일 전
const ago = (ms) => {
  const m = Math.floor((Date.now() - Number(ms)) / 60000)
  if (m < 1) return '방금'
  if (m < 60) return `${m}분 전`
  if (m < 1440) return `${Math.floor(m / 60)}시간 전`
  return `${Math.floor(m / 1440)}일 전`
}

function CouplePage() {

  const [data, setData] = useState(null)
  const [needLogin, setNeedLogin] = useState(false)
  const [error, setError] = useState('')
  const [nickname, setNickname] = useState('')
  const [startDate, setStartDate] = useState('')
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const [confirmEnd, setConfirmEnd] = useState(false)
  const [floats, setFloats] = useState([])   // 하트 날아가는 효과
  const [note, setNote] = useState('')        // 💌 한마디 입력칸

  // 💗 하트 날리기
  const sendHeart = async () => {
    const key = Date.now()
    setFloats((f) => [...f.slice(-8), { key, x: Math.round(Math.random() * 60 - 30) }])
    setTimeout(() => setFloats((f) => f.filter((h) => h.key !== key)), 1200)
    try {
      const json = await post('/api/couple/heart')
      if (!json.success) throw new Error(json.message)
      setMsg('')
      setData((d) => ({ ...d, couple: { ...d.couple, heartsSent: json.sent } }))
    } catch (e) {
      setMsg(e.message || '하트를 보내지 못했어요.')
    }
  }

  // 🎤 챗봇이 이 화면에서 하트를 보내거나 약속을 잡았으면 숫자 · 목록을 다시 불러옴
  const [reloadKey, setReloadKey] = useState(0)
  useEffect(() => onAiActed(() => setReloadKey((k) => k + 1)), [])

  useEffect(() => {
    let ignore = false
    fetch('/api/couple')
      .then((res) => {
        if (res.status === 401) {
          if (!ignore) setNeedLogin(true)
          return null
        }
        return res.json()
      })
      .then((json) => {
        if (ignore || !json) return
        if (json.success) {
          setData(json)
          setStartDate((d) => d || json.today)
        } else setError(json.message || '불러오지 못했어요.')
      })
      .catch(() => { if (!ignore) setError('서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [reloadKey])

  const act = async (url, body, done) => {
    setBusy(true)
    setMsg('')
    try {
      const json = await post(url, body)
      if (!json.success) throw new Error(json.message)
      setData(json)
      if (done) setMsg(done)
    } catch (e) {
      setMsg(e.message || '처리하지 못했어요.')
    } finally {
      setBusy(false)
      setConfirmEnd(false)
    }
  }

  // 💌 한마디 보내기 → 상대에게 알림 · 푸시로 내용이 감
  const sendNote = async (e) => {
    e.preventDefault()
    const text = note.trim()
    if (!text || busy) return
    setNote('')
    await act('/api/couple/note', { text }, '💌 보냈어요. 상대에게 알림이 갔어요.')
  }

  const saveBirthday = (who, month, day) =>
    act('/api/couple/birthday', { who, month, day }, month ? '🎂 생일을 저장했어요.' : '생일을 지웠어요.')

  if (needLogin) {
    return (
      <div className="cp-page">
        <h1 className="cp-title">💕 커플</h1>
        <div className="cp-box cp-center">
          <p>로그인하면 커플을 맺고 함께한 날을 셀 수 있어요.</p>
          <Link to="/login" className="btn btn-primary">로그인하기</Link>
        </div>
      </div>
    )
  }

  if (error) return <div className="cp-page"><p className="cp-muted">{error}</p></div>
  if (!data) return <div className="cp-page"><p className="cp-muted">불러오는 중...</p></div>

  const c = data.couple

  return (
    <div className="cp-page">
      <h1 className="cp-title">💕 커플</h1>

      {c ? (
        <section className="cp-hero">
          <div className="cp-names">
            <span>{c.myName}</span>
            <i aria-hidden="true">♥</i>
            <span>{c.partnerName}</span>
          </div>
          <p className="cp-days"><b>{c.days.toLocaleString()}</b>일째</p>
          <p className="cp-since">{dot(c.startDate)}부터</p>
          <div className="cp-next">
            {c.next.dDay === 0
              ? <span>🎉 오늘은 <b>{c.next.label}</b>이에요!</span>
              : <span>다음 기념일 <b>{c.next.label}</b> · {dot(c.next.date)}<br /><Countdown date={c.next.date} done={`🎉 ${c.next.label}`} /> 남았어요</span>}
          </div>

          <div className="cp-heart-area">
            <button type="button" className="cp-heart-btn" onClick={sendHeart} aria-label={`${c.partnerName}님에게 하트 보내기`}>
              💗
              {floats.map((h) => <span key={h.key} className="cp-float" style={{ '--x': `${h.x}px` }} aria-hidden="true">💗</span>)}
            </button>
            <p className="cp-heart-hint">눌러서 {c.partnerName}님에게 하트 보내기</p>
            <p className="cp-heart-count">
              보낸 하트 <b>{c.heartsSent.total.toLocaleString()}</b> (오늘 {c.heartsSent.today}) · 받은 하트 <b>{c.heartsReceived.total.toLocaleString()}</b> (오늘 {c.heartsReceived.today})
            </p>
          </div>

          {/* 💌 한마디: 쓰면 상대에게 알림으로 내용이 감 */}
          <div className="cp-note">
            <form onSubmit={sendNote}>
              <input value={note} onChange={(e) => setNote(e.target.value.slice(0, 100))} maxLength={100} placeholder={`${c.partnerName}님에게 한마디`} aria-label={`${c.partnerName}님에게 보낼 한마디`} enterKeyHint="send" />
              <button type="submit" className="btn btn-primary" disabled={busy || !note.trim()}>보내기</button>
            </form>
            {c.notes && c.notes.length > 0 && (
              <ul>
                {c.notes.slice(0, 5).map((n) => (
                  <li key={n.id} className={n.mine ? 'cp-note-mine' : ''}>
                    <span>{n.content}</span>
                    <small>{n.mine ? '나' : c.partnerName} · {ago(n.insAt)}</small>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* 🎂 생일: 얼마나 남았는지 (7일 전 · 하루 전 · 당일 오전 9시에 알림) */}
          {c.birthdays && (
            <div className="cp-bday">
              <h2>🎂 생일</h2>
              <ul>
                <BirthdayRow who="partner" name={c.partnerName} birthday={c.birthdays.partner} busy={busy} onSave={saveBirthday} />
                <BirthdayRow who="me" name={c.myName} birthday={c.birthdays.me} busy={busy} onSave={saveBirthday} />
              </ul>
              <p>생일 7일 전 · 하루 전 · 당일 오전 9시에 알려드려요. AI 챗봇에게 “커플 생일 얼마나 남았어?”라고 물어도 돼요.</p>
            </div>
          )}

          <p className="cp-private">커플 정보는 두 분에게만 보여요.</p>

          {!confirmEnd ? (
            <button type="button" className="cp-end" onClick={() => setConfirmEnd(true)}>커플 끊기</button>
          ) : (
            <div className="cp-confirm">
              <span>정말 끊을까요? 상대에게 알림이 가요.</span>
              <button type="button" className="btn btn-ghost" onClick={() => setConfirmEnd(false)}>아니요</button>
              <button type="button" className="btn cp-danger" onClick={() => act('/api/couple/end')} disabled={busy}>끊기</button>
            </div>
          )}
        </section>
      ) : null}

      {c && <CouplePlans partnerName={c.partnerName} reloadKey={reloadKey} />}

      {c && <CoupleAlbum key={c.id} />}

      {!c && (
        <>
          {data.received.length > 0 && (
            <section className="cp-box">
              <h2>💌 받은 신청</h2>
              <ul className="cp-list">
                {data.received.map((r) => (
                  <li key={r.id}>
                    <span><b>{r.nickname}</b>님 · 시작일 {dot(r.startDate)}</span>
                    <div className="cp-actions">
                      <button type="button" className="btn btn-primary" onClick={() => act(`/api/couple/${r.id}/accept`, null, '💕 커플이 됐어요!')} disabled={busy}>수락</button>
                      <button type="button" className="btn btn-ghost" onClick={() => act(`/api/couple/${r.id}/decline`)} disabled={busy}>거절</button>
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {data.sent.length > 0 ? (
            <section className="cp-box">
              <h2>보낸 신청</h2>
              <ul className="cp-list">
                {data.sent.map((s) => (
                  <li key={s.id}>
                    <span><b>{s.nickname}</b>님의 수락을 기다리는 중 · 시작일 {dot(s.startDate)}</span>
                    <button type="button" className="btn btn-ghost" onClick={() => act(`/api/couple/${s.id}/cancel`)} disabled={busy}>신청 취소</button>
                  </li>
                ))}
              </ul>
            </section>
          ) : (
            <form
              className="cp-box cp-form"
              onSubmit={(e) => {
                e.preventDefault()
                act('/api/couple/request', { nickname, startDate }, '💌 신청을 보냈어요. 상대가 수락하면 함께한 날이 보여요.')
              }}
            >
              <h2>커플 신청하기</h2>
              <label>
                <span>상대 닉네임</span>
                <input value={nickname} onChange={(e) => setNickname(e.target.value)} placeholder="띄어쓰기까지 똑같이" maxLength={30} />
              </label>
              <label>
                <span>시작일 (이 날이 1일)</span>
                <input type="date" value={startDate} max={data.today} onChange={(e) => setStartDate(e.target.value)} />
              </label>
              <button type="submit" className="btn btn-primary" disabled={busy || !nickname.trim() || !startDate}>💌 신청 보내기</button>
              <p className="cp-muted">상대가 수락해야 커플이 돼요. 보낸 신청은 한 번에 하나만 둘 수 있어요.</p>
            </form>
          )}
        </>
      )}

      {msg && <p className="cp-msg">{msg}</p>}
    </div>
  )
}

export default CouplePage
