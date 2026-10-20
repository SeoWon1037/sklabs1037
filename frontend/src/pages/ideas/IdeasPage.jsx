// ============================================
// 💡 기능 제안 + 투표 (주소: /ideas)
// - 있었으면 하는 기능을 적고 👍 투표 → 표를 많이 받은 것부터 만듦
// - 올리기 전에 AI가 비슷한 제안을 찾아줌 → 이미 있으면 거기에 투표
// - 상태: 제안 → 만드는 중 → 나왔어요 (보류) / 바뀌면 🔔 알림
// - 백엔드: GET/POST /api/ideas, POST /api/ideas/check, POST /api/ideas/{id}/vote, DELETE /api/ideas/{id}, POST /api/ideas/{id}/status
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './IdeasPage.css'

const STATUS = { OPEN: '제안', PLANNED: '만드는 중', DONE: '나왔어요', REJECTED: '보류' }
const FILTERS = [['', '전체'], ['OPEN', '제안'], ['PLANNED', '만드는 중'], ['DONE', '나왔어요'], ['REJECTED', '보류']]

const ago = (ms) => {
  const m = Math.floor((Date.now() - ms) / 60000)
  if (m < 60) return `${Math.max(1, m)}분 전`
  if (m < 1440) return `${Math.floor(m / 60)}시간 전`
  return `${Math.floor(m / 1440)}일 전`
}

const send = (url, method, body) =>
  fetch(url, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  }).then((res) => res.json())

