// ============================================
// 🔔 알림 센터 (상단바, 로그인했을 때만)
// - 안 읽은 수: 처음 + 60초마다 + 화면(주소)이 바뀔 때 (GET /api/me/notifications/unread)
// - 누르면 최근 30개 목록 (GET /api/me/notifications), 하나 누르면 읽음 + 그 화면으로
// - 받는 알림: 새 배지, 예측 결과, 내 글 댓글, 목표가 도달, 신고로 가려짐
// - 📲 창 아래 "휴대폰·PC 알림": 켜면 사이트를 닫아도 같은 알림이 기기로 와요 (pwa.js)
// ============================================
import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { disablePush, enablePush, pushState, sendTestPush, syncPush } from '../../pwa'
import './NotificationBell.css'

const POLL_MS = 60 * 1000

const ago = (ms) => {
  const m = Math.floor((Date.now() - ms) / 60000)
  if (m < 1) return '방금'
  if (m < 60) return `${m}분 전`
  if (m < 1440) return `${Math.floor(m / 60)}시간 전`
  return `${Math.floor(m / 1440)}일 전`
}

// ===== 📲 휴대폰·PC 알림 켜기/끄기 =====
function PushToggle() {

  const [state, setState] = useState(null)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')

  useEffect(() => { pushState().then(setState).catch(() => setState('unsupported')) }, [])

  const act = async (fn, done) => {
    setBusy(true)
    setMsg('')
    try {
      await fn()
      if (done) setMsg(done)
    } catch (e) {
      setMsg(e.message)
    }
    setState(await pushState().catch(() => 'unsupported'))
    setBusy(false)
  }

  const test = () => act(async () => {
    const json = await sendTestPush()
    if (!json.success) throw new Error(json.message)
  }, '테스트 알림을 보냈어요. 몇 초 안에 와요.')

  if (state === null) return null

  return (
    <div className="bell-push">
      <div className="bell-push-row">
        <span>📲 휴대폰·PC 알림 {state === 'on' && <b className="bell-push-on">켜짐</b>}</span>
        {state === 'off' && <button type="button" className="bell-push-btn" disabled={busy} onClick={() => act(enablePush, '켜졌어요! 사이트를 닫아도 알림이 와요.')}>켜기</button>}
        {state === 'on' && (
          <span className="bell-push-actions">
            <button type="button" className="bell-all" disabled={busy} onClick={test}>테스트</button>
            <button type="button" className="bell-all" disabled={busy} onClick={() => act(disablePush, '이 기기 알림을 껐어요.')}>끄기</button>
          </span>
        )}
      </div>
      {state === 'ios-install' && <p>아이폰은 Safari <b>공유 → 홈 화면에 추가</b>로 설치한 앱에서 켤 수 있어요.</p>}
      {state === 'denied' && <p>브라우저 설정에서 이 사이트 알림이 막혀 있어요. 주소창 왼쪽 🔒에서 알림을 허용해 주세요.</p>}
      {state === 'unsupported' && <p>이 브라우저는 푸시 알림을 지원하지 않아요.</p>}
      {state === 'off' && !msg && <p>사이트를 닫아도 하트·대결 결과·가격 알림이 기기로 와요.</p>}
      {msg && <p>{msg}</p>}
    </div>
  )
}

function NotificationBell() {

  const [unread, setUnread] = useState(0)
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState(null)
  const box = useRef(null)
  const location = useLocation()
  const navigate = useNavigate()

  const refresh = useCallback(() => {
    fetch('/api/me/notifications/unread')
      .then((res) => res.json())
      .then((json) => { if (json.success) setUnread(json.unread) })
      .catch(() => {})
  }, [])

  // 60초마다 (다른 탭을 보고 있으면 건너뜀) + 주소가 바뀔 때
  useEffect(() => {
    refresh()
    const timer = setInterval(() => { if (document.visibilityState === 'visible') refresh() }, POLL_MS)
    return () => clearInterval(timer)
  }, [refresh, location.pathname])

  // 이미 알림을 켠 브라우저면 지금 로그인한 계정으로 다시 맞춤
  useEffect(() => { syncPush().catch(() => {}) }, [])

  // 바깥을 누르면 닫기
  useEffect(() => {
    if (!open) return undefined
    const onClick = (e) => { if (box.current && !box.current.contains(e.target)) setOpen(false) }
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const toggle = () => {
    if (open) {
      setOpen(false)
      return
    }
    setOpen(true)
    fetch('/api/me/notifications')
      .then((res) => res.json())
      .then((json) => {
        if (!json.success) return
        setItems(json.items)
        setUnread(json.unread)
      })
      .catch(() => setItems([]))
  }

  const read = (id) =>
    fetch('/api/me/notifications/read', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(id ? { id } : {}),
    })
      .then((res) => res.json())
      .then((json) => { if (json.success) setUnread(json.unread) })
      .catch(() => {})

  const openItem = (n) => {
    if (!n.read) {
      read(n.id)
      setItems((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)))
    }
    setOpen(false)
    if (n.link) navigate(n.link)
  }

  const readAll = () => {
    read(null)
    setItems((list) => list?.map((x) => ({ ...x, read: true })))
  }

  return (
    <div className="bell" ref={box}>
      <button type="button" className="bell-btn" onClick={toggle} aria-label={`알림 ${unread}개 안 읽음`} aria-expanded={open}>
        🔔
        {unread > 0 && <span className="bell-count">{unread > 99 ? '99+' : unread}</span>}
      </button>

      {open && (
        <div className="bell-panel" role="dialog" aria-label="알림">
          <div className="bell-head">
            <strong>알림</strong>
            {unread > 0 && <button type="button" className="bell-all" onClick={readAll}>모두 읽음</button>}
          </div>
          {items === null && <p className="bell-empty">불러오는 중...</p>}
          {items?.length === 0 && <p className="bell-empty">아직 알림이 없어요. 예측 결과·새 배지·내 글 댓글이 여기로 와요.</p>}
          {items?.length > 0 && (
            <ul className="bell-list">
              {items.map((n) => (
                <li key={n.id}>
                  <button type="button" className={`bell-item ${n.read ? '' : 'bell-unread'}`} onClick={() => openItem(n)}>
                    <b>{n.title}</b>
                    {n.body && <span>{n.body}</span>}
                    <small>{ago(n.insAt)}</small>
                  </button>
                </li>
              ))}
            </ul>
          )}
          <PushToggle />
        </div>
      )}
    </div>
  )
}

export default NotificationBell
