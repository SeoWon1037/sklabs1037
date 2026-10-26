// ============================================
// 관리자: 🔌 외부 연동 (주소: /admin/api) — 30초마다 새로고침
// - 우리 서버가 바깥 서비스(토스, Claude, Voyage, 뉴스 RSS, 환율, 카카오·네이버 로그인, 메일, 푸시, GitHub)를
//   부를 때마다 남긴 기록을 모아서 보여줌
// - 서비스 카드: 오늘 호출 수 · 실패율 · 평균 / 느린 5% 응답 시간 · 마지막 실패 이유
//   색: 최근 15분 기준 (빨강 = 절반 넘게 실패, 노랑 = 실패 있음, 초록 = 정상)
// - 최근 24시간 시간대별 호출(막대) + 실패(빨강), 기능별 표, 최근 실패 30건
// - 백엔드 API (관리자만): GET /api/admin/api-calls
// ============================================
import { useCallback, useEffect, useMemo, useState } from 'react'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminApiPage.css'

const POLL_MS = 30000

const num = (n) => Number(n ?? 0).toLocaleString('ko-KR')
const ms = (v) => (v == null ? '-' : v >= 1000 ? `${(v / 1000).toFixed(1)}초` : `${v}ms`)
const rate = (fails, calls) => (calls > 0 ? fails / calls : 0)
const pct = (r) => `${r < 0.001 && r > 0 ? '<0.1' : (r * 100).toFixed(r < 0.1 ? 1 : 0)}%`
// now = 받은 시각 (렌더 중 Date.now() 금지 → 화면이 그릴 때마다 값이 바뀌지 않게)
const ago = (at, now) => {
  if (!at || !now) return '-'
  const m = Math.floor((now - at) / 60000)
  if (m < 1) return '방금'
  if (m < 60) return `${m}분 전`
  if (m < 1440) return `${Math.floor(m / 60)}시간 전`
  return `${Math.floor(m / 1440)}일 전`
}
const clock = (at) => {
  const d = new Date(at)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// 최근 15분 숫자 → 상태
function tone(recent) {
  if (!recent || recent.calls === 0) return 'ap-idle'
  if (recent.fails * 2 > recent.calls || (recent.calls >= 3 && recent.fails === recent.calls)) return 'ap-bad'
  if (recent.fails > 0) return 'ap-warn'
  return 'ap-ok'
}
const TONE_TEXT = { 'ap-bad': '실패 많음', 'ap-warn': '실패 있음', 'ap-ok': '정상', 'ap-idle': '최근 호출 없음' }

// 최근 24시간 막대 (회색 = 성공, 빨강 = 실패)
function HourlyBars({ rows, service, now }) {

  const hours = useMemo(() => {
    const map = new Map()
    rows.filter((r) => !service || r.service === service).forEach((r) => {
      const h = map.get(r.hour) ?? { hour: r.hour, calls: 0, fails: 0 }
      h.calls += Number(r.calls)
      h.fails += Number(r.fails)
      map.set(r.hour, h)
    })
    // 빈 시간도 칸을 채움 (지금부터 23시간 전까지, 한국 시간)
    const out = []
    const kst = now + 9 * 3600 * 1000   // UTC → KST 글자 만들기용
    for (let i = 23; i >= 0; i--) {
      const d = new Date(kst - i * 3600 * 1000)
      const key = d.toISOString().slice(0, 13)
      out.push(map.get(key) ?? { hour: key, calls: 0, fails: 0 })
    }
    return out
  }, [rows, service, now])

  const max = Math.max(1, ...hours.map((h) => h.calls))
  const W = 480
  const H = 120
  const bw = W / hours.length

  return (
    <figure className="ap-chart">
      <div className="ap-plot">
        <span className="ap-max">{num(max)}</span>
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img" aria-label="최근 24시간 시간대별 호출 수와 실패 수">
          {[0.5].map((g) => <line key={g} x1="0" x2={W} y1={H * g} y2={H * g} className="ap-grid" />)}
          {hours.map((h, i) => {
            const ht = h.calls > 0 ? Math.max((h.calls / max) * H, 2) : 0   // 적어도 2px (있다는 게 보이게)
            const fh = (h.fails / max) * H
            return (
              <g key={h.hour}>
                <title>{`${Number(h.hour.slice(11, 13))}시 · ${num(h.calls)}번 · 실패 ${num(h.fails)}`}</title>
                <rect x={i * bw + 1.5} y={H - ht} width={bw - 3} height={ht} className="ap-bar" />
                {h.fails > 0 && <rect x={i * bw + 1.5} y={H - Math.max(fh, 2)} width={bw - 3} height={Math.max(fh, 2)} className="ap-bar-fail" />}
              </g>
            )
          })}
        </svg>
      </div>
      <div className="ap-axis">
        <span>{Number(hours[0].hour.slice(11, 13))}시</span>
        <span>{Number(hours[12].hour.slice(11, 13))}시</span>
        <span>지금</span>
      </div>
    </figure>
  )
}

function AdminApiPage() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [service, setService] = useState('')   // 시간대 그래프·기능 표를 한 서비스로 좁히기
  const [at, setAt] = useState(0)              // 받은 시각

  const load = useCallback(() => {
    fetch('/api/admin/api-calls')
      .then((r) => r.json())
      .then((j) => {
        if (!j.success) throw new Error(j.message)
        setData(j)
        setAt(Date.now())
        setError('')
      })
      .catch((e) => setError(e.message || '불러오지 못했어요.'))
  }, [])

  useEffect(() => {
    load()
    const t = setInterval(() => { if (document.visibilityState === 'visible') load() }, POLL_MS)
    return () => clearInterval(t)
  }, [load])

  const labels = data?.labels ?? {}
  const label = (s) => labels[s] ?? s
  const recent = useMemo(() => Object.fromEntries((data?.recent15 ?? []).map((r) => [r.service, r])), [data])
  const services = data?.services ?? []
  const actions = (data?.actions ?? []).filter((a) => !service || a.service === service)
  const totals = services.reduce((t, s) => ({ calls: t.calls + Number(s.calls), fails: t.fails + Number(s.fails) }), { calls: 0, fails: 0 })

  return (
    <main className="admin-page">
      <AdminTabs />
      <div className="admin-head">
        <h1 className="admin-title">외부 연동</h1>
        <p className="admin-desc">
          우리 서버가 바깥 서비스를 부를 때마다 남긴 기록 · 30초마다 새로고침 · 최근 15분 동안 실패가 많으면 메일·📲 경보
        </p>
      </div>

      {error && <p className="admin-empty admin-error">{error}</p>}

      {data && !data.saving && (
        <p className="ap-notice">이 서버는 기록을 저장하지 않아요 (로컬 PC). 운영 서버의 기록만 보여요.</p>
      )}
      {data?.dropped > 0 && (
        <p className="ap-notice">저장하지 못하고 버린 기록이 {num(data.dropped)}줄 있어요. (서버가 켜진 뒤로)</p>
      )}

      {data && (
        <>
          <div className="ap-summary">
            <div><span>오늘 호출</span><b>{num(totals.calls)}</b></div>
            <div><span>오늘 실패</span><b className={totals.fails > 0 ? 'ap-text-bad' : ''}>{num(totals.fails)}</b></div>
            <div><span>실패율</span><b>{pct(rate(totals.fails, totals.calls))}</b></div>
            <div><span>저장 대기</span><b>{num(data.pending)}</b></div>
          </div>

          {services.length === 0 && <p className="admin-empty">오늘은 아직 기록이 없어요.</p>}

          {/* ===== 서비스별 카드 ===== */}
          <div className="ap-cards">
            {services.map((s) => {
              const t = tone(recent[s.service])
              const selected = service === s.service
              return (
                <button
                  type="button"
                  key={s.service}
                  className={`ap-card ${t} ${selected ? 'ap-card-on' : ''}`}
                  onClick={() => setService(selected ? '' : s.service)}
                  aria-pressed={selected}
                >
                  <div className="ap-card-top">
                    <b>{label(s.service)}</b>
                    <span className="ap-dot">{TONE_TEXT[t]}</span>
                  </div>
                  <div className="ap-card-nums">
                    <span><small>호출</small>{num(s.calls)}</span>
                    <span><small>실패</small>{num(s.fails)}<em>{pct(rate(s.fails, s.calls))}</em></span>
                    <span><small>평균</small>{ms(s.avgMs)}</span>
                    <span><small>느린 5%</small>{ms(s.p95Ms)}</span>
                  </div>
                  <p className="ap-card-last">
                    {s.lastFailAt
                      ? <>마지막 실패 {ago(s.lastFailAt, at)} · {s.lastFailAction} · {s.lastFailError}</>
                      : <>마지막 호출 {ago(s.lastAt, at)} · 오늘 실패 없음</>}
                  </p>
                </button>
              )
            })}
          </div>

          {/* ===== 최근 24시간 ===== */}
          <h2 className="ap-h2">
            최근 24시간 {service && <small>{label(service)} · <button type="button" className="ap-link" onClick={() => setService('')}>전체 보기</button></small>}
          </h2>
          {at > 0 && <HourlyBars rows={data.hourly ?? []} service={service} now={at} />}

          {/* ===== 기능별 ===== */}
          {actions.length > 0 && (
            <section className="ap-table">
              <h2 className="ap-h2">기능별 (오늘, 실패 많은 순)</h2>
              <div className="ap-scroll">
                <table>
                  <thead>
                    <tr><th>서비스</th><th>기능</th><th className="ap-n">호출</th><th className="ap-n">실패</th><th className="ap-n">평균</th><th className="ap-n">가장 느림</th></tr>
                  </thead>
                  <tbody>
                    {actions.map((a) => (
                      <tr key={`${a.service}:${a.action}`}>
                        <td>{label(a.service)}</td>
                        <td><code>{a.action}</code></td>
                        <td className="ap-n">{num(a.calls)}</td>
                        <td className={`ap-n ${a.fails > 0 ? 'ap-text-bad' : ''}`}>{num(a.fails)}</td>
                        <td className="ap-n">{ms(a.avgMs)}</td>
                        <td className="ap-n">{ms(a.maxMs)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          {/* ===== 최근 실패 ===== */}
          <section className="ap-table">
            <h2 className="ap-h2">최근 실패 30건</h2>
            {(data.recentFails ?? []).length === 0
              ? <p className="admin-empty">실패 기록이 없어요.</p>
              : (
                <div className="ap-scroll">
                  <table>
                    <thead>
                      <tr><th>시각</th><th>서비스</th><th>기능</th><th className="ap-n">응답</th><th className="ap-n">걸린 시간</th><th>이유</th></tr>
                    </thead>
                    <tbody>
                      {data.recentFails.map((f) => (
                        <tr key={f.id}>
                          <td>{clock(f.at)}</td>
                          <td>{label(f.service)}</td>
                          <td><code>{f.action}</code></td>
                          <td className="ap-n">{f.status ?? '연결 실패'}</td>
                          <td className="ap-n">{ms(f.elapsedMs)}</td>
                          <td className="ap-reason">{f.error}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
          </section>

          <p className="ap-note">
            응답 번호 401 = 키·토큰 문제, 429 = 너무 자주 부름, 5xx = 상대 서버 문제, &quot;연결 실패&quot; = 시간 초과·네트워크 문제예요.
            키·토큰과 주소 뒤 조건(종목코드 등)은 기록하지 않아요. 기록은 30일 동안 보관해요.
          </p>
        </>
      )}
    </main>
  )
}

export default AdminApiPage
