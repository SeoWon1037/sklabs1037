// ============================================
// 관리자: 🧪 자가 점검 (주소: /admin/selfcheck)
// - 배포한 뒤 버튼 하나로 확인: DB에 빠진 SQL이 없는지 · 서버 키가 있는지 · 옛날 시세를 실제로 받아오는지 · 계산이 되는지
// - 토스증권을 실제로 불러보는 점검이 있어서 몇 초 걸릴 수 있음
// - 백엔드 (관리자만): GET /api/admin/selfcheck
// ============================================
import { useCallback, useEffect, useState } from 'react'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminInquiriesPage.css'

const LOOK = { OK: ['✅ 정상', 'iq-auto'], WARN: ['⚠️ 확인 필요', 'iq-mid'], FAIL: ['❌ 문제', 'iq-risk'] }

function AdminSelfCheckPage() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(true)

  const load = useCallback(() => {
    return fetch('/api/admin/selfcheck')
      .then((r) => r.json())
      .then((j) => {
        if (!j.success) throw new Error(j.message)
        setData(j)
        setError('')
      })
      .catch((e) => setError(e.message || '점검하지 못했어요.'))
      .finally(() => setBusy(false))
  }, [])

  useEffect(() => { load() }, [load])

  const again = () => {
    setBusy(true)
    load()
  }

  return (
    <main className="admin-page">
      <AdminTabs />
      <div className="admin-head">
        <h1 className="admin-title">자가 점검</h1>
        <p className="admin-desc">
          배포한 뒤 제대로 붙었는지 한 번에 확인해요. 빠뜨린 SQL 파일, 서버 키, 옛날 시세 받기, 계산을 실제로 해봐요.
          {data && ` 마지막 점검 ${new Date(data.at).toLocaleTimeString('ko-KR')}.`}
        </p>
        <button type="button" className="iq-btn" style={{ marginTop: 10 }} disabled={busy} onClick={again}>
          {busy ? '점검하는 중... (몇 초 걸려요)' : '🧪 다시 점검'}
        </button>
      </div>

      {error && <p className="admin-empty admin-error">{error}</p>}
      {data && <p className="iq-notice" role="status">{data.ok ? '모두 정상이에요. 👍' : '확인할 것이 있어요. 아래에서 ⚠️ · ❌ 줄을 봐주세요.'}</p>}

      <ul className="iq-list">
        {data?.checks.map((c) => (
          <li key={c.key} className="iq-card">
            <div className="iq-head">
              <strong>{c.name}</strong>
              <span className={`iq-chip ${LOOK[c.status]?.[1] ?? ''}`}>{LOOK[c.status]?.[0] ?? c.status}</span>
            </div>
            <p className="iq-body" style={{ overflowWrap: 'anywhere' }}>{c.detail}</p>
            {c.fix && <p className="iq-hint">👉 {c.fix}</p>}
          </li>
        ))}
      </ul>
    </main>
  )
}

export default AdminSelfCheckPage
