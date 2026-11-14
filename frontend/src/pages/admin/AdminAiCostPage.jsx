// ============================================
// 관리자: 💸 AI 비용 (주소: /admin/ai-cost) — 30초마다 새로고침
// - 서버가 Claude를 부를 때마다 응답의 토큰 수로 계산한 비용 (추정값, 정확한 청구는 Anthropic 콘솔)
// - 오늘 쓴 돈 / 하루 한도 → 한도에 닿으면 서버가 AI 기능을 자동으로 멈춤 (내일 0시에 다시 열림)
//   80% · 100%에 관리자 메일 + 📲
// - 하루 한도 바꾸기 · 🛑 긴급 정지 (모든 AI 기능을 바로 멈춤)
// - 최근 30일 날짜별 막대 (빨강 = 한도 넘은 날) · 오늘 기능별 표
// - 백엔드 API (관리자만): GET /api/admin/ai-cost, POST /api/admin/ai-cost { dailyUsd, paused }
// ============================================
import { useCallback, useEffect, useMemo, useState } from 'react'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminApiPage.css'
import './AdminInquiriesPage.css'
import './AdminAiCostPage.css'

const POLL_MS = 30000
const usd = (v, d = 2) => `$${Number(v ?? 0).toFixed(d)}`
// 작은 금액은 센트 단위까지 보이게
const usdSmall = (v) => (Number(v ?? 0) < 0.01 ? usd(v, 4) : usd(v, 3))
const num = (n) => Number(n ?? 0).toLocaleString('ko-KR')

// 최근 30일 막대 (빈 날도 칸을 채움)
function DayBars({ rows, limit, today }) {

  const days = useMemo(() => {
    const map = new Map(rows.map((r) => [r.day, r]))
    const out = []
    const base = new Date(`${today}T00:00:00Z`)
    for (let i = 29; i >= 0; i--) {
      const key = new Date(base.getTime() - i * 86400000).toISOString().slice(0, 10)
      out.push(map.get(key) ?? { day: key, calls: 0, cost: 0 })
    }
    return out
  }, [rows, today])

  const max = Math.max(limit * 1.1, ...days.map((d) => Number(d.cost)), 0.01)
  const W = 480
  const H = 120
  const bw = W / days.length
  const ly = H - (limit / max) * H

  return (
    <figure className="ap-chart">
      <div className="ap-plot">
        <span className="ap-max">{usd(max)}</span>
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img" aria-label="최근 30일 날짜별 AI 비용과 하루 한도">
          {days.map((d, i) => {
            const c = Number(d.cost)
            const ht = c > 0 ? Math.max((c / max) * H, 2) : 0
            return (
              <g key={d.day}>
                <title>{`${d.day.slice(5).replace('-', '/')} · ${usdSmall(c)} · ${num(d.calls)}번`}</title>
                <rect x={i * bw + 1.5} y={H - ht} width={bw - 3} height={ht} className={c >= limit ? 'ac-bar-over' : 'ap-bar'} />
              </g>
            )
          })}
          <line x1="0" x2={W} y1={ly} y2={ly} className="ac-limit" />
        </svg>
      </div>
      <div className="ap-axis">
        <span>{days[0].day.slice(5).replace('-', '/')}</span>
        <span>{days[15].day.slice(5).replace('-', '/')}</span>
        <span>오늘</span>
      </div>
      <p className="ac-legend">빨간 점선 = 지금 하루 한도 {usd(limit)} · 빨간 막대 = 한도에 닿은 날</p>
    </figure>
  )
}

