// ============================================
// 💕 커플 (주소: /couple)
// - 커플이면: 두 사람 · 함께한 날(시작일 = 1일) · 다가오는 기념일 D-day · 커플 끊기
// - 아니면: 상대 닉네임 + 시작일로 신청 / 받은 신청 수락·거절 / 보낸 신청 취소
// - 커플 정보는 두 사람에게만 보여요
// - 백엔드: GET /api/couple, POST /api/couple/request, /api/couple/{id}/accept|decline|cancel, /api/couple/end
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './CouplePage.css'

const dot = (d) => d.replaceAll('-', '.')

const post = (url, body) =>
  fetch(url, {
    method: 'POST',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  }).then((res) => res.json())

function CouplePage() {

  const [data, setData] = useState(null)
  const [needLogin, setNeedLogin] = useState(false)
  const [error, setError] = useState('')
  const [nickname, setNickname] = useState('')
  const [startDate, setStartDate] = useState('')
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const [confirmEnd, setConfirmEnd] = useState(false)

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
          setStartDate(json.today)
        } else setError(json.message || '불러오지 못했어요.')
      })
      .catch(() => { if (!ignore) setError('서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [])

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
              : <span>다음 기념일 <b>{c.next.label}</b> · {dot(c.next.date)} · <b>D-{c.next.dDay}</b></span>}
          </div>
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
      ) : (
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
