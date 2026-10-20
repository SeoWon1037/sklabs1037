// ============================================
// 관리자: 🤖 AI QA 봇 (주소: /admin/qa)
// - GitHub Actions가 매일 08:30 + 배포 직후 진짜 브라우저로 주요 화면을 휴대폰·PC 크기로 열고 눌러본 결과
// - 규칙(페이지 오류·실패한 요청·흐름 실패·가로 넘침·느림) + Claude가 캡처를 보고 찾은 화면 문제
// - 백엔드 API (관리자만)
//   GET /api/admin/qa          최근 점검 목록
//   GET /api/admin/qa/{runId}  점검 하나 + 측정값 + 캡처
// ============================================
import { useCallback, useEffect, useState } from 'react'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminOpsPage.css'
import './AdminQaPage.css'

const STATUS = {
  PASS: { label: '통과', cls: 'qa-pass', icon: '✅' },
  WARN: { label: '확인 필요', cls: 'qa-warn', icon: '⚠️' },
  FAIL: { label: '문제 있음', cls: 'qa-fail', icon: '❌' },
  ANALYZING: { label: '분석 중', cls: 'qa-run', icon: '⏳' },
}

const SEVERITY = {
  HIGH: { label: '심각', cls: 'qa-sev-high' },
  MEDIUM: { label: '주의', cls: 'qa-sev-mid' },
  LOW: { label: '참고', cls: 'qa-sev-low' },
}

const SOURCE = { SCHEDULE: '매일 점검', DEPLOY: '배포 직후', MANUAL: '수동' }

