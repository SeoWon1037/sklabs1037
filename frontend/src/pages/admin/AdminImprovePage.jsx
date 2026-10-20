// ============================================
// 관리자: 🔁 스스로 고치는 AI 비서 (주소: /admin/ai-improve)
// - 👎 받은 답변을 AI가 분석해 시스템 프롬프트 개선안을 만들고,
//   예전 질문으로 옛·새 프롬프트를 둘 다 돌려 AI 심판이 채점한 결과를 보여줌
// - 사람이 보고 적용 / 거절, 언제든 예전 버전·기본 프롬프트로 되돌리기
// - 백엔드 API (관리자만)
//   GET  /api/admin/ai-improve
//   POST /api/admin/ai-improve/run
//   POST /api/admin/ai-improve/{version}/activate   (0 = 기본 프롬프트)
//   POST /api/admin/ai-improve/{version}/reject
// ============================================
import { useCallback, useEffect, useState } from 'react'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminOpsPage.css'
import './AdminImprovePage.css'

const STATUS = {
  CANDIDATE: { label: '검토 대기', cls: 'imp-st-wait' },
  ACTIVE: { label: '적용 중', cls: 'imp-st-on' },
  RETIRED: { label: '예전 버전', cls: 'imp-st-off' },
  REJECTED: { label: '거절', cls: 'imp-st-no' },
}

const formatTime = (millis) => {
  if (!millis) return '-'
  const d = new Date(millis)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// 줄 단위 비교 (LCS) → [{ type: 'same' | 'add' | 'del', text }]
const lineDiff = (before, after) => {
  const a = (before || '').split('\n')
  const b = (after || '').split('\n')
  const dp = Array.from({ length: a.length + 1 }, () => new Array(b.length + 1).fill(0))
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }
  const out = []
  let i = 0
  let j = 0
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) { out.push({ type: 'same', text: a[i] }); i++; j++ }
    else if (dp[i + 1][j] >= dp[i][j + 1]) { out.push({ type: 'del', text: a[i] }); i++ }
    else { out.push({ type: 'add', text: b[j] }); j++ }
  }
  while (i < a.length) out.push({ type: 'del', text: a[i++] })
  while (j < b.length) out.push({ type: 'add', text: b[j++] })
  return out
}

function PromptDiff({ before, after }) {
  const [all, setAll] = useState(false)
  const lines = lineDiff(before, after)
  const changed = lines.filter((l) => l.type !== 'same').length
  const shown = all ? lines : lines.filter((l) => l.type !== 'same')
  return (
    <div className="imp-diff">
      <div className="imp-diff-head">
        <span>프롬프트 변경 {changed}줄</span>
        <button type="button" className="imp-link" onClick={() => setAll(!all)}>{all ? '바뀐 줄만' : '전체 보기'}</button>
      </div>
      <pre className="imp-diff-body">
        {shown.map((l, i) => (
          <span key={i} className={`imp-line imp-line-${l.type}`}>
            {l.type === 'add' ? '+ ' : l.type === 'del' ? '- ' : '  '}{l.text}{'\n'}
          </span>
        ))}
      </pre>
    </div>
  )
}

function EvalRow({ e }) {
  const [open, setOpen] = useState(false)
  if (e.error) return <li className="imp-eval imp-eval-error">{e.error}</li>
  const better = e.newScore - e.oldScore
  return (
    <li className="imp-eval">
      <button type="button" className="imp-eval-head" onClick={() => setOpen(!open)} aria-expanded={open}>
        <span className={`imp-kind ${e.kind === 'UP' ? 'imp-kind-up' : 'imp-kind-down'}`}>{e.kind === 'UP' ? '👍 유지 확인' : '👎 고칠 질문'}</span>
        <span className="imp-q">{e.question}</span>
        <span className="imp-scores">
          {e.oldScore} → <b className={better > 0 ? 'imp-better' : better < 0 ? 'imp-worse' : ''}>{e.newScore}</b>
        </span>
      </button>
      {open && (
        <div className="imp-eval-body">
          <p className="imp-reason">⚖️ {e.winner === 'NEW' ? '새 프롬프트 승' : e.winner === 'OLD' ? '옛 프롬프트 승' : '무승부'} · {e.reason}</p>
          <div className="imp-answers">
            <div>
              <h4>옛 답변 <small>{e.oldTools || '도구 없음'}</small></h4>
              <p>{e.oldAnswer}</p>
            </div>
            <div>
              <h4>새 답변 <small>{e.newTools || '도구 없음'}</small></h4>
              <p>{e.newAnswer}</p>
            </div>
          </div>
        </div>
      )}
    </li>
  )
}

