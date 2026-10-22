// ============================================
// 💬 상단 메시지 버튼 (로그인했을 때만, 🔔 왼쪽)
// - 안 읽은 메시지 수: 처음 + 60초마다 + 주소가 바뀔 때 + 메시지 화면이 "dm-unread" 이벤트를 보낼 때
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'

function MessageButton() {

  const [unread, setUnread] = useState(0)
  const location = useLocation()

  const refresh = useCallback(() => {
    fetch('/api/dm/unread')
      .then((r) => r.json())
      .then((j) => { if (j.success) setUnread(j.unread) })
      .catch(() => {})
  }, [])

  useEffect(() => {
    refresh()
    const timer = setInterval(() => { if (document.visibilityState === 'visible') refresh() }, 60 * 1000)
    const onEvent = (e) => (typeof e.detail === 'number' ? setUnread(e.detail) : refresh())
    window.addEventListener('dm-unread', onEvent)
    return () => {
      clearInterval(timer)
      window.removeEventListener('dm-unread', onEvent)
    }
  }, [refresh, location.pathname, location.search])

  return (
    <Link to="/messages" className="bell-btn msg-btn" aria-label={`메시지 ${unread}개 안 읽음`} title="메시지">
      💬
      {unread > 0 && <span className="bell-count">{unread > 99 ? '99+' : unread}</span>}
    </Link>
  )
}

export default MessageButton