const formatTime = (millis) => {
  if (!millis) return '-'
  const d = new Date(millis)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function AdminQaPage() {

  const [runs, setRuns] = useState(null)
  const [configured, setConfigured] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(null)   // 점검 상세
  const [zoom, setZoom] = useState(null)           // 크게 볼 캡처 key

  const openRun = useCallback((runId) => {
    fetch(`/api/admin/qa/${runId}`)
      .then((res) => res.json())
      .then((json) => { if (json.success) setSelected(json) })
      .catch(() => {})
  }, [])

  useEffect(() => {
    let ignore = false
    fetch('/api/admin/qa')
      .then((res) => res.json())
      .then((json) => {
        if (ignore) return
        if (!json.success) throw new Error(json.message)
        setRuns(json.runs)
        setConfigured(json.configured)
        if (json.runs.length) openRun(json.runs[0].runId)
      })
      .catch((e) => { if (!ignore) setError(e.message || '서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [openRun])

  // 화면별로 캡처·측정값 묶기 (home-mobile, home-pc → home)
  const pages = []
  for (const c of selected?.checks || []) {
    const base = c.key.replace(/-(mobile|pc)$/, '')
    let p = pages.find((x) => x.base === base)
    if (!p) { p = { base, name: c.name, path: c.page, items: [] }; pages.push(p) }
    p.items.push(c)
  }

  const findingsOf = (key) => (selected?.findings || []).filter((f) => f.key === key)

  return (
    <main className="admin-page">
      <AdminTabs />

      <div className="admin-head">
        <h1 className="admin-title">🤖 AI QA 봇</h1>
        <p className="admin-desc">
          매일 아침 8시 30분과 배포 직후, 진짜 브라우저가 주요 화면을 휴대폰·PC 크기로 열고 눌러봐요. 규칙으로 오류를 잡고,
          Claude가 캡처를 보고 깨진 화면을 찾아요. 문제가 있으면 관리자 메일로도 알려줘요.
        </p>
      </div>

      {!configured && (
        <p className="ops-notice" role="status">
          아직 QA_TOKEN이 없어서 점검 결과를 받지 않아요. 서버 .env와 GitHub → Settings → Secrets → Actions에 같은 QA_TOKEN 값을 넣어주세요.
        </p>
      )}

      {error && <p className="admin-empty admin-error">{error}</p>}
      {!runs && !error && <p className="admin-empty">불러오는 중...</p>}
      {runs?.length === 0 && <p className="admin-empty">아직 점검 기록이 없어요. GitHub Actions → QA Bot → Run workflow 로 바로 돌려볼 수 있어요.</p>}

      {runs?.length > 0 && (
        <div className="qa-layout">
          <ul className="qa-runs" aria-label="점검 기록">
            {runs.map((r) => {
              const st = STATUS[r.status] || STATUS.ANALYZING
              return (
                <li key={r.runId}>
                  <button type="button" className={`qa-run-item ${selected?.runId === r.runId ? 'qa-run-on' : ''}`} onClick={() => openRun(r.runId)}>
                    <span className={`qa-badge ${st.cls}`}>{st.icon} {st.label}</span>
                    <span className="qa-run-meta">#{r.runId} · {SOURCE[r.source] || r.source} · {formatTime(r.insAt)}</span>
                    <span className="qa-run-sum">{r.findingCount ? `문제 ${r.findingCount}개` : '문제 없음'}{r.commit ? ` · ${r.commit.slice(0, 7)}` : ''}</span>
                  </button>
                </li>
              )
            })}
          </ul>

          {selected && (
            <section className="qa-detail">
              <div className="qa-detail-head">
                <span className={`qa-badge ${(STATUS[selected.status] || STATUS.ANALYZING).cls}`}>
                  {(STATUS[selected.status] || STATUS.ANALYZING).icon} {(STATUS[selected.status] || STATUS.ANALYZING).label}
                </span>
                <strong>점검 #{selected.runId}</strong>
                <span className="qa-muted">{SOURCE[selected.source] || selected.source} · {formatTime(selected.insAt)}</span>
              </div>
              {selected.summary && <p className="qa-summary">🤖 {selected.summary}</p>}

              {selected.findings.length > 0 && (
                <ul className="qa-findings">
                  {selected.findings.map((f, i) => (
                    <li key={i}>
                      <span className={`qa-sev ${(SEVERITY[f.severity] || SEVERITY.LOW).cls}`}>{(SEVERITY[f.severity] || SEVERITY.LOW).label}</span>
                      <span className="qa-f-body">
                        <b>{f.title}</b> <span className="qa-muted">{f.key} · {f.by === 'AI' ? 'AI 화면 판정' : '규칙'}</span>
                        {f.detail && <span className="qa-f-detail">{f.detail}</span>}
                      </span>
                    </li>
                  ))}
                </ul>
              )}

              <div className="qa-pages">
                {pages.map((p) => (
                  <article key={p.base} className="qa-page">
                    <h3>{p.name} <span className="qa-muted">{p.path}</span></h3>
                    <div className="qa-shots">
                      {p.items.map((c) => {
                        const shot = selected.shots?.[c.key]
                        const issues = findingsOf(c.key)
                        return (
                          <figure key={c.key} className={`qa-shot qa-shot-${c.viewport}`}>
                            {shot ? (
                              <button type="button" className="qa-shot-btn" onClick={() => setZoom(c.key)} aria-label={`${c.key} 캡처 크게 보기`}>
                                <img src={`data:image/jpeg;base64,${shot}`} alt={`${p.name} ${c.viewport === 'mobile' ? '휴대폰' : 'PC'} 화면`} loading="lazy" />
                              </button>
                            ) : <div className="qa-noshot">캡처 없음</div>}
                            <figcaption>
                              <b>{c.viewport === 'mobile' ? '📱 휴대폰' : '🖥 PC'}</b> {c.loadMs != null ? `${(c.loadMs / 1000).toFixed(1)}초` : '-'}
                              {c.flow && <span className={c.flow.ok ? 'qa-ok' : 'qa-bad'}> · {c.flow.ok ? '흐름 성공' : '흐름 실패'}</span>}
                              {issues.length > 0 && <span className="qa-bad"> · 문제 {issues.length}</span>}
                            </figcaption>
                          </figure>
                        )
                      })}
                    </div>
                  </article>
                ))}
              </div>
            </section>
          )}
        </div>
      )}

      {zoom && selected?.shots?.[zoom] && (
        <div className="qa-zoom" role="dialog" aria-label="캡처 크게 보기" onClick={() => setZoom(null)}>
          <img src={`data:image/jpeg;base64,${selected.shots[zoom]}`} alt={zoom} />
          <span className="qa-zoom-close">닫기 ✕</span>
        </div>
      )}
    </main>
  )
}

export default AdminQaPage
