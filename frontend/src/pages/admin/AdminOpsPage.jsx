// ============================================
// 관리자: 운영 상태 (주소: /admin, /admin/ops)
// - 작업(스케줄러)이 언제 돌았고 성공했는지, 데이터가 최신인지, 토스 API가 정상인지 한눈에
// - 실패하거나 정해진 시간에 안 돈 작업은 관리자 메일로도 알림 (백엔드 JobRunService / OpsWatchdogScheduler)
// - 백엔드 API (관리자만)
//   GET  /api/admin/ops                 운영 상태
//   POST /api/admin/ops/run { job }     작업 지금 실행
//   POST /api/admin/ops/test-mail       관리자 메일 테스트
// ============================================
import { useCallback, useEffect, useState } from 'react'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminOpsPage.css'

// 실행 중인 작업이 있으면 자주, 없으면 가끔 새로고침
const FAST_MS = 5_000
const SLOW_MS = 60_000

const STATUS = {
  SUCCESS: { label: '성공', cls: 'ok' },
  PARTIAL: { label: '일부 실패', cls: 'warn' },
  FAIL: { label: '실패', cls: 'bad' },
  RUNNING: { label: '실행 중', cls: 'run' },
  ABORTED: { label: '중단됨', cls: 'off' },
}

const TRIGGERS = { SCHEDULE: '정해진 시간', STARTUP: '서버 시작', MANUAL: '수동', WATCHDOG: '감시' }

// "3분 전", "어제 20:30"
const ago = (value, now) => {
  if (!value) return '-'
  const t = new Date(value)
  if (Number.isNaN(t.getTime())) return '-'
  const sec = Math.round((now - t.getTime()) / 1000)
  if (sec < 60) return '방금'
  if (sec < 3600) return `${Math.floor(sec / 60)}분 전`
  if (sec < 86400) return `${Math.floor(sec / 3600)}시간 전`
  return `${t.getMonth() + 1}/${t.getDate()} ${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`
}

const clock = (value) => {
  if (!value) return '-'
  const t = new Date(value)
  if (Number.isNaN(t.getTime())) return '-'
  return `${t.getMonth() + 1}/${t.getDate()} ${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}:${String(t.getSeconds()).padStart(2, '0')}`
}

const duration = (sec) => {
  if (sec == null) return '-'
  if (sec < 60) return `${sec}초`
  if (sec < 3600) return `${Math.floor(sec / 60)}분 ${sec % 60}초`
  return `${Math.floor(sec / 3600)}시간 ${Math.floor((sec % 3600) / 60)}분`
}

const num = (v) => (v == null ? '-' : Number(v).toLocaleString())

function StatusBadge({ status }) {
  const s = STATUS[status] ?? { label: status ?? '기록 없음', cls: 'off' }
  return <span className={`ops-badge ops-badge-${s.cls}`}>{s.label}</span>
}

