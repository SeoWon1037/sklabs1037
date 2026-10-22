// ============================================
// 관리자 화면 위 탭 (운영 상태 / 문의함 / 신고 관리 / 초기화 신청 / AI 자기 개선 / QA 봇 / AI 피드백)
// - 문의함 탭에는 답변 기다리는 문의 수 (GET /api/admin/inquiries/count), AI 수정 탭에는 승인 기다리는 수
// - 초기화 신청 탭에는 기다리는 신청 수 (GET /api/admin/resets/count)
// - 신고 관리 탭에는 처리할 글 수를 숫자로 (GET /api/admin/reports/count)
// ============================================
import { useEffect, useState } from 'react'
import { NavLink } from 'react-router-dom'
import './AdminTabs.css'

const TABS = [
  { to: '/admin/ops', label: '🛠 운영 상태' },
  { to: '/admin/server', label: '🖥 서버 상태' },
  { to: '/admin/inquiries', label: '📮 문의함', badge: 'inquiries' },
  { to: '/admin/autofix', label: '🤖 AI 수정', badge: 'autofix' },
  { to: '/admin/reports', label: '🚩 신고 관리', badge: 'reports' },
  { to: '/admin/resets', label: '♻️ 초기화 신청', badge: 'resets' },
  { to: '/admin/ai-improve', label: '🔁 AI 자기 개선' },
  { to: '/admin/qa', label: '🤖 QA 봇' },
  { to: '/admin/feedback', label: '💬 AI 피드백' },
]

function AdminTabs({ reportCount }) {

  // 신고 관리 화면은 자기 목록 길이를 넘겨줌, 다른 화면은 직접 물어봄
  const [fetched, setFetched] = useState(null)
  const [resets, setResets] = useState(0)   // 기다리는 계좌 초기화 신청 수
  const [inquiries, setInquiries] = useState(0)   // 답변 기다리는 문의 수
  const [autofix, setAutofix] = useState(0)       // 승인 기다리는 AI 수정 수

  useEffect(() => {
    let ignore = false
    fetch('/api/admin/resets/count')
      .then((res) => res.json())
      .then((data) => { if (!ignore && data.success) setResets(data.count) })
      .catch(() => {})
    fetch('/api/admin/inquiries/count')
      .then((res) => res.json())
      .then((data) => { if (!ignore && data.success) setInquiries(data.count) })
      .catch(() => {})
    fetch('/api/admin/autofix/count')
      .then((res) => res.json())
      .then((data) => { if (!ignore && data.success) setAutofix(data.count) })
      .catch(() => {})
    return () => { ignore = true }
  }, [])

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
          {t.badge === 'reports' && count > 0 && <span className="admin-tab-badge">{count}</span>}
          {t.badge === 'resets' && resets > 0 && <span className="admin-tab-badge">{resets}</span>}
          {t.badge === 'inquiries' && inquiries > 0 && <span className="admin-tab-badge">{inquiries}</span>}
          {t.badge === 'autofix' && autofix > 0 && <span className="admin-tab-badge">{autofix}</span>}
        </NavLink>
      ))}
    </nav>
  )
}

export default AdminTabs
