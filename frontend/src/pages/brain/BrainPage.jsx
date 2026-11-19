// ============================================
// 🧠 세컨드 브레인 · 내 메모 (주소: /brain, 로그인 필요)
// - 메모(RAW)를 적고 #태그를 달면 같은 주제끼리 묶여 뇌 모양 그래프에 나타남
// - [AI로 정리] 또는 AI 챗봇에게 "내 메모에서 Spring 관련 정리해줘"
//   → 내 메모만 근거로 정리 문서(WIKI)를 만들고, 문서 안 [[메모:12]]로 출처 메모를 이어 줌
// - 정리 문서를 보고 "이걸로 결과물 쓰기" → 내가 쓴 결과물(OUTPUT)이 정리 문서와 이어짐
// - 원칙: AI는 정리만, 무엇을 남기고 지울지는 내가 결정 (AI는 메모를 고치거나 지우지 않음)
// - ?note=ID 로 열면 그 노트를 바로 펼침 (챗봇이 정리한 뒤 이 주소로 엶)
// - API: GET/POST /api/brain/notes, GET /api/brain/notes/{id}, POST /api/brain/notes/{id}(/delete),
//        GET /api/brain/graph, POST /api/brain/organize, POST /api/brain/sample
// ============================================
import { Fragment, useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { onAiActed } from '../../voice/aiActed'
import BrainGraph from './BrainGraph'
import { KIND_COLOR } from './brainColors'
import './BrainPage.css'

const KIND_LABEL = { RAW: '메모', WIKI: 'AI 정리', OUTPUT: '결과물' }
const FILTERS = [['', '전체'], ['RAW', '메모'], ['WIKI', 'AI 정리'], ['OUTPUT', '결과물']]

const post = async (url, body) => {
  const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body ?? {}) })
  const json = await res.json().catch(() => ({}))
  if (!json.success) throw new Error(json.message || '잠시 후 다시 해 주세요.')
  return json
}

// 한 줄 안의 [[메모:12]] → 누르면 그 메모가 열리는 버튼
function Inline({ text, onOpen }) {
  const parts = text.split(/(\[\[메모:\d+\]\])/g)
  return parts.map((p, i) => {
    const m = p.match(/^\[\[메모:(\d+)\]\]$/)
    if (!m) return <Fragment key={i}>{p}</Fragment>
    return <button key={i} type="button" className="brain-ref" onClick={() => onOpen(Number(m[1]))}>메모 {m[1]}</button>
  })
}

// 본문: "## 제목", "### 작은 제목", "- 항목", ``` 코드 ```, `코드`, 나머지는 문단 (HTML로 넣지 않음 → 안전)
function NoteBody({ body, onOpen }) {
  const blocks = []
  let list = null
  let code = null
  body.split('\n').forEach((line, i) => {
    const t = line.trim()
    if (code) {
      if (t.startsWith('```')) { blocks.push(<pre key={`c${i}`} className="brain-code"><code>{code.join('\n')}</code></pre>); code = null }
      else code.push(line)
      return
    }
    if (t.startsWith('```')) { code = []; list = null; return }
    if (t.startsWith('- ')) {
      if (!list) { list = []; blocks.push({ list }) }
      list.push(<li key={i}><Inline text={t.slice(2)} onOpen={onOpen} /></li>)
      return
    }
    list = null
    if (t.startsWith('### ')) blocks.push(<h4 key={i}>{t.slice(4)}</h4>)
    else if (t.startsWith('## ')) blocks.push(<h3 key={i}>{t.slice(3)}</h3>)
    else if (t) blocks.push(<p key={i}><Inline text={t} onOpen={onOpen} /></p>)
  })
  if (code) blocks.push(<pre key="c-end" className="brain-code"><code>{code.join('\n')}</code></pre>)
  return <div className="brain-body">{blocks.map((b, i) => (b.list ? <ul key={`u${i}`}>{b.list}</ul> : b))}</div>
}