function AdminOpsPage() {

  const [ops, setOps] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState('')            // 누른 버튼 (중복 클릭 방지)
  const [now, setNow] = useState(() => Date.now())

  const load = useCallback(() => {
    return fetch('/api/admin/ops')
      .then((res) => res.json())
      .then((data) => {
        if (data.success) {
          setOps(data)
          setError('')
        } else {
          setError(data.message)
        }
        setNow(Date.now())
      })
      .catch(() => setError('서버에 연결할 수 없습니다.'))
  }, [])

  // 처음 + 주기적으로 (실행 중인 작업이 있으면 5초, 없으면 1분)
  const runningCount = ops?.running?.length ?? 0
  useEffect(() => {
    load()
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') load()
    }, runningCount > 0 ? FAST_MS : SLOW_MS)
    return () => clearInterval(timer)
  }, [load, runningCount])

  const runJob = async (job) => {
    if (busy) return
    setBusy(job)
    setNotice('')
    try {
      const res = await fetch('/api/admin/ops/run', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ job }),
      })
      const data = await res.json()
      setNotice(data.message || (data.success ? '시작했어요.' : '실행하지 못했어요.'))
      await load()
    } catch {
      setNotice('서버에 연결할 수 없습니다.')
    } finally {
      setBusy('')
    }
  }

  const testMail = async () => {
    if (busy) return
    setBusy('mail')
    setNotice('')
    try {
      const res = await fetch('/api/admin/ops/test-mail', { method: 'POST' })
      const data = await res.json()
      setNotice(data.message || '요청했어요.')
    } catch {
      setNotice('서버에 연결할 수 없습니다.')
    } finally {
      setBusy('')
    }
  }

  if (error && !ops) {
    return (
      <main className="admin-page">
        <AdminTabs />
        <p className="admin-empty admin-error">{error}</p>
      </main>
    )
  }

  if (!ops) {
    return (
      <main className="admin-page">
        <AdminTabs />
        <p className="admin-empty">불러오는 중...</p>
      </main>
    )
  }

  const { jobs, runs, toss, data, running } = ops
  const failedJobs = jobs.filter((j) => ['FAIL', 'PARTIAL', 'ABORTED'].includes(j.last?.status))
  const tossOk = toss.configured && toss.lastOkAt && (!toss.lastErrorAt || new Date(toss.lastOkAt) > new Date(toss.lastErrorAt))
  const freshPct = data.stockCount ? Math.round(((data.stockCount - data.staleCount) / data.stockCount) * 100) : 0

  return (
    <main className="admin-page">
      <AdminTabs />

      <div className="ops-head">
        <div>
          <h1 className="admin-title">운영 상태</h1>
          <p className="admin-desc">
            작업이 제때 돌았는지, 데이터가 최신인지 확인해요. 실패하거나 정해진 시간에 안 돈 작업은 관리자 메일로도 알려줘요.
          </p>
        </div>
        <div className="ops-head-actions">
          <button type="button" className="ops-btn" onClick={load}>새로고침</button>
          <button type="button" className="ops-btn" onClick={testMail} disabled={busy === 'mail'}>
            {busy === 'mail' ? '보내는 중...' : '테스트 메일'}
          </button>
        </div>
      </div>

      {notice && <p className="ops-notice" role="status">{notice}</p>}

      {ops.backgroundJobs === false && (
        <p className="ops-notice" role="status">
          💻 이 서버(로컬 PC)에서는 스케줄러·서버 시작 작업이 꺼져 있어요. 운영 서버와 토스 토큰·메일이 겹치지 않게 하려는 거예요. "지금 실행"은 쓸 수 있어요.
        </p>
      )}

      {/* ===== 한 줄 요약 ===== */}
      <section className={`ops-summary ${failedJobs.length > 0 || !tossOk ? 'ops-summary-bad' : 'ops-summary-ok'}`}>
        {failedJobs.length === 0 && tossOk
          ? <>✅ 모든 작업이 정상이에요{running.length > 0 && ` · 지금 실행 중: ${running.length}개`}</>
          : <>⚠️ 확인이 필요해요: {[
              ...failedJobs.map((j) => `${j.label} ${STATUS[j.last.status]?.label}`),
              ...(!tossOk ? ['토스 API'] : []),
            ].join(', ')}</>}
      </section>

      {/* ===== 상태 카드 ===== */}
      <section className="ops-cards">
        <div className="ops-card">
          <span className="ops-card-title">토스 API</span>
          <strong className={tossOk ? 'ops-good' : 'ops-bad'}>{!toss.configured ? '키 없음' : tossOk ? '정상' : '오류'}</strong>
          <span>마지막 성공 {ago(toss.lastOkAt, now)}</span>
          {toss.lastErrorAt && <span className="ops-card-error" title={toss.lastError ?? ''}>마지막 오류 {ago(toss.lastErrorAt, now)}</span>}
          <span>토큰 발급 {ago(toss.tokenIssuedAt, now)}</span>
        </div>
        <div className="ops-card">
          <span className="ops-card-title">시세 데이터</span>
          <strong>{data.lastTradeDate ?? '-'}</strong>
          <span>최신 종목 {num(data.stockCount - data.staleCount)} / {num(data.stockCount)} ({freshPct}%)</span>
          <div className="ops-meter"><i style={{ width: `${freshPct}%` }} /></div>
          <span>종가 있는 종목 {num(data.pricedCount)}</span>
        </div>
        <div className="ops-card">
          <span className="ops-card-title">뉴스 · AI 요약</span>
          <strong>{ago(data.lastNewsAt, now)}</strong>
          <span>최근 기사 · 오늘 {num(data.newsToday)}건</span>
          <span>마지막 AI 요약 {ago(data.lastSummaryAt, now)}</span>
        </div>
        <div className="ops-card">
          <span className="ops-card-title">모의투자 · 알림</span>
          <strong>{num(data.pendingOrders)}건</strong>
          <span>미체결 지정가 주문</span>
          <span>켜진 가격 알림 {num(data.activeAlerts)}개</span>
        </div>
      </section>

      {/* ===== 작업별 마지막 실행 ===== */}
      <section className="admin-box ops-box">
        <h2 className="admin-box-title">작업</h2>
        <ul className="ops-jobs">
          {jobs.map((j) => {
            const isRunning = running.includes(j.job)
            const last = j.last
            return (
              <li key={j.job} className="ops-job">
                <div className="ops-job-name">
                  <strong>{j.label}</strong>
                  <small>{j.schedule}</small>
                </div>
                <div className="ops-job-status">
                  <StatusBadge status={isRunning ? 'RUNNING' : last?.status} />
                  <small>{last ? `${ago(last.startedAt, now)} · ${duration(last.durationSec)}` : '아직 기록 없음'}</small>
                </div>
                <p className="ops-job-message" title={last?.message ?? ''}>
                  {last?.totalCount != null && <b>{num(last.totalCount)}{last.failCount ? ` / 실패 ${num(last.failCount)}` : ''} · </b>}
                  {last?.message ?? ''}
                </p>
                <div className="ops-job-action">
                  {j.manual && (
                    <button
                      type="button"
                      className="ops-btn ops-btn-run"
                      onClick={() => runJob(j.job)}
                      disabled={isRunning || busy === j.job}
                    >
                      {isRunning ? '실행 중...' : '지금 실행'}
                    </button>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
        <p className="ops-hint">
          지금 실행: 놓친 시세 따라잡기는 1~2분, 시세 정기 수집은 10분 넘게 걸려요. AI 뉴스 요약은 Claude 비용이 들어요.
        </p>
      </section>

      {/* ===== 최근 실행 ===== */}
      <section className="admin-box ops-box">
        <h2 className="admin-box-title">최근 실행 {runs.length}건</h2>
        {runs.length === 0 && <p className="admin-empty">아직 기록이 없어요.</p>}
        <ul className="ops-runs">
          {runs.map((r) => (
            <li key={r.runId} className="ops-run">
              <details>
                <summary>
                  <span className="ops-run-time">{clock(r.startedAt)}</span>
                  <span className="ops-run-job">{ops.jobs.find((j) => j.job === r.jobName)?.label ?? r.jobName}</span>
                  <span className="ops-run-trigger">{TRIGGERS[r.triggerType] ?? r.triggerType}</span>
                  <StatusBadge status={r.status} />
                  <span className="ops-run-dur">{duration(r.durationSec)}</span>
                </summary>
                <div className="ops-run-detail">
                  <p>처리 {num(r.totalCount)} · 실패 {num(r.failCount)} · 끝 {clock(r.endedAt)}</p>
                  {r.message && <p className="ops-run-message">{r.message}</p>}
                </div>
              </details>
            </li>
          ))}
        </ul>
      </section>
    </main>
  )
}

export default AdminOpsPage
