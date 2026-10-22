// ============================================
// 📮 문의하기 (주소: /support, 푸터 "관리자 문의")
// - 종류(버그·기능 제안·질문·기타) + 제목 + 내용 → POST /api/support (로그인, 하루 5개)
//   어느 화면에서 왔는지(?from=/mock)를 같이 보내서 관리자가 바로 찾아볼 수 있게
// - 들어온 문의는 AI가 먼저 분류·요약해서 관리자에게 🔔·📲 알림 (사용자 화면에는 안 보임)
// - 아래: 내 문의 + 관리자 답변 (답변이 오면 🔔·📲 알림)
// - 로그인 안 했으면 메일 주소 안내
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CONTACT_EMAIL } from '../../site'
import './SupportPage.css'

const KINDS = [
  { value: 'BUG', label: '🐞 버그', hint: '어느 화면에서, 무엇을 했을 때, 어떻게 됐는지 적어주세요.\n예) 모의투자에서 지정가 매수를 누르면 "오류가 발생했어요"가 떠요. (아이폰 Safari)' },
  { value: 'FEATURE', label: '💡 기능 제안', hint: '있었으면 하는 기능이나 바꿨으면 하는 점을 적어주세요.' },
  { value: 'QUESTION', label: '❓ 질문', hint: '궁금한 점을 편하게 적어주세요.' },
  { value: 'OTHER', label: '💬 기타', hint: '무엇이든 적어주세요.' },
]
const STATUS = { OPEN: ['접수됨', 'sp-open'], ANSWERED: ['답변 완료', 'sp-done'], CLOSED: ['종료', 'sp-closed'] }
const kindLabel = (v) => KINDS.find((k) => k.value === v)?.label ?? v

const day = (ms) => {
  if (!ms) return ''
  const d = new Date(ms)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function SupportPage() {

  const [params] = useSearchParams()
  const from = params.get('from') || ''

  const [mine, setMine] = useState(null)
  const [login, setLogin] = useState(true)
  const [kind, setKind] = useState('BUG')
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const [error, setError] = useState('')

  const load = useCallback(() => {
    fetch('/api/support/mine')
      .then((r) => r.json())
      .then((j) => {
        if (!j.success) {
          setLogin(false)
          setMine([])
          return
        }
        setMine(j.inquiries)
      })
      .catch(() => setMine([]))
  }, [])

  useEffect(() => { load() }, [load])

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    setMsg('')
    const j = await fetch('/api/support', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ category: kind, title, body, page: from }),
    }).then((r) => r.json()).catch(() => ({ success: false, message: '서버에 연결하지 못했어요.' }))
    setBusy(false)
    if (!j.success) return setError(j.message)
    setTitle('')
    setBody('')
    setMsg('접수됐어요! 답변이 오면 🔔 알림으로 알려드릴게요.')
    load()
  }

  return (
    <main className="sp-page">
      <h1>📮 문의하기</h1>
      <p className="sp-sub">버그·기능 제안·궁금한 점을 남겨주세요. AI가 먼저 내용을 정리해서 관리자에게 바로 전달해요.</p>

      {!login ? (
        <section className="sp-card sp-login">
          <p>문의는 로그인 후 남길 수 있어요. 답변을 알림으로 받을 수 있거든요.</p>
          <Link to="/login" className="btn btn-primary">로그인</Link>
          <p className="sp-muted">로그인 없이 문의하려면 메일로 보내주세요 · <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a></p>
        </section>
      ) : (
        <form className="sp-card" onSubmit={submit}>
          <div className="sp-kinds" role="radiogroup" aria-label="문의 종류">
            {KINDS.map((k) => (
              <button key={k.value} type="button" role="radio" aria-checked={kind === k.value}
                className={`sp-kind ${kind === k.value ? 'sp-kind-on' : ''}`} onClick={() => setKind(k.value)}>
                {k.label}
              </button>
            ))}
          </div>
          {kind === 'FEATURE' && (
            <p className="sp-muted">다른 분들의 투표를 받고 싶다면 <Link to="/ideas">💡 기능 제안</Link> 게시판도 있어요.</p>
          )}

          <label className="sp-field">
            <span>제목</span>
            <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={60} placeholder="한 줄로 요약해 주세요" />
          </label>
          <label className="sp-field">
            <span>내용 <small>{body.length}/2000</small></span>
            <textarea rows={7} value={body} onChange={(e) => setBody(e.target.value)} maxLength={2000} placeholder={KINDS.find((k) => k.value === kind).hint} />
          </label>
          {from && <p className="sp-muted">보던 화면: {from}</p>}

          {error && <p className="sp-error" role="alert">{error}</p>}
          {msg && <p className="sp-ok" role="status">{msg}</p>}

          <button type="submit" className="btn btn-primary sp-submit" disabled={busy || title.trim().length < 2 || body.trim().length < 5}>
            {busy ? '보내는 중...' : '문의 보내기'}
          </button>
        </form>
      )}

      {login && (
        <section className="sp-mine">
          <h2>내 문의</h2>
          {mine === null && <p className="sp-muted">불러오는 중...</p>}
          {mine?.length === 0 && <p className="sp-muted">아직 남긴 문의가 없어요.</p>}
          <ul>
            {mine?.map((q) => (
              <li key={q.id} className="sp-card sp-item">
                <div className="sp-item-head">
                  <span className={`sp-status ${STATUS[q.status]?.[1] ?? ''}`}>{STATUS[q.status]?.[0] ?? q.status}</span>
                  <span className="sp-muted">{kindLabel(q.category)} · {day(q.insAt)}</span>
                </div>
                <strong>{q.title}</strong>
                <p className="sp-body">{q.body}</p>
                {q.reply && (
                  <div className="sp-reply">
                    <b>관리자 답변 <small>{day(q.repliedAt)}</small></b>
                    <p>{q.reply}</p>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
    </main>
  )
}

export default SupportPage