// 📥 SKLabs 스프링 코드 기록 (관리자만 보임): 한 번 받으면 배포할 때마다 서버가 새 기록을 넣어 줌
function CodeImport({ onDone }) {
  const [st, setSt] = useState(null)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')

  const load = useCallback(() => {
    fetch('/api/brain/import')
      .then((res) => res.json())
      .then((json) => setSt(json.success && json.admin ? json : null))
      .catch(() => setSt(null))
  }, [])
  useEffect(() => { load() }, [load])

  if (!st) return null

  const pull = async () => {
    setBusy(true)
    setMsg('')
    try {
      const json = await post('/api/brain/import')
      setMsg(json.added || json.updated ? `새로 ${json.added}개, 바뀜 ${json.updated}개` : '이미 최신이에요.')
      load()
      onDone()
    } catch (e) {
      setMsg(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="brain-import">
      <div>
        <b>📥 SKLabs 스프링 코드 기록</b>
        <span>
          {st.on
            ? `자동 저장 중 · 내 브레인에 ${st.imported}개 · 업데이트(배포)할 때마다 새 기록이 들어와요`
            : `패키지별 코드 요약 + 업데이트 기록 ${st.available}개를 내 브레인에 넣어요. 한 번 받으면 그 뒤로는 자동이에요.`}
        </span>
        {msg && <small>{msg}</small>}
      </div>
      <button type="button" className={`btn ${st.on ? '' : 'btn-primary'}`} onClick={pull} disabled={busy}>
        {busy ? '받는 중…' : st.on ? '지금 다시 받기' : '받기'}
      </button>
    </section>
  )
}

function BrainPage() {
  const [params, setParams] = useSearchParams()
  const [needLogin, setNeedLogin] = useState(false)
  const [graph, setGraph] = useState(null)
  const [notes, setNotes] = useState(null)
  const [filter, setFilter] = useState('')
  const [search, setSearch] = useState('')
  const [loaded, setNote] = useState(null)        // 마지막으로 불러온 노트
  const [editing, setEditing] = useState(null)    // { title, body, topics }
  const [draft, setDraft] = useState({ kind: 'RAW', body: '', fromId: null })
  const [query, setQuery] = useState('')
  const [busy, setBusy] = useState('')
  const [msg, setMsg] = useState('')
  const viewer = useRef(null)
  const composer = useRef(null)

  const noteId = Number(params.get('note')) || null
  const note = loaded && loaded.id === noteId ? loaded : null   // 펼친 노트 (주소와 같은 것만)

  const loadGraph = useCallback(() => {
    fetch('/api/brain/graph')
      .then((res) => {
        if (res.status === 401) setNeedLogin(true)
        return res.json()
      })
      .then((json) => setGraph(json.success ? json : { nodes: [], hubs: [], edges: [] }))
      .catch(() => setGraph({ nodes: [], hubs: [], edges: [] }))
  }, [])

  const loadList = useCallback(() => {
    const q = new URLSearchParams()
    if (filter) q.set('kind', filter)
    if (search.trim()) q.set('q', search.trim())
    fetch(`/api/brain/notes?${q}`)
      .then((res) => res.json())
      .then((json) => setNotes(json.success ? json.notes : []))
      .catch(() => setNotes([]))
  }, [filter, search])

  const reload = useCallback(() => { loadGraph(); loadList() }, [loadGraph, loadList])

  useEffect(() => { loadGraph() }, [loadGraph])
  useEffect(() => {
    const t = setTimeout(loadList, 250)
    return () => clearTimeout(t)
  }, [loadList])
  // 🎤 챗봇이 메모를 저장 · 정리했으면 다시
  useEffect(() => onAiActed(reload), [reload])

  // 주소의 ?note= 로 노트 펼치기
  useEffect(() => {
    if (!noteId) return undefined
    let alive = true
    fetch(`/api/brain/notes/${noteId}`)
      .then((res) => res.json())
      .then((json) => {
        if (!alive) return
        if (json.success) setNote(json.note)
        else setMsg(json.message || '노트를 찾지 못했어요.')
        requestAnimationFrame(() => viewer.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
      })
      .catch(() => {})
    return () => { alive = false }
  }, [noteId])

  const open = useCallback((id) => {
    setEditing(null)
    setParams(id ? { note: String(id) } : {})
  }, [setParams])
  const onHub = useCallback((label) => { setFilter(''); setSearch(label) }, [])

  const run = async (what, fn) => {
    if (busy) return
    setBusy(what)
    setMsg('')
    try { await fn() } catch (e) { setMsg(e.message) } finally { setBusy('') }
  }

  const organize = (e) => {
    e.preventDefault()
    const q = query.trim()
    if (!q) return
    run('organize', async () => {
      const json = await post('/api/brain/organize', { query: q })
      setQuery('')
      setMsg(`메모 ${json.note.sourceCount}개를 읽고 정리했어요.`)
      reload()
      open(json.note.id)
    })
  }

  const save = (e) => {
    e.preventDefault()
    if (!draft.body.trim()) return
    run('save', async () => {
      const json = await post('/api/brain/notes', draft)
      setDraft({ kind: 'RAW', body: '', fromId: null })
      setMsg(`저장했어요: ${json.note.title}`)
      reload()
    })
  }

  const sample = () => run('sample', async () => {
    const json = await post('/api/brain/sample')
    setMsg(`예시 메모 ${json.added}개를 넣었어요. 이제 위에서 "spring"을 정리해 보세요.`)
    setQuery('spring')
    reload()
  })

  const saveEdit = () => run('edit', async () => {
    const json = await post(`/api/brain/notes/${note.id}`, editing)
    setNote(json.note)
    setEditing(null)
    reload()
  })

  const remove = () => {
    if (!window.confirm(`"${note.title}"을(를) 지울까요? 되돌릴 수 없어요.`)) return
    run('delete', async () => {
      await post(`/api/brain/notes/${note.id}/delete`)
      setMsg('지웠어요.')
      open(null)
      reload()
    })
  }

  const writeOutput = () => {
    setDraft({ kind: 'OUTPUT', body: `# ${note.title.replace(/ ?정리$/, '')} — \n\n`, fromId: note.id })
    requestAnimationFrame(() => {
      composer.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      composer.current?.focus()
    })
  }

  if (needLogin) {
    return (
      <main className="brain-page">
        <h1>🧠 세컨드 브레인</h1>
        <p className="brain-muted">내 메모를 모으고 AI로 정리하려면 로그인이 필요해요.</p>
        <Link to="/login" className="btn btn-primary brain-login">로그인하러 가기</Link>
      </main>
    )
  }

  const empty = graph && graph.nodes.length === 0

  return (
    <main className="brain-page">
      <header className="brain-head">
        <h1>🧠 세컨드 브레인</h1>
        <p className="brain-muted">흩어진 메모를 모으고, AI가 주제별로 정리해 줘요. 정리는 AI가, 남길지 말지는 내가.</p>
      </header>

      <CodeImport onDone={reload} />

      <section className="brain-graph-card">
        <div className="brain-legend">
          {Object.entries(KIND_LABEL).map(([k, label]) => (
            <span key={k}><i style={{ background: KIND_COLOR[k] }} />{label}</span>
          ))}
          <span><i className="brain-hub-dot" />주제</span>
          <b>{graph ? `노트 ${graph.nodes.length}` : ''}</b>
        </div>
        {graph && <BrainGraph graph={graph} selectedId={noteId} onSelect={open} onHub={onHub} />}
        {!graph && <div className="brain-canvas brain-loading">불러오는 중…</div>}
        {empty && (
          <div className="brain-empty">
            <p>아직 메모가 없어요. 아래에 첫 메모를 적거나, 예시로 시작해 보세요.</p>
            <button type="button" className="btn btn-primary" onClick={sample} disabled={!!busy}>
              {busy === 'sample' ? '넣는 중…' : '예시 메모 넣기'}
            </button>
          </div>
        )}
      </section>

      <form className="brain-organize" onSubmit={organize}>
        <label htmlFor="brain-q">✨ AI로 정리</label>
        <div>
          <input id="brain-q" value={query} onChange={(e) => setQuery(e.target.value)} maxLength={40} placeholder="무엇을 정리할까요? (예: Spring)" />
          <button type="submit" className="btn btn-primary" disabled={!!busy || !query.trim()}>
            {busy === 'organize' ? '정리하는 중…' : '정리하기'}
          </button>
        </div>
        <p className="brain-muted">내 메모에 있는 내용만으로 정리하고, 근거 메모를 이어 둬요. 하루 20번까지. AI 챗봇에게 "내 메모에서 Spring 관련 정리해줘"라고 해도 돼요.</p>
      </form>

      {msg && <p className="brain-msg" role="status">{msg}</p>}

      {note && (
        <article className="brain-viewer" ref={viewer}>
          <div className="brain-viewer-top">
            <span className="brain-badge" style={{ background: KIND_COLOR[note.kind] }}>{KIND_LABEL[note.kind]}{note.sourceKey ? ' · 📥 코드 기록' : ''}</span>
            <button type="button" className="brain-x" onClick={() => open(null)} aria-label="닫기">✕</button>
          </div>
          {editing ? (
            <div className="brain-edit">
              <input value={editing.title} onChange={(e) => setEditing({ ...editing, title: e.target.value })} maxLength={100} aria-label="제목" />
              <textarea value={editing.body} onChange={(e) => setEditing({ ...editing, body: e.target.value })} rows={10} aria-label="내용" />
              <input value={editing.topics} onChange={(e) => setEditing({ ...editing, topics: e.target.value })} placeholder="주제 (쉼표로: spring, 트랜잭션)" aria-label="주제" />
              <div className="brain-actions">
                <button type="button" className="btn btn-primary" onClick={saveEdit} disabled={!!busy}>저장</button>
                <button type="button" className="btn" onClick={() => setEditing(null)}>취소</button>
              </div>
            </div>
          ) : (
            <>
              <h2>{note.title}</h2>
              {note.topics.length > 0 && (
                <div className="brain-topics">{note.topics.map((t) => <button type="button" key={t} onClick={() => onHub(t)}>#{t}</button>)}</div>
              )}
              <NoteBody body={note.body} onOpen={open} />
              {note.sources.length > 0 && (
                <div className="brain-links"><b>근거 메모</b>{note.sources.map((s) => <button type="button" key={s.id} onClick={() => open(s.id)}>{s.title}</button>)}</div>
              )}
              {note.usedBy.length > 0 && (
                <div className="brain-links"><b>이 노트로 만든 것</b>{note.usedBy.map((s) => <button type="button" key={s.id} onClick={() => open(s.id)}>{s.title}</button>)}</div>
              )}
              <div className="brain-actions">
                {note.kind === 'WIKI' && <button type="button" className="btn btn-primary" onClick={writeOutput}>이걸로 결과물 쓰기</button>}
                <button type="button" className="btn" onClick={() => setEditing({ title: note.title, body: note.body, topics: note.topics.join(', ') })}>수정</button>
                <button type="button" className="btn brain-danger" onClick={remove} disabled={!!busy}>삭제</button>
              </div>
            </>
          )}
        </article>
      )}

      <div className="brain-grid">
        <form className="brain-card" onSubmit={save}>
          <h2>{draft.kind === 'OUTPUT' ? '📝 결과물 쓰기' : '✏️ 메모 남기기'}</h2>
          <textarea
            ref={composer}
            value={draft.body}
            onChange={(e) => setDraft({ ...draft, body: e.target.value })}
            rows={6}
            maxLength={20000}
            placeholder={'떠오른 걸 그대로 적어요. 첫 줄이 제목이 돼요.\n예) @Transactional은 같은 클래스 안에서 부르면 안 걸린다 #spring'}
          />
          <p className="brain-muted">#spring 처럼 태그를 달면 같은 주제끼리 묶여요.{draft.fromId ? ' 정리 문서와 이어서 저장돼요.' : ''}</p>
          <div className="brain-actions">
            <button type="submit" className="btn btn-primary" disabled={!!busy || !draft.body.trim()}>{busy === 'save' ? '저장 중…' : '저장'}</button>
            {draft.kind === 'OUTPUT' && <button type="button" className="btn" onClick={() => setDraft({ kind: 'RAW', body: '', fromId: null })}>메모로 돌아가기</button>}
          </div>
        </form>

        <section className="brain-card">
          <h2>📚 내 노트</h2>
          <div className="brain-filters">
            {FILTERS.map(([k, label]) => (
              <button type="button" key={k} className={filter === k ? 'on' : ''} onClick={() => setFilter(k)}>{label}</button>
            ))}
          </div>
          <input className="brain-search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="제목 · 내용 · 주제 검색" aria-label="노트 검색" />
          {notes === null && <p className="brain-muted">불러오는 중…</p>}
          {notes?.length === 0 && <p className="brain-muted">{search || filter ? '맞는 노트가 없어요.' : '아직 노트가 없어요.'}</p>}
          <ul className="brain-list">
            {notes?.map((n) => (
              <li key={n.id}>
                <button type="button" className={n.id === noteId ? 'on' : ''} onClick={() => open(n.id)}>
                  <i style={{ background: KIND_COLOR[n.kind] }} />
                  <span>
                    <b>{n.title}</b>
                    <small>{n.topics.map((t) => `#${t}`).join(' ')}{n.topics.length ? ' · ' : ''}{n.updatedAt}</small>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </main>
  )
}

export default BrainPage
