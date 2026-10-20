// ============================================
// 관리자 화면 위 탭 (운영 상태 / 신고 관리 / AI 피드백)
// - 신고 관리 탭에는 처리할 글 수를 숫자로 (GET /api/admin/reports/count)
// ============================================
import { useEffect, useState } from 'react'
import { NavLink } from 'react-router-dom'
import './AdminTabs.css'

const TABS = [
  { to: '/admin/ops', label: '🛠 운영 상태' },
  { to: '/admin/reports', label: '🚩 신고 관리', badge: true },
  { to: '/admin/ai-improve', label: '🔁 AI 자기 개선' },
  { to: '/admin/qa', label: '🤖 QA 봇' },
  { to: '/admin/feedback', label: '💬 AI 피드백' },
]

function AdminTabs({ reportCount }) {

  // 신고 관리 화면은 자기 목록 길이를 넘겨줌, 다른 화면은 직접 물어봄
  const [fetched, setFetched] = useState(null)

  useEffect(() => {
    if (reportCount != null) return undefined
    let ignore = false
    fetch('/api/admin/reports/count')
      .then((res) => res.json())
      .then((data) => { if (!ignore && data.success) setFetched(data.count) })
      .catch(() => {})
    return () => { ignore = true }
  }, [reportCount])

  const count = reportCount ?? fetched

  return (
    <nav className="admin-tabs" aria-label="관리자 메뉴">
      {TABS.map((t) => (
        <NavLink key={t.to} to={t.to} className={({ isActive }) => `admin-tab ${isActive ? 'admin-tab-active' : ''}`}>
          {t.label}
          {t.badge && count > 0 && <span className="admin-tab-badge">{count}</span>}
        </NavLink>
      ))}
    </nav>
  )
}

export default AdminTabs
