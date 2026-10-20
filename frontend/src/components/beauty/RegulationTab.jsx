// ============================================
// 뷰티 랩 > 📚 규제 Q&A (RAG)
// - 질문 → 등록된 규제 문서에서 뜻이 가까운 문단을 찾아(pgvector) → AI가 그 문단만 근거로 답 + [번호] 출처
// - 관리자: 문서 올리기(PDF·TXT) / 삭제, 처리 진행 상황
// - 백엔드 API: GET/POST /api/beauty/regulation/docs, DELETE /api/beauty/regulation/docs/{id}, POST /api/beauty/regulation/ask
// ============================================
import { Fragment, useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { formatDay, postJson } from './beautyUtils'

const EXAMPLES = [
  '미백 기능성화장품으로 광고하려면 어떤 절차가 필요해?',
  '알레르기 유발 착향제는 언제 성분명을 표시해야 해?',
  '화장품 광고에 쓰면 안 되는 표현은 뭐가 있어?',
  '영유아용 화장품은 어떤 자료를 보관해야 해?',
]

// 관리자에게 권하는 문서 (공개 자료)
const RECOMMENDED = [
  '화장품법 · 화장품법 시행규칙 (국가법령정보센터에서 PDF 저장)',
  '화장품 안전기준 등에 관한 규정 (식약처 고시)',
  '기능성화장품 심사에 관한 규정 (식약처 고시)',
  '화장품 표시·광고 관리 가이드라인 (식약처 민원인 안내서)',
]

const STATUS = { PROCESSING: ['처리 중', 'amber'], READY: ['사용 중', 'green'], FAIL: ['실패', 'red'] }

// "…필요해요 [1][3]" → 번호를 눌러 출처로 이동할 수 있게
function AnswerText({ text }) {
  const parts = text.split(/(\[\d+\])/g)
  return (
    <p className="bl-answer">
      {parts.map((p, i) => {
        const m = p.match(/^\[(\d+)\]$/)
        return m
          ? <a key={i} href={`#src-${m[1]}`} className="bl-cite">{m[1]}</a>
          : <Fragment key={i}>{p}</Fragment>
      })}
    </p>
  )
}

function RegulationTab({ loggedIn, result, onResult }) {

  const [docs, setDocs] = useState(null)       // { docs, admin, ready }
  const [question, setQuestion] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [upload, setUpload] = useState({ file: null, title: '', source: '' })
  const [uploading, setUploading] = useState(false)
  const [uploadMsg, setUploadMsg] = useState({ ok: false, text: '' })

  const loadDocs = useCallback(() => {
    return fetch('/api/beauty/regulation/docs')
      .then((res) => res.json())
      .then((json) => setDocs(json.success ? json : { docs: [], error: json.message }))
      .catch(() => setDocs({ docs: [], error: '서버에 연결할 수 없습니다.' }))
  }, [])

  // 처음 + 처리 중인 문서가 있으면 5초마다
  const processing = docs?.docs?.some((d) => d.status === 'PROCESSING')
  useEffect(() => {
    loadDocs()
    if (!processing) return
    const timer = setInterval(loadDocs, 5000)
    return () => clearInterval(timer)
  }, [loadDocs, processing])

  const ask = async (q = question) => {
    const text = q.trim()
    if (loading || text.length < 5) return
    setQuestion(text)
    setLoading(true)
    setError('')
    try {
      const json = await postJson('/api/beauty/regulation/ask', { question: text })
      if (json.success) onResult(json)
      else setError(json.message)
    } catch {
      setError('서버에 연결할 수 없습니다.')
    } finally {
      setLoading(false)
    }
  }

  const submitUpload = async (e) => {
    e.preventDefault()
    if (!upload.file || uploading) return
    setUploading(true)
    setUploadMsg({ ok: false, text: '' })
    try {
      const form = new FormData()
      form.append('file', upload.file)
      form.append('title', upload.title)
      form.append('source', upload.source)
      const res = await fetch('/api/beauty/regulation/docs', { method: 'POST', body: form })
      const json = await res.json().catch(() => ({ success: false, message: res.status === 413 ? '파일이 너무 커요.' : '올리지 못했어요.' }))
      setUploadMsg({ ok: json.success, text: json.message })
      if (json.success) {
        setUpload({ file: null, title: '', source: '' })
        e.target.reset()
        loadDocs()
      }
    } catch {
      setUploadMsg({ ok: false, text: '서버에 연결할 수 없습니다.' })
    } finally {
      setUploading(false)
    }
  }

  const remove = async (doc) => {
    if (!window.confirm(`"${doc.title}" 문서를 삭제할까요? 문단과 벡터도 같이 지워져요.`)) return
    const res = await fetch(`/api/beauty/regulation/docs/${doc.docId}`, { method: 'DELETE' })
    const json = await res.json().catch(() => ({}))
    if (!json.success) setUploadMsg({ ok: false, text: json.message || '삭제하지 못했어요.' })
    loadDocs()
  }

  const readyDocs = docs?.docs?.filter((d) => d.status === 'READY') ?? []

  return (
    <div className="bl-tab">
      {/* ===== 질문 ===== */}
      <section className="bl-card">
        <p className="bl-muted bl-intro">
          등록된 화장품 규제 문서에서 <b>뜻이 가까운 문단</b>을 찾아(벡터 검색) 그 문단만 근거로 답해요. 문장마다 붙은 번호를 누르면 원문 발췌를 볼 수 있어요.
        </p>
        {docs && docs.ready === false && (
          <p className="bl-error">규제 Q&A가 아직 설정되지 않았어요. (서버에 VOYAGE_API_KEY 필요)</p>
        )}
        <div className="bl-text">
          <textarea
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) ask() }}
            placeholder="예) 주름 개선 크림을 기능성으로 내려면 무엇을 준비해야 해?"
            maxLength={500}
            rows={3}
          />
        </div>
        <div className="bl-chips bl-examples">
          {EXAMPLES.map((q) => (
            <button key={q} type="button" className="bl-chip" onClick={() => ask(q)} disabled={!loggedIn || loading}>{q}</button>
          ))}
        </div>
        {error && <p className="bl-error" role="alert">{error}</p>}
        {loggedIn ? (
          <div className="bl-submit">
            <button type="button" className="bl-primary" onClick={() => ask()} disabled={loading || question.trim().length < 5 || readyDocs.length === 0}>
              {loading ? '문서를 찾아 답하는 중...' : '질문하기'}
            </button>
            <span className="bl-remaining">{readyDocs.length === 0 ? '아직 등록된 문서가 없어요' : `문서 ${readyDocs.length}개에서 찾아요`}{result?.remaining != null && ` · 오늘 ${result.remaining}번 남음`}</span>
          </div>
        ) : (
          <p className="bl-login"><Link to="/login">로그인</Link>하면 질문할 수 있어요.</p>
        )}
      </section>

      {/* ===== 답 + 출처 ===== */}
      {result && (
        <section className="bl-card">
          <span className="bl-eyebrow">Q. {result.question}</span>
          <AnswerText text={result.answer} />
          {result.sources?.length > 0 && (
            <>
              <h3 className="bl-card-title bl-gap">출처 (가까운 문단 {result.sources.length}개)</h3>
              <ol className="bl-sources">
                {result.sources.map((s) => (
                  <li key={s.no} id={`src-${s.no}`} className={s.cited ? 'bl-source-cited' : ''}>
                    <details open={s.cited}>
                      <summary>
                        <span className="bl-cite">{s.no}</span>
                        <strong>{s.title}</strong>
                        {s.page && <small>{s.page}쪽</small>}
                        <small className="bl-score">유사도 {Math.round(s.score * 100)}%</small>
                        {s.cited && <span className="bl-tag bl-tag-blue">답에 사용</span>}
                      </summary>
                      <p className="bl-source-text">{s.content}</p>
                      {s.source && <p className="bl-muted bl-small">출처: {s.source}</p>}
                    </details>
                  </li>
                ))}
              </ol>
            </>
          )}
        </section>
      )}

      {/* ===== 등록된 문서 ===== */}
      <section className="bl-card">
        <h3 className="bl-card-title">📚 등록된 문서 {docs?.docs ? `(${docs.docs.length})` : ''}</h3>
        {docs?.error && <p className="bl-error">{docs.error}</p>}
        {docs?.docs?.length === 0 && <p className="bl-muted">아직 등록된 문서가 없어요.</p>}
        <ul className="bl-docs">
          {docs?.docs?.map((d) => {
            const [label, tone] = STATUS[d.status] ?? [d.status, 'gray']
            return (
              <li key={d.docId}>
                <div>
                  <strong>{d.title}</strong>
                  <small>
                    {d.pageCount ? `${d.pageCount}쪽 · ` : ''}문단 {d.chunkCount}개 · {formatDay(d.insDay)}
                    {d.status !== 'READY' && d.message ? ` · ${d.message}` : ''}
                  </small>
                </div>
                <span className={`bl-tag bl-tag-${tone === 'red' ? 'amber' : tone}`}>{label}</span>
                {docs.admin && <button type="button" className="bl-link bl-danger" onClick={() => remove(d)}>삭제</button>}
              </li>
            )
          })}
        </ul>

        {docs?.admin && (
          <form className="bl-upload" onSubmit={submitUpload}>
            <h4>관리자: 문서 올리기 (PDF · TXT, 20MB까지)</h4>
            <input type="file" accept=".pdf,.txt,.md" onChange={(e) => setUpload((u) => ({ ...u, file: e.target.files?.[0] ?? null }))} />
            <input type="text" value={upload.title} onChange={(e) => setUpload((u) => ({ ...u, title: e.target.value }))} placeholder="문서 이름 (비우면 파일 이름)" maxLength={200} />
            <input type="text" value={upload.source} onChange={(e) => setUpload((u) => ({ ...u, source: e.target.value }))} placeholder="출처 (예: 국가법령정보센터, 2025.12 개정)" maxLength={500} />
            <button type="submit" className="bl-primary" disabled={!upload.file || uploading}>{uploading ? '올리는 중...' : '올리기'}</button>
            {uploadMsg.text && <p className={uploadMsg.ok ? 'bl-ok' : 'bl-error'}>{uploadMsg.text}</p>}
            <p className="bl-muted bl-small">추천 문서: {RECOMMENDED.join(' / ')}</p>
            <p className="bl-muted bl-small">스캔한 이미지 PDF는 글자를 읽을 수 없어요. 무료 Voyage 한도 때문에 큰 문서는 처리에 몇 분 걸려요.</p>
          </form>
        )}
      </section>
    </div>
  )
}

export default RegulationTab
