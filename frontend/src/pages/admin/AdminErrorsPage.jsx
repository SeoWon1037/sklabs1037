// ============================================
// 관리자: 🐞 화면 오류 (주소: /admin/errors)
// - 사용자 브라우저에서 난 오류를 모은 것 (같은 오류는 한 줄 + 횟수). 화면이 깨지면 여기에 남음
// - [고침]: 고쳤다고 표시 — 같은 오류가 또 생기면 자동으로 "새 오류"로 돌아옴 / [무시]: 다시 생겨도 조용히
// - 최근 이틀 안에 2번 이상 난 새 오류는 🔁 매일 개선(AI 자동 수정)의 후보가 됨
// - 백엔드 (관리자만): GET /api/admin/client-errors?status=, POST /api/admin/client-errors/{id}/status
// ============================================
import { useCallback, useEffect, useState } from 'react'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminInquiriesPage.css'

const TABS = [['OPEN', '새 오류', 'open'], ['DONE', '고침', 'done'], ['IGNORED', '무시', 'ignored']]
const KIND = { ERROR: '자바스크립트 오류', PROMISE: '처리 안 된 실패', RENDER: '화면 그리기 실패', CHUNK: '화면 코드 못 받음' }

const time = (ms) => {
  const d = new Date(ms)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
// 긴 브라우저 정보에서 기기 · 브라우저만
const device = (ua) => {
  if (!ua) return ''
  const os = /iPhone|iPad/.test(ua) ? 'iOS' : /Android/.test(ua) ? 'Android' : /Windows/.test(ua) ? 'Windows' : /Mac OS/.test(ua) ? 'Mac' : '기타'
  const browser = /KAKAOTALK/i.test(ua) ? '카카오톡' : /SamsungBrowser/.test(ua) ? '삼성 인터넷' : /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : /Firefox\//.test(ua) ? 'Firefox' : ''
  return `${os}${browser ? ` · ${browser}` : ''}`
}

function AdminErrorsPage() {

  const [status, setStatus] = useState('OPEN')
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(null)

  const load = useCallback(() => {
    fetch(`/api/admin/client-errors?status=${status}`)
      .then((r) => r.json())
      .then((j) => {
        if (!j.success) throw new Error(j.message)
        setData(j)
        setError('')
      })
      .catch((e) => setError(e.message || '불러오지 못했어요.'))
  }, [status])

  useEffect(() => { load() }, [load])

  const change = async (id, to) => {
    setBusy(id)
    await fetch(`/api/admin/client-errors/${id}/status`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status: to }) }).catch(() => {})
    setBusy(null)
    load()
  }

  return (
    <main className="admin-page">
      <AdminTabs />
      <div className="admin-head">
        <h1 className="admin-title">화면 오류</h1>
        <p className="admin-desc">
          사용자 브라우저에서 난 오류를 자동으로 모은 거예요. 같은 오류는 한 줄에 횟수만 올라가요.
          최근 이틀 안에 2번 이상 난 새 오류는 🔁 매일 개선에서 AI가 고칠 후보가 돼요.
        </p>
        <div className="iq-chips" role="tablist" style={{ marginTop: 10 }}>
          {TABS.map(([value, label, key]) => (
            <button key={value} type="button" role="tab" aria-selected={status === value} className={`iq-chip ${status === value ? 'iq-fix' : ''}`}
              onClick={() => { setData(null); setStatus(value) }}>
              {label} {data?.counts?.[key] ?? ''}
            </button>
          ))}
        </div>
      </div>

      {error && <p className="admin-empty admin-error">{error}</p>}
      {!error && data === null && <p className="admin-empty">불러오는 중...</p>}
      {data?.errors.length === 0 && <p className="admin-empty">{status === 'OPEN' ? '새 오류가 없어요. 👍' : '여기에는 아직 없어요.'}</p>}

      <ul className="iq-list">
        {data?.errors.map((e) => (
          <li key={e.id} className="iq-card">
            <div className="iq-head">
              <strong style={{ overflowWrap: 'anywhere' }}>{e.message}</strong>
              <span className="iq-meta">{time(e.firstAt)} 처음 · {time(e.lastAt)} 마지막</span>
            </div>
            <div className="iq-chips">
              <span className={`iq-chip ${e.kind === 'RENDER' ? 'iq-risk' : ''}`}>{KIND[e.kind] ?? e.kind}</span>
              <span className="iq-chip iq-mid">{e.cnt}번</span>
              {e.path && <span className="iq-chip">{e.path}</span>}
              {device(e.ua) && <span className="iq-chip">{device(e.ua)}</span>}
            </div>
            {e.stack && (
              <details className="iq-small">
                <summary>어디서 났는지 (스택)</summary>
                <pre style={{ margin: '8px 0 0', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', fontSize: 12, lineHeight: 1.5 }}>{e.stack}</pre>
              </details>
            )}
            <div className="iq-actions">
              <span className="iq-gap" />
              {e.status !== 'IGNORED' && <button type="button" className="iq-btn" disabled={busy === e.id} onClick={() => change(e.id, 'IGNORED')}>무시</button>}
              {e.status !== 'OPEN' && <button type="button" className="iq-btn" disabled={busy === e.id} onClick={() => change(e.id, 'OPEN')}>새 오류로 되돌리기</button>}
              {e.status !== 'DONE' && <button type="button" className="iq-btn iq-primary" disabled={busy === e.id} onClick={() => change(e.id, 'DONE')}>✅ 고침</button>}
            </div>
          </li>
        ))}
      </ul>
    </main>
  )
}

export default AdminErrorsPage