function AdminBox({ idea, onDone }) {
  const [status, setStatus] = useState(idea.status)
  const [note, setNote] = useState(idea.adminNote || '')
  const [busy, setBusy] = useState(false)
  const save = async () => {
    setBusy(true)
    const json = await send(`/api/ideas/${idea.id}/status`, 'POST', { status, note }).catch(() => ({}))
    setBusy(false)
    if (json.success) onDone()
  }
  return (
    <div className="ip-admin">
      <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="상태">
        {Object.entries(STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
      </select>
      <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="관리자 한마디" maxLength={300} />
      <button type="button" className="btn btn-ghost" onClick={save} disabled={busy}>저장</button>
    </div>
  )
}

function IdeasPage() {

  const [sort, setSort] = useState('TOP')
  const [filter, setFilter] = useState('')
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [category, setCategory] = useState('')
  const [similar, setSimilar] = useState(null)   // null: 아직 확인 전
  const [busy, setBusy] = useState(false)
  const [formMsg, setFormMsg] = useState('')

  const load = useCallback(() => {
    const q = new URLSearchParams({ sort })
    if (filter) q.set('status', filter)
    return fetch(`/api/ideas?${q}`)
      .then((res) => res.json())
      .then((json) => {
        if (json.success) setData(json)
        else setError(json.message || '불러오지 못했어요.')
      })
      .catch(() => setError('서버에 연결할 수 없습니다.'))
  }, [sort, filter])

  useEffect(() => {
    let ignore = false
    const q = new URLSearchParams({ sort })
    if (filter) q.set('status', filter)
    fetch(`/api/ideas?${q}`)
      .then((res) => res.json())
      .then((json) => {
        if (ignore) return
        if (json.success) setData(json)
        else setError(json.message || '불러오지 못했어요.')
      })
      .catch(() => { if (!ignore) setError('서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [sort, filter])

  const vote = async (id) => {
    const json = await send(`/api/ideas/${id}/vote`, 'POST').catch(() => ({}))
    if (!json.success) return
    setData((d) => ({ ...d, ideas: d.ideas.map((x) => (x.id === id ? { ...x, voted: json.voted, voteCount: json.voteCount } : x)) }))
    setSimilar((list) => list?.map((x) => (x.id === id ? { ...x, voteCount: json.voteCount, votedNow: json.voted } : x)))
  }

  const remove = async (id) => {
    const json = await send(`/api/ideas/${id}`, 'DELETE').catch(() => ({}))
    if (json.success) load()
  }

  // 1단계: 비슷한 제안 찾기 → 없으면 바로 올림
  const check = async (e) => {
    e.preventDefault()
    setFormMsg('')
    setBusy(true)
    try {
      const json = await send('/api/ideas/check', 'POST', { title, body })
      if (!json.success) throw new Error(json.message)
      setCategory(json.category)
      if (json.similar.length > 0) setSimilar(json.similar)
      else await create(json.category)
    } catch (err) {
      setFormMsg(err.message || '확인하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  // 2단계: 올리기
  const create = async (cat) => {
    setBusy(true)
    try {
      const json = await send('/api/ideas', 'POST', { title, body, category: cat || category })
      if (!json.success) throw new Error(json.message)
      setTitle('')
      setBody('')
      setSimilar(null)
      setFormMsg('✅ 올렸어요! 내 제안에는 1표가 자동으로 들어가요.')
      setSort('NEW')
      setFilter('')
      load()
    } catch (err) {
      setFormMsg(err.message || '올리지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="ip-page">
      <header className="ip-hero">
        <p className="ip-eyebrow">IDEAS</p>
        <h1>💡 이런 기능 있었으면</h1>
        <p className="ip-desc">원하는 기능을 적고 👍로 투표해 주세요. 표를 많이 받은 것부터 만들고, 나오면 알림으로 알려드려요.</p>
      </header>

      {data?.loggedIn === false ? (
        <div className="ip-login">
          <p>로그인하면 제안하고 투표할 수 있어요.</p>
          <Link to="/login" className="btn btn-primary">로그인하기</Link>
        </div>
      ) : data && (
        <form className="ip-form" onSubmit={check}>
          <input
            value={title}
            onChange={(e) => { setTitle(e.target.value); setSimilar(null) }}
            placeholder="한 줄로: 예) 환율이 정해둔 값이 되면 알림"
            maxLength={60}
          />
          <textarea
            value={body}
            onChange={(e) => { setBody(e.target.value); setSimilar(null) }}
            placeholder="자세히 (선택): 언제, 왜 필요한지 적어주시면 더 잘 만들 수 있어요"
            maxLength={500}
            rows={3}
          />

          {similar && (
            <div className="ip-similar">
              <p className="ip-similar-title">🔎 비슷한 제안이 이미 있어요. 여기에 투표하면 더 빨리 만들어져요.</p>
              <ul>
                {similar.map((x) => (
                  <li key={x.id}>
                    <button type="button" className={`ip-vote-sm ${x.votedNow ? 'ip-voted' : ''}`} onClick={() => vote(x.id)}>👍 {x.voteCount}</button>
                    <span>{x.title}</span>
                    <em>{STATUS[x.status]}</em>
                  </li>
                ))}
              </ul>
              <div className="ip-similar-actions">
                <select value={category} onChange={(e) => setCategory(e.target.value)} aria-label="카테고리">
                  {data.categories.map((c) => <option key={c}>{c}</option>)}
                </select>
                <button type="button" className="btn btn-ghost" onClick={() => create()} disabled={busy}>그래도 새로 올리기</button>
              </div>
            </div>
          )}

          {!similar && (
            <button type="submit" className="btn btn-primary ip-submit" disabled={busy || title.trim().length < 4}>
              {busy ? 'AI가 비슷한 제안을 찾는 중...' : '제안 올리기'}
            </button>
          )}
          {formMsg && <p className="ip-msg">{formMsg}</p>}
        </form>
      )}

      <div className="ip-bar">
        <div className="ip-filters">
          {FILTERS.map(([k, label]) => (
            <button key={k} type="button" className={`ip-chip ${filter === k ? 'ip-chip-on' : ''}`} onClick={() => setFilter(k)}>
              {label}{k && data?.counts ? ` ${data.counts[k] ?? 0}` : ''}
            </button>
          ))}
        </div>
        <div className="ip-sort">
          <button type="button" className={sort === 'TOP' ? 'ip-sort-on' : ''} onClick={() => setSort('TOP')}>인기순</button>
          <button type="button" className={sort === 'NEW' ? 'ip-sort-on' : ''} onClick={() => setSort('NEW')}>최신순</button>
        </div>
      </div>

      {error && <p className="ip-muted">{error}</p>}
      {data?.ideas.length === 0 && <p className="ip-empty">아직 제안이 없어요. 첫 번째로 적어주세요!</p>}

      <ul className="ip-list">
        {data?.ideas.map((x) => (
          <li key={x.id} className={`ip-item ip-${x.status.toLowerCase()}`}>
            <button
              type="button"
              className={`ip-vote ${x.voted ? 'ip-voted' : ''}`}
              onClick={() => vote(x.id)}
              disabled={!data.loggedIn}
              aria-label={`투표 ${x.voteCount}표${x.voted ? ' (내가 투표함)' : ''}`}
            >
              <span>👍</span>
              <b>{x.voteCount}</b>
            </button>
            <div className="ip-main">
              <div className="ip-tags">
                <span className={`ip-status ip-st-${x.status.toLowerCase()}`}>{STATUS[x.status]}</span>
                <span className="ip-cat">{x.category}</span>
              </div>
              <p className="ip-title">{x.title}</p>
              {x.body && <p className="ip-body">{x.body}</p>}
              {x.adminNote && <p className="ip-note">🛠️ {x.adminNote}</p>}
              <p className="ip-meta">
                {x.nickname} · {ago(x.insAt)}
                {x.mine && <button type="button" className="ip-del" onClick={() => remove(x.id)}>지우기</button>}
              </p>
              {data.admin && <AdminBox idea={x} onDone={load} />}
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

export default IdeasPage
