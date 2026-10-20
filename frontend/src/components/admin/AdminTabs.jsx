// ============================================
// 관리자 화면 위 탭 (운영 상태 / AI 피드백)
// ============================================
import { NavLink } from 'react-router-dom'

const TABS = [
  { to: '/admin/ops', label: '🛠 운영 상태' },
  { to: '/admin/feedback', label: '💬 AI 피드백' },
]

function AdminTabs() {
  return (
    <nav className="admin-tabs" aria-label="관리자 메뉴">
      {TABS.map((t) => (
        <NavLink key={t.to} to={t.to} className={({ isActive }) => `admin-tab ${isActive ? 'admin-tab-active' : ''}`}>
          {t.label}
        </NavLink>
      ))}
    </nav>
  )
}

export default AdminTabs
