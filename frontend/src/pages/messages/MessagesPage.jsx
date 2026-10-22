// ============================================
// 💬 1:1 메시지 (주소: /messages, /messages?room=방번호)
// - 왼쪽(휴대폰은 첫 화면): 대화방 목록 + [새 메시지] 닉네임 찾기
// - 오른쪽(휴대폰은 방을 누르면): 대화 · 읽음 "1" · 이전 메시지 더 보기 · 차단
// - 실시간: GET /api/dm/stream (SSE) → 새 메시지·상대가 읽음 바로 반영
//   이 화면을 안 보고 있으면 서버가 📲 푸시로 알려줌
// - Enter 보내기 / Shift+Enter 줄바꿈 (한글 입력 중 Enter는 무시 → 두 번 가는 문제 방지)
// ============================================
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import './MessagesPage.css'

const getJson = (url) => fetch(url).then((r) => r.json()).catch(() => ({ success: false, message: '서버에 연결하지 못했어요.' }))
const postJson = (url, body) =>
  fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body ?? {}) })
    .then((r) => r.json())
    .catch(() => ({ success: false, message: '서버에 연결하지 못했어요.' }))

const hhmm = (ms) => {
  const d = new Date(ms)
  const h = d.getHours()
  return `${h < 12 ? '오전' : '오후'} ${((h + 11) % 12) + 1}:${String(d.getMinutes()).padStart(2, '0')}`
}
const dayLabel = (ms) => {
  const d = new Date(ms)
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 ${'일월화수목금토'[d.getDay()]}요일`
}
const listTime = (ms) => {
  if (!ms) return ''
  const d = new Date(ms)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return hhmm(ms)
  return `${d.getMonth() + 1}/${d.getDate()}`
}
const tellUnread = (n) => window.dispatchEvent(new CustomEvent('dm-unread', { detail: n }))

// ===== 새 메시지: 닉네임 찾기 =====
function NewChat({ onOpen, onClose }) {

  const [q, setQ] = useState('')
  const [users, setUsers] = useState([])
  const [msg, setMsg] = useState('')

  useEffect(() => {
    const word = q.trim()
    if (!word) return undefined
    const t = setTimeout(() => {
      getJson(`/api/dm/users?q=${encodeURIComponent(word)}`).then((j) => {
        setUsers(j.success ? j.users : [])
        setMsg(j.success ? (j.users.length ? '' : '그 닉네임으로 시작하는 회원이 없어요.') : j.message)
      })
    }, 250)
    return () => clearTimeout(t)
  }, [q])

  const shown = q.trim() ? users : []

  return (
    <div className="dm-new">
      <div className="dm-new-head">
        <input autoFocus value={q} onChange={(e) => setQ(e.target.value)} placeholder="닉네임으로 찾기" maxLength={20} aria-label="닉네임으로 찾기" />
        <button type="button" className="dm-link" onClick={onClose}>닫기</button>
      </div>
      {q.trim() && msg && <p className="dm-muted">{msg}</p>}
      <ul className="dm-new-list">
        {shown.map((u) => (
          <li key={u.id}>
            <button type="button" onClick={() => onOpen(u.id)}>
              <span className="dm-avatar">{u.nickname.slice(0, 1)}</span>{u.nickname}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

function MessagesPage() {

  const [params, setParams] = useSearchParams()
  const roomId = Number(params.get('room')) || null
  const navigate = useNavigate()

  const [login, setLogin] = useState(true)
  const [rooms, setRooms] = useState(null)
  const [newOpen, setNewOpen] = useState(false)
  const [room, setRoom] = useState(null)          // { partner, partnerRead, messages, hasMore, canSend, blockedByMe }
  const [text, setText] = useState('')
  const [error, setError] = useState('')
  const [sending, setSending] = useState(false)
  const [menu, setMenu] = useState(false)

  const scroller = useRef(null)
  const stickBottom = useRef(true)
  const keepFrom = useRef(null)                     // 이전 메시지를 붙일 때 스크롤 위치 유지
  const page = useRef(null)
  const roomRef = useRef(roomId)

  // 화면 높이 = 보이는 창 - 상단바 (상단바 높이는 기기마다 달라서 재서 씀)
  useLayoutEffect(() => {
    const fit = () => {
      const bottom = document.querySelector('.header')?.getBoundingClientRect().bottom ?? 64
      page.current?.style.setProperty('--dm-top', `${Math.max(0, Math.round(bottom))}px`)
    }
    fit()
    window.addEventListener('resize', fit)
    return () => window.removeEventListener('resize', fit)
  }, [])
  useEffect(() => { roomRef.current = roomId }, [roomId])

  const loadRooms = useCallback(() => {
    getJson('/api/dm/rooms').then((j) => {
      if (!j.success) {
        if (j.message?.includes('로그인')) setLogin(false)
        setRooms([])
        return
      }
      setRooms(j.rooms)
      tellUnread(j.unread)
    })
  }, [])

  // 방 읽음 처리
  const markRead = useCallback((id, msgs) => {
    const last = msgs?.length ? msgs[msgs.length - 1].id : null
    if (!last || document.visibilityState !== 'visible') return
    postJson(`/api/dm/rooms/${id}/read`, { lastId: last }).then((j) => {
      if (j.success) tellUnread(j.unread)
      setRooms((list) => list?.map((r) => (r.id === id ? { ...r, unread: 0 } : r)))
    })
  }, [])

  useEffect(() => { loadRooms() }, [loadRooms])

  // 방 열기
  useEffect(() => {
    if (!roomId) return undefined
    let alive = true
    getJson(`/api/dm/rooms/${roomId}`).then((j) => {
      if (!alive) return
      if (!j.success) {
        setError(j.message)
        setRoom(null)
        return
      }
      setError('')
      stickBottom.current = true
      setRoom(j)
      markRead(roomId, j.messages)
    })
    return () => { alive = false }
  }, [roomId, markRead])

  // 실시간
  useEffect(() => {
    if (!login) return undefined
    const es = new EventSource('/api/dm/stream')
    es.addEventListener('msg', (e) => {
      const { roomId: rid, message } = JSON.parse(e.data)
      if (rid === roomRef.current) {
        setRoom((r) => {
          if (!r || r.messages.some((m) => m.id === message.id)) return r
          const mine = message.senderId !== r.partner.id
          const messages = [...r.messages, { ...message, mine }]
          if (!mine) markRead(rid, messages)
          return { ...r, messages }
        })
      }
      loadRooms()
    })
    es.addEventListener('read', (e) => {
      const { roomId: rid, readId } = JSON.parse(e.data)
      if (rid === roomRef.current) setRoom((r) => (r ? { ...r, partnerRead: Math.max(r.partnerRead, readId) } : r))
    })
    return () => es.close()
  }, [login, loadRooms, markRead])

  // 다른 탭에서 돌아오면 읽음
  useEffect(() => {
    const onVis = () => { if (document.visibilityState === 'visible' && roomRef.current && room) markRead(roomRef.current, room.messages) }
    document.addEventListener('visibilitychange', onVis)
    return () => document.removeEventListener('visibilitychange', onVis)
  }, [room, markRead])

  // 스크롤: 새 메시지면 맨 아래로 / 이전 메시지를 붙이면 보던 자리 그대로
  useLayoutEffect(() => {
    const el = scroller.current
    if (!el) return
    if (keepFrom.current != null) {
      el.scrollTop = el.scrollHeight - keepFrom.current
      keepFrom.current = null
    } else if (stickBottom.current) {
      el.scrollTop = el.scrollHeight
    }
  }, [room])

  const onScroll = () => {
    const el = scroller.current
    stickBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
  }

  const loadOlder = async () => {
    const first = room.messages[0]?.id
    const j = await getJson(`/api/dm/rooms/${roomId}?before=${first}`)
    if (!j.success) return
    keepFrom.current = scroller.current.scrollHeight - scroller.current.scrollTop
    setRoom((r) => ({ ...r, messages: [...j.messages, ...r.messages], hasMore: j.hasMore }))
  }

  const openWith = async (userId) => {
    const j = await postJson('/api/dm/rooms', { userId })
    if (!j.success) return setError(j.message)
    setNewOpen(false)
    setParams({ room: String(j.roomId) })
  }

  const send = async () => {
    const body = text.trim()
    if (!body || sending) return
    setSending(true)
    setError('')
    const j = await postJson(`/api/dm/rooms/${roomId}/messages`, { body })
    setSending(false)
    if (!j.success) return setError(j.message)
    setText('')
    stickBottom.current = true
    setRoom((r) => (r.messages.some((m) => m.id === j.message.id) ? r : { ...r, messages: [...r.messages, { ...j.message, mine: true }] }))
    loadRooms()
  }

  const onKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing && window.matchMedia('(min-width: 641px)').matches) {
      e.preventDefault()
      send()
    }
  }

  const toggleBlock = async () => {
    setMenu(false)
    const on = !room.blockedByMe
    if (on && !window.confirm(`${room.partner.nickname}님을 차단할까요? 서로 메시지를 보낼 수 없어요.`)) return
    const j = await postJson(`/api/dm/rooms/${roomId}/block`, { on })
    if (!j.success) return setError(j.message)
    const fresh = await getJson(`/api/dm/rooms/${roomId}`)
    if (fresh.success) setRoom(fresh)
  }

  if (!login) {
    return (
      <main className="dm-page">
        <div className="dm-empty-page">
          <p>💬 메시지는 로그인 후 쓸 수 있어요.</p>
          <Link to="/login" className="btn btn-primary">로그인</Link>
        </div>
      </main>
    )
  }

  // 메시지 묶기: 날짜 구분선 + 같은 사람이 1분 안에 연달아 보내면 시간은 마지막에만
  const items = []
  const msgs = room?.messages ?? []
  msgs.forEach((m, i) => {
    const prev = msgs[i - 1]
    const next = msgs[i + 1]
    if (!prev || new Date(prev.at).toDateString() !== new Date(m.at).toDateString()) items.push({ day: m.at })
    const showTime = !next || next.mine !== m.mine || Math.floor(next.at / 60000) !== Math.floor(m.at / 60000)
    items.push({ m, showTime, unread: m.mine && m.id > (room?.partnerRead ?? 0) })
  })

  return (
    <main ref={page} className={`dm-page ${roomId ? 'dm-has-room' : ''}`}>
      {/* ===== 목록 ===== */}
      <section className="dm-list">
        <div className="dm-list-head">
          <h1>💬 메시지</h1>
          <button type="button" className="dm-new-btn" onClick={() => setNewOpen((v) => !v)}>✏️ 새 메시지</button>
        </div>
        {newOpen && <NewChat onOpen={openWith} onClose={() => setNewOpen(false)} />}
        {rooms === null && <p className="dm-muted dm-pad">불러오는 중...</p>}
        {rooms?.length === 0 && !newOpen && <p className="dm-muted dm-pad">아직 대화가 없어요. ✏️ 새 메시지로 닉네임을 찾아 말을 걸어보세요.</p>}
        <ul className="dm-rooms">
          {rooms?.map((r) => (
            <li key={r.id}>
              <button type="button" className={`dm-room ${r.id === roomId ? 'dm-room-on' : ''}`} onClick={() => setParams({ room: String(r.id) })}>
                <span className="dm-avatar">{r.partnerName?.slice(0, 1)}</span>
                <span className="dm-room-text">
                  <b>{r.partnerName}{r.partnerGone && <small> (탈퇴)</small>}</b>
                  <span>{r.lastMine ? '나: ' : ''}{r.lastBody}</span>
                </span>
                <span className="dm-room-side">
                  <small>{listTime(r.lastAt)}</small>
                  {r.unread > 0 && <i>{r.unread > 99 ? '99+' : r.unread}</i>}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </section>

      {/* ===== 대화 ===== */}
      <section className="dm-chat">
        {!roomId && <div className="dm-empty-page dm-hide-mobile"><p>왼쪽에서 대화를 고르거나 ✏️ 새 메시지를 눌러주세요.</p></div>}
        {roomId && (
          <>
            <div className="dm-chat-head">
              <button type="button" className="dm-back" onClick={() => navigate('/messages')} aria-label="목록으로">‹</button>
              <strong>{room?.partner.nickname ?? ''}</strong>
              {room && (
                <div className="dm-more">
                  <button type="button" className="dm-link" onClick={() => setMenu((v) => !v)} aria-label="더 보기">⋯</button>
                  {menu && (
                    <div className="dm-menu">
                      <button type="button" onClick={toggleBlock}>{room.blockedByMe ? '차단 풀기' : '🚫 차단하기'}</button>
                    </div>
                  )}
                </div>
              )}
            </div>

            <div className="dm-scroll" ref={scroller} onScroll={onScroll}>
              {room?.hasMore && <button type="button" className="dm-older" onClick={loadOlder}>이전 메시지 더 보기</button>}
              {room && msgs.length === 0 && <p className="dm-muted dm-center">{room.partner.nickname}님에게 첫 메시지를 보내보세요 👋</p>}
              {items.map((it, i) => (it.day
                ? <p key={`d${i}`} className="dm-day">{dayLabel(it.day)}</p>
                : (
                  <div key={it.m.id} className={`dm-row ${it.m.mine ? 'dm-mine' : ''}`}>
                    <p className="dm-bubble">{it.m.body}</p>
                    <span className="dm-meta">
                      {it.unread && <b>1</b>}
                      {it.showTime && <small>{hhmm(it.m.at)}</small>}
                    </span>
                  </div>
                )))}
            </div>

            {error && <p className="dm-error" role="alert">{error}</p>}

            {room && (room.canSend ? (
              <div className="dm-input">
                <textarea
                  rows={1}
                  value={text}
                  maxLength={1000}
                  onChange={(e) => setText(e.target.value)}
                  onKeyDown={onKey}
                  placeholder="메시지 입력"
                  aria-label="메시지 입력"
                />
                <button type="button" onClick={send} disabled={!text.trim() || sending}>보내기</button>
              </div>
            ) : (
              <p className="dm-muted dm-closed">{room.blockedByMe ? '차단한 상대예요. ⋯ 에서 풀 수 있어요.' : '지금은 메시지를 보낼 수 없는 상대예요.'}</p>
            ))}
          </>
        )}
      </section>
    </main>
  )
}

export default MessagesPage
