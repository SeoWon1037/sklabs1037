// ============================================
// 🔔 알림 센터 (상단바, 로그인했을 때만)
// - 안 읽은 수: 처음 + 60초마다 + 화면(주소)이 바뀔 때 (GET /api/me/notifications/unread)
// - 누르면 최근 30개 목록 (GET /api/me/notifications), 하나 누르면 읽음 + 그 화면으로
// - 받는 알림: 새 배지, 예측 결과, 내 글 댓글, 목표가 도달, 신고로 가려짐
// ============================================
import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import './NotificationBell.css'

const POLL_MS = 60 * 1000

const ago = (ms) => {
  const m = Math.floor((Date.now() - ms) / 60000)
  if (m < 1) return '방금'
  if (m < 60) return `${m}분 전`
  if (m < 1440) return `${Math.floor(m / 60)}시간 전`
  return `${Math.floor(m / 1440)}일 전`
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
        </div>
      )}
    </div>
  )
}

export default NotificationBell
