// ============================================
// ⌘K 빠른 이동 (어디서든 Ctrl+K · ⌘K · / 키, PC 상단바 🔍 버튼)
// - 서비스(☰ 메뉴와 같은 목록) · 종목(GET /api/stock/search) · "AI 비서에게 묻기"를 한 칸에서
// - ↑↓로 고르고 Enter, Esc로 닫기
// ============================================
import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router-dom'
import { SERVICES } from './menuData'
import './CommandPalette.css'

// 입력칸·글쓰기 중에는 "/" 키를 가로채지 않음
const typing = (el) => el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable)

function Palette({ onClose }) {

  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [stocks, setStocks] = useState([])
  const [active, setActive] = useState(0)
  const input = useRef(null)
  const keyword = q.trim()

  useEffect(() => { input.current?.focus() }, [])

  // 종목 검색 (입력 멈추고 0.2초 뒤)
  useEffect(() => {
    if (keyword.length < 1) return undefined
    let ignore = false
    const t = setTimeout(() => {
      fetch(`/api/stock/search?q=${encodeURIComponent(keyword)}`)
        .then((r) => r.json())
        .then((j) => { if (!ignore) setStocks((j.stocks ?? []).slice(0, 6)) })
        .catch(() => { if (!ignore) setStocks([]) })
    }, 200)
    return () => { ignore = true; clearTimeout(t) }
  }, [keyword])

  const items = useMemo(() => {
    const services = Object.values(SERVICES)
      .filter((s) => !keyword || s.label.includes(keyword) || s.desc.includes(keyword))
      .slice(0, keyword ? 5 : 8)
      .map((s) => ({ key: `s:${s.to}`, icon: s.icon, title: s.label, sub: s.desc, to: s.to, group: '서비스' }))
    const found = keyword ? stocks.map((s) => ({
      key: `k:${s.symbol}`, icon: '📈', title: s.name, sub: [s.symbol, s.market].filter(Boolean).join(' · '), to: `/stock?symbol=${s.symbol}`, group: '종목',
    })) : []
    const ask = keyword ? [{ key: 'ai', icon: '🤖', title: `AI 비서에게 묻기: "${keyword}"`, sub: '시세·뉴스·종목 문서를 찾아보고 답해요', to: `/ai?q=${encodeURIComponent(keyword)}`, group: 'AI' }] : []
    return [...found, ...services, ...ask]
  }, [keyword, stocks])

  const go = (it) => {
    if (!it) return
    onClose()
    navigate(it.to)
  }

  const onKey = (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, items.length - 1)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)) }
    else if (e.key === 'Enter') { e.preventDefault(); go(items[Math.min(active, items.length - 1)]) }
    else if (e.key === 'Escape') onClose()
  }

  let lastGroup = ''

  return createPortal(
    <div className="cp-overlay" onClick={onClose}>
      <div className="cp-box" role="dialog" aria-modal="true" aria-label="빠른 이동" onClick={(e) => e.stopPropagation()}>
        <div className="cp-input">
          <span aria-hidden="true">🔍</span>
          <input
            ref={input}
            value={q}
            onChange={(e) => { setQ(e.target.value); setActive(0) }}
            onKeyDown={onKey}
            placeholder="종목, 서비스, 궁금한 것 (예: 삼성전자, 퀴즈, 금리 인하 영향)"
            aria-label="빠른 이동 검색"
            role="combobox"
            aria-expanded="true"
            aria-controls="cp-list"
            aria-activedescendant={items[active] ? `cp-${active}` : undefined}
          />
          <kbd>Esc</kbd>
        </div>
        <ul className="cp-list" id="cp-list" role="listbox">
          {items.map((it, i) => {
            const head = it.group !== lastGroup
            lastGroup = it.group
            return (
              <li key={it.key} role="presentation">
                {head && <p className="cp-group">{it.group}</p>}
                <button
                  type="button"
                  id={`cp-${i}`}
                  role="option"
                  aria-selected={i === active}
                  className={`cp-item ${i === active ? 'cp-active' : ''}`}
                  onMouseEnter={() => setActive(i)}
                  onClick={() => go(it)}
                >
                  <i aria-hidden="true">{it.icon}</i>
                  <span><b>{it.title}</b><small>{it.sub}</small></span>
                </button>
              </li>
            )
          })}
        </ul>
        <p className="cp-foot"><kbd>↑</kbd><kbd>↓</kbd> 고르기 · <kbd>Enter</kbd> 이동 · 어디서든 <kbd>Ctrl</kbd>+<kbd>K</kbd></p>
      </div>
    </div>,
    document.body,
  )
}

function CommandPalette() {

  const [open, setOpen] = useState(false)

  useEffect(() => {
    const onKey = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setOpen((o) => !o)
      } else if (e.key === '/' && !typing(document.activeElement)) {
        e.preventDefault()
        setOpen(true)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <>
      <button type="button" className="theme-toggle cp-open" onClick={() => setOpen(true)} aria-label="빠른 이동 (Ctrl+K)" title="빠른 이동 (Ctrl+K)">
        🔍
      </button>
      {open && <Palette onClose={() => setOpen(false)} />}
    </>
  )
}

export default CommandPalette
