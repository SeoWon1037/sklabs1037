// ============================================
// 관리자: 🤖 AI 자동 수정 (주소: /admin/autofix)
// - 문의함에서 [AI로 고치기] 한 것들의 진행 상황
//   요청됨 → AI가 고치는 중(GitHub Actions) → 승인 대기(테스트 통과 + PR) / 고칠 곳 못 찾음 / 실패
// - 승인 대기: AI 요약 · 바뀐 파일 · PR 링크를 보고 [승인 · 배포] → 서버가 PR을 main에 합침 → 자동 배포 → 문의한 사람에게 답변
//              [거절] → PR·이슈 닫기
// - 백엔드 API (관리자만): GET /api/admin/autofix, POST /api/admin/autofix/{id}/approve | reject
// ============================================
import { useCallback, useEffect, useState } from 'react'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminInquiriesPage.css'

const STATUS = {
  REQUESTED: ['요청됨', ''], WORKING: ['AI가 고치는 중', 'iq-mid'], READY: ['승인 대기 · 테스트 통과', 'iq-auto'],
  NO_CHANGE: ['고칠 곳 못 찾음', 'iq-low'], FAILED: ['실패', 'iq-risk'], MERGED: ['승인 · 배포함', 'iq-auto'], REJECTED: ['거절함', 'iq-low'],
}
const time = (ms) => {
  if (!ms) return '-'
  const d = new Date(ms)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
const post = (url, body) =>
  fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body ?? {}) })
    .then((r) => r.json())
    .catch(() => ({ success: false, message: '서버에 연결하지 못했어요.' }))

function AdminAutofixPage() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(null)
  const [notice, setNotice] = useState('')

  const load = useCallback(() => {
    fetch('/api/admin/autofix')
      .then((r) => r.json())
      .then((j) => {
        if (!j.success) throw new Error(j.message)
        setData(j)
        setError('')
      })
      .catch((e) => setError(e.message || '불러오지 못했어요.'))
  }, [])

  useEffect(() => {
    load()
    const t = setInterval(() => { if (document.visibilityState === 'visible') load() }, 30 * 1000)   // 진행 중 상태 갱신
    return () => clearInterval(t)
  }, [load])

  const act = async (id, url, body, done, ask) => {
    if (ask && !window.confirm(ask)) return
    setBusy(id)
    const j = await post(url, body)
    setBusy(null)
    setNotice(j.success ? done : j.message)
    load()
  }

  const gh = (path) => `https://github.com/${data?.repo}/${path}`

  return (
    <main className="admin-page">
      <AdminTabs />
      <div className="admin-head">
        <h1 className="admin-title">AI 자동 수정</h1>
        <p className="admin-desc">
          문의함의 자동 수정 후보를 AI(Claude Code)가 GitHub Actions에서 고치고, 손대면 안 되는 파일 검사와 빌드·테스트를 통과하면 PR로 올려요.
          승인하면 main에 합쳐져 자동 배포돼요.
          {data && ` 오늘 ${data.today}/${data.dailyLimit}번 사용.`}
        </p>
      </div>

      {data && !data.configured && <p className="admin-empty admin-error">서버 .env 에 GITHUB_TOKEN 이 없어서 아직 쓸 수 없어요.</p>}
      {notice && <p className="iq-notice" role="status">{notice}</p>}
      {error && <p className="admin-empty admin-error">{error}</p>}
      {!error && data === null && <p className="admin-empty">불러오는 중...</p>}
      {data?.runs.length === 0 && <p className="admin-empty">아직 AI 수정 기록이 없어요. 문의함에서 🤖 자동 수정 후보에 [AI로 고치기]를 눌러보세요.</p>}

      <ul className="iq-list">
        {data?.runs.map((r) => (
          <li key={r.id} className="iq-card">
            <div className="iq-head">
              <strong>문의 #{r.inquiryId} · {r.inquirySummary || r.inquiryTitle}</strong>
              <span className="iq-meta">{time(r.insAt)} 요청 · {time(r.updAt)} 갱신</span>
            </div>
            <div className="iq-chips">
              <span className={`iq-chip ${STATUS[r.status]?.[1] ?? ''}`}>{STATUS[r.status]?.[0] ?? r.status}</span>
              {r.issueNo && <a className="iq-chip" href={gh(`issues/${r.issueNo}`)} target="_blank" rel="noopener noreferrer">이슈 #{r.issueNo}</a>}
              {r.prNo && <a className="iq-chip iq-fix" href={gh(`pull/${r.prNo}/files`)} target="_blank" rel="noopener noreferrer">PR #{r.prNo} 바뀐 코드 보기</a>}
              {r.runUrl && <a className="iq-chip" href={r.runUrl} target="_blank" rel="noopener noreferrer">Actions 로그</a>}
            </div>

            {r.summary && <p className="iq-body">{r.summary}</p>}
            {r.files && <p className="iq-small">바뀐 파일: {r.files.split('\n').join(' · ')}</p>}
            {r.error && <p className="iq-hint iq-err">{r.error}</p>}

            {['READY', 'FAILED', 'NO_CHANGE'].includes(r.status) && (
              <div className="iq-actions">
                <span className="iq-gap" />
                <button type="button" className="iq-btn" disabled={busy === r.id}
                  onClick={() => act(r.id, `/api/admin/autofix/${r.id}/reject`, null, '닫았어요.')}>
                  {r.status === 'READY' ? '거절 (PR 닫기)' : '닫기'}
                </button>
                {r.status === 'READY' && (
                  <button type="button" className="iq-btn iq-primary" disabled={busy === r.id}
                    onClick={() => act(r.id, `/api/admin/autofix/${r.id}/approve`, null, '승인했어요. main에 합쳐져서 배포가 시작돼요.', `PR #${r.prNo}을 main에 합치고 배포할까요?`)}>
                    ✅ 승인 · 배포
                  </button>
                )}
              </div>
            )}
          </li>
        ))}
      </ul>
    </main>
  )
}

export default AdminAutofixPage
