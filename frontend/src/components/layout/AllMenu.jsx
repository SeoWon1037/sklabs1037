// ============================================
// ☰ 전체 메뉴 (메인 검색창 왼쪽 버튼 / 바로가기 "⋯ 전체")
// - 왼쪽에서 열리는 패널: 내 이름 · 바로가기 8칸 · 서비스 검색 · 카테고리별 목록
// - 바깥을 누르거나 Esc, 메뉴를 고르면 닫힘
// ============================================
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { CATEGORIES, SERVICES, SHORTCUT_KEYS } from './menuData'
import './AllMenu.css'

function Tile({ s, onClose }) {
  return (
    <Link to={s.to} className="am-tile" onClick={onClose}>
      <span className="am-tile-icon" style={{ '--c': s.color }}>{s.icon}</span>
      <span className="am-tile-label">{s.label}</span>
    </Link>
  )
}

function AllMenu({ user, onClose }) {

  const [q, setQ] = useState('')
  const panel = useRef(null)

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'   // 뒤 화면 스크롤 막기
    panel.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [onClose])

  const keyword = q.trim()
  const found = keyword
    ? Object.values(SERVICES).filter((s) => s.label.includes(keyword) || s.desc.includes(keyword))
    : null

  return (
    <div className="am-overlay" onClick={onClose}>
      <aside
        className="am-panel"
        ref={panel}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label="전체 메뉴"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="am-head">
          {user ? (
            <Link to="/mypage" className="am-user" onClick={onClose}>{user.nickname}님 ›</Link>
          ) : (
            <Link to="/login" className="am-user" onClick={onClose}>로그인하고 시작하기 ›</Link>
          )}
          <button type="button" className="am-close" onClick={onClose} aria-label="닫기">✕</button>
        </div>

        <div className="am-body">
          <section className="am-section">
            <h2>바로가기</h2>
            <div className="am-tiles">
              {SHORTCUT_KEYS.slice(0, 8).map((k) => <Tile key={k} s={SERVICES[k]} onClose={onClose} />)}
            </div>
          </section>

          <label className="am-search">
            <span aria-hidden="true">🔍</span>
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="서비스 검색 (예: 퀴즈, 성분)" />
          </label>

          {found ? (
            <section className="am-cat">
              <h3>검색 결과 {found.length}</h3>
              {found.length === 0 && <p className="am-empty">찾는 서비스가 없어요.</p>}
              <ul className="am-list">
                {found.map((s) => (
                  <li key={s.label}>
                    <Link to={s.to} onClick={onClose}><i style={{ '--c': s.color }}>{s.icon}</i>{s.label}<small>{s.desc}</small></Link>
                  </li>
                ))}
              </ul>
            </section>
          ) : (
            CATEGORIES.map((c) => (
              <section key={c.title} className="am-cat">
                <h3>{c.icon} {c.title} <em>{c.keys.length}</em></h3>
                <ul className="am-list">
                  {c.keys.map((k) => {
                    const s = SERVICES[k]
                    return (
                      <li key={k}>
                        <Link to={s.to} onClick={onClose}><i style={{ '--c': s.color }}>{s.icon}</i>{s.label}<small>{s.desc}</small></Link>
                      </li>
                    )
                  })}
                </ul>
              </section>
            ))
          )}
        </div>
      </aside>
    </div>
  )
}

export default AllMenu