function AdminImprovePage() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState('')

  const load = useCallback(() => {
    return fetch('/api/admin/ai-improve')
      .then((res) => res.json())
      .then((json) => {
        if (!json.success) throw new Error(json.message)
        setData(json)
        setError('')
      })
      .catch((e) => setError(e.message || '서버에 연결할 수 없습니다.'))
  }, [])

  useEffect(() => {
    let ignore = false
    fetch('/api/admin/ai-improve')
      .then((res) => res.json())
      .then((json) => {
        if (ignore) return
        if (!json.success) throw new Error(json.message)
        setData(json)
      })
      .catch((e) => { if (!ignore) setError(e.message || '서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [])

  // 실행 중이면 5초마다 다시
  const running = data?.running
  useEffect(() => {
    if (!running) return undefined
    const timer = setInterval(load, 5000)
    return () => clearInterval(timer)
  }, [running, load])

  const post = async (url, key, done) => {
    setBusy(key)
    try {
      const res = await fetch(url, { method: 'POST' })
      const json = await res.json()
      if (!json.success) throw new Error(json.message)
      setNotice(done(json))
      await load()
    } catch (e) {
      setNotice(e.message || '처리하지 못했어요.')
    } finally {
      setBusy('')
    }
  }

  const run = () => post('/api/admin/ai-improve/run', 'run', (j) => (j.started ? '개선을 시작했어요. 몇 분 걸려요.' : '이미 실행 중이에요.'))
  const activate = (v) => {
    if (!window.confirm(v === 0 ? '기본 프롬프트로 되돌릴까요?' : `v${v} 프롬프트를 AI 비서에 적용할까요?`)) return
    post(`/api/admin/ai-improve/${v}/activate`, `a${v}`, () => (v === 0 ? '기본 프롬프트로 되돌렸어요.' : `v${v}를 적용했어요. 1분 안에 모든 답변에 반영돼요.`))
  }
  const reject = (v) => post(`/api/admin/ai-improve/${v}/reject`, `r${v}`, () => `v${v} 개선안을 거절했어요.`)

  // 버전 → 프롬프트 내용 (diff 기준)
  const contentOf = (version) => (version === 0 ? data.basePrompt : data.prompts.find((p) => p.version === version)?.content)

  return (
    <main className="admin-page">
      <AdminTabs />

      <div className="ops-head">
        <div>
          <h1 className="admin-title">🔁 AI 비서 자기 개선</h1>
          <p className="admin-desc">
            AI 비서 답변에 달린 👎를 AI가 분석해서 시스템 프롬프트 개선안을 만들어요. 예전 질문으로 옛·새 프롬프트를 둘 다 돌려 보고
            AI 심판이 순서를 섞어 채점해요. 적용은 사람이 결정하고, 언제든 되돌릴 수 있어요.
          </p>
        </div>
        <div className="ops-head-actions">
          <button type="button" className="ops-btn" onClick={run} disabled={!data || running || busy === 'run'}>
            {running ? '개선 중...' : '지금 개선 실행'}
          </button>
        </div>
      </div>

      {notice && <p className="ops-notice" role="status">{notice}</p>}
      {error && <p className="admin-empty admin-error">{error}</p>}
      {!data && !error && <p className="admin-empty">불러오는 중...</p>}

      {data && (
        <>
          <section className="imp-now">
            <div>
              <span className="imp-now-label">지금 적용 중</span>
              <strong>{data.activeVersion === 0 ? '기본 프롬프트 (v0)' : `v${data.activeVersion}`}</strong>
            </div>
            <div>
              <span className="imp-now-label">지난 개선 뒤 새 👎</span>
              <strong>{data.newDownCount}개</strong>
              <small>{data.newDownCount >= data.minNewDown ? '개선할 수 있어요' : `${data.minNewDown}개부터 개선해요`}</small>
            </div>
            {data.activeVersion !== 0 && (
              <button type="button" className="ops-btn" onClick={() => activate(0)} disabled={busy === 'a0'}>기본으로 되돌리기</button>
            )}
          </section>

          {data.versionStats.length > 0 && (
            <section className="admin-box imp-stats">
              <h2 className="admin-box-title">버전별 성적 (최근 90일)</h2>
              <table>
                <thead><tr><th>버전</th><th>대화</th><th>평가</th><th>👍</th><th>👎</th><th>👎 비율</th></tr></thead>
                <tbody>
                  {data.versionStats.map((s) => (
                    <tr key={s.version} className={s.version === data.activeVersion ? 'imp-row-on' : ''}>
                      <td>v{s.version}{s.version === data.activeVersion && ' ●'}</td>
                      <td>{s.chatCount}</td>
                      <td>{s.ratedCount}</td>
                      <td>{s.upCount}</td>
                      <td>{s.downCount}</td>
                      <td>{s.ratedCount ? `${Math.round((s.downCount / s.ratedCount) * 100)}%` : '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}

          {data.prompts.length === 0 && (
            <p className="admin-empty">아직 개선안이 없어요. 👎가 {data.minNewDown}개 이상 모이면 매일 새벽 4시 10분에 만들어요.</p>
          )}

          {data.prompts.map((p) => {
            const st = STATUS[p.status] || STATUS.RETIRED
            const errorOnly = p.evals?.length === 1 && p.evals[0].error
            return (
              <article key={p.version} className="admin-box imp-card">
                <div className="imp-card-head">
                  <h2>v{p.version} <small>v{p.baseVersion} 개선 · 👎 {p.downCount}개 분석 · {formatTime(p.insAt)}</small></h2>
                  <span className={`imp-status ${st.cls}`}>{st.label}</span>
                  {p.recommend && <span className="imp-status imp-st-rec">추천</span>}
                </div>

                {p.scoreNew != null && (
                  <div className="imp-score">
                    <span>채점 평균</span>
                    <b>{p.scoreOld}</b>
                    <span>→</span>
                    <b className={Number(p.scoreNew) > Number(p.scoreOld) ? 'imp-better' : 'imp-worse'}>{p.scoreNew}</b>
                    <span className="imp-muted">/ 10</span>
                  </div>
                )}

                {p.patterns?.length > 0 && (
                  <>
                    <h3>찾은 문제</h3>
                    <ul className="imp-patterns">
                      {p.patterns.map((x, i) => (
                        <li key={i}>
                          <span className={`imp-fix ${x.fixable ? '' : 'imp-fix-no'}`}>{x.fixable ? '고침' : '프롬프트로 못 고침'}</span>
                          <b>{x.problem}</b>
                          <span className="imp-muted">{x.fix}{x.chatIds?.length ? ` (대화 #${x.chatIds.join(', #')})` : ''}</span>
                        </li>
                      ))}
                    </ul>
                  </>
                )}

                {p.changes?.length > 0 && (
                  <>
                    <h3>바꾼 점</h3>
                    <ul className="imp-changes">{p.changes.map((c, i) => <li key={i}>{c}</li>)}</ul>
                  </>
                )}

                {p.evals?.length > 0 && !errorOnly && (
                  <>
                    <h3>질문별 채점 (눌러서 답변 비교)</h3>
                    <ul className="imp-evals">{p.evals.map((e, i) => <EvalRow key={i} e={e} />)}</ul>
                  </>
                )}
                {errorOnly && <p className="admin-error">자동 거절: {p.evals[0].error}</p>}

                <PromptDiff before={contentOf(p.baseVersion)} after={p.content} />

                <div className="imp-actions">
                  {p.status === 'CANDIDATE' && (
                    <>
                      <button type="button" className="ops-btn" onClick={() => reject(p.version)} disabled={!!busy}>거절</button>
                      <button type="button" className="ops-btn imp-apply" onClick={() => activate(p.version)} disabled={!!busy}>이 버전 적용</button>
                    </>
                  )}
                  {(p.status === 'RETIRED' || (p.status === 'REJECTED' && p.scoreNew != null)) && (
                    <button type="button" className="ops-btn" onClick={() => activate(p.version)} disabled={!!busy}>이 버전으로 적용</button>
                  )}
                </div>
              </article>
            )
          })}
        </>
      )}
    </main>
  )
}

export default AdminImprovePage