function AdminAiCostPage() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [limit, setLimit] = useState('')
  const [busy, setBusy] = useState(false)
  const [today, setToday] = useState('')   // 받은 시각의 한국 날짜 (렌더 중 Date.now() 금지)

  const apply = useCallback((j) => {
    setData(j)
    setToday(new Date(Date.now() + 9 * 3600000).toISOString().slice(0, 10))
    setLimit((prev) => (prev === '' ? String(j.dailyUsd) : prev))
    setError('')
  }, [])

  const load = useCallback(() => {
    fetch('/api/admin/ai-cost')
      .then((r) => r.json())
      .then((j) => {
        if (!j.success) throw new Error(j.message)
        apply(j)
      })
      .catch((e) => setError(e.message || '불러오지 못했어요.'))
  }, [apply])

  useEffect(() => {
    load()
    const t = setInterval(() => { if (document.visibilityState === 'visible') load() }, POLL_MS)
    return () => clearInterval(t)
  }, [load])

  const save = async (paused, ask) => {
    if (ask && !window.confirm(ask)) return
    setBusy(true)
    const j = await fetch('/api/admin/ai-cost', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ dailyUsd: Number(limit), paused }),
    }).then((r) => r.json()).catch(() => ({ success: false, message: '서버에 연결하지 못했어요.' }))
    setBusy(false)
    if (!j.success) return setNotice(j.message)
    setLimit(String(j.dailyUsd))
    apply(j)
    setNotice(paused ? '🛑 모든 AI 기능을 멈췄어요.' : '저장했어요.')
  }

  const ratio = data ? data.todayCost / data.dailyUsd : 0
  const tone = data?.blocked ? 'ac-stop' : ratio >= 0.8 ? 'ac-warn' : ''
  const calls = (data?.features ?? []).reduce((s, f) => s + Number(f.calls), 0)
  const changed = data && limit !== '' && Number(limit) !== data.dailyUsd

  return (
    <main className="admin-page">
      <AdminTabs />
      <div className="admin-head">
        <h1 className="admin-title">AI 비용</h1>
        <p className="admin-desc">
          서버가 Claude를 부를 때마다 토큰 수로 계산한 비용이에요. 오늘 쓴 돈이 하루 한도에 닿으면 AI 기능을 자동으로 멈추고, 80% · 100%에 메일·📲으로 알려줘요.
        </p>
      </div>

      {error && <p className="admin-empty admin-error">{error}</p>}
      {notice && <p className="iq-notice" role="status">{notice}</p>}
      {!error && !data && <p className="admin-empty">불러오는 중...</p>}

      {data && (
        <>
          <section className={`ac-status ${tone}`} aria-live="polite">
            <div className="ac-status-main">
              <strong>
                {data.paused ? '🛑 긴급 정지 중 · 모든 AI 기능이 멈춰 있어요'
                  : data.blocked ? '⏸ 오늘 한도에 닿아서 AI 기능을 멈췄어요 (내일 0시에 다시 열림)'
                    : ratio >= 0.8 ? '⚠️ 오늘 한도의 80%를 넘었어요' : '✅ 정상 · AI 기능이 돌고 있어요'}
              </strong>
              <div className="ac-meter" role="meter" aria-valuemin={0} aria-valuemax={data.dailyUsd} aria-valuenow={data.todayCost} aria-label="오늘 쓴 비용">
                <span style={{ width: `${Math.min(100, ratio * 100)}%` }} />
              </div>
              <p>오늘 한도의 {Math.round(ratio * 100)}% 사용</p>
            </div>
            <div className="ac-big">{usdSmall(data.todayCost)} <small>/ {usd(data.dailyUsd)}</small></div>
          </section>

          <div className="ap-summary">
            <div><span>오늘 호출</span><b>{num(calls)}</b></div>
            <div><span>오늘 비용</span><b>{usdSmall(data.todayCost)}</b></div>
            <div><span>이번 달 비용</span><b>{usdSmall(data.month?.cost)}</b></div>
            <div><span>이번 달 호출</span><b>{num(data.month?.calls)}</b></div>
          </div>

          <div className="ac-controls">
            <label className="ac-field">
              하루 한도 (달러)
              <span className="ac-input">
                $<input type="number" inputMode="decimal" min="0.1" max="100" step="0.5" value={limit} onChange={(e) => setLimit(e.target.value)} />
              </span>
            </label>
            <button type="button" className="iq-btn iq-primary" disabled={busy || !changed} onClick={() => save(data.paused)}>
              한도 저장
            </button>
            <span className="ac-gap" />
            {data.paused ? (
              <button type="button" className="iq-btn iq-primary" disabled={busy} onClick={() => save(false)}>
                ▶️ AI 다시 켜기
              </button>
            ) : (
              <button type="button" className="iq-btn ac-stop-btn" disabled={busy}
                onClick={() => save(true, '모든 AI 기능(AI 챗봇, 뷰티 랩, 뉴스 요약, 문의 분류, 매일 개선 …)을 바로 멈출까요?')}>
                🛑 긴급 정지
              </button>
            )}
          </div>

          <h2 className="ap-h2">최근 30일</h2>
          {today && <DayBars rows={data.days ?? []} limit={data.dailyUsd} today={today} />}

          <section className="ap-table">
            <h2 className="ap-h2">오늘 기능별 (비싼 순)</h2>
            {(data.features ?? []).length === 0
              ? <p className="admin-empty">오늘은 아직 AI를 부른 기록이 없어요.</p>
              : (
                <div className="ap-scroll">
                  <table>
                    <thead>
                      <tr><th>기능</th><th className="ap-n">비용</th><th className="ap-n">호출</th><th className="ap-n">실패</th><th className="ap-n">입력 토큰</th><th className="ap-n">출력 토큰</th></tr>
                    </thead>
                    <tbody>
                      {data.features.map((f) => (
                        <tr key={f.feature}>
                          <td><code>{f.feature}</code></td>
                          <td className="ap-n">{usdSmall(f.cost)}</td>
                          <td className="ap-n">{num(f.calls)}</td>
                          <td className={`ap-n ${f.fails > 0 ? 'ap-text-bad' : ''}`}>{num(f.fails)}</td>
                          <td className="ap-n">{num(Number(f.inTokens) + Number(f.cacheRead))}</td>
                          <td className="ap-n">{num(f.outTokens)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
          </section>

          <p className="ap-note">
            가격 기준 (1M 토큰당): 입력 {usd(data.prices?.input)} · 출력 {usd(data.prices?.output)} · 캐시 읽기 {usd(data.prices?.cacheRead)} · 캐시 쓰기 {usd(data.prices?.cacheWrite, 3)},
            한 번에 10만 토큰 넘게 보내면 5배. 추정값이라 실제 청구와 조금 다를 수 있어요.
            GitHub AI 자동 수정(Claude Code)은 GitHub 쪽 키로 돌아서 여기에 안 잡혀요 (그쪽은 1번에 $3 한도).
          </p>
        </>
      )}
    </main>
  )
}

export default AdminAiCostPage
