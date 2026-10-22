// ============================================
// 관리자: 🖥 서버 상태 (주소: /admin/server) — 5초마다 실시간
// - 운영 서버(EC2 · Docker 안 Spring Boot): CPU(서버 전체 / 우리 앱) · 메모리 · Java 힙 · 디스크 · 스레드 · 실시간 접속
// - DB 서버(RDS PostgreSQL): 응답 속도 · 연결 수 · 초당 트랜잭션 · 캐시 적중률 · 가장 오래 도는 쿼리 · 크기 · 큰 테이블
// - 최근 1시간 선 그래프 (서버가 5초마다 재서 메모리에 둔 값, 배포하면 처음부터)
// - 70% 넘으면 노랑, 90% 넘으면 빨강 / 오래 높으면 서버가 메일 + 📲 경보
// - 백엔드 API (관리자만): GET /api/admin/server
// ============================================
import { useCallback, useEffect, useState } from 'react'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminServerPage.css'

const POLL_MS = 5000

const gb = (b) => (b == null || b < 0 ? '-' : b >= 1024 ** 3 ? `${(b / 1024 ** 3).toFixed(1)}GB` : `${Math.round(b / 1024 ** 2)}MB`)
const pct = (v) => (v == null || v < 0 ? '-' : `${Math.round(v * 100)}%`)
const ratio = (u, t) => (u == null || t == null || u < 0 || t <= 0 ? -1 : u / t)
const level = (v) => (v < 0 ? '' : v >= 0.9 ? 'sv-bad' : v >= 0.7 ? 'sv-warn' : 'sv-ok')
const since = (ms) => {
  if (!ms) return '-'
  const m = Math.floor((Date.now() - ms) / 60000)
  if (m < 60) return `${m}분`
  if (m < 1440) return `${Math.floor(m / 60)}시간 ${m % 60}분`
  return `${Math.floor(m / 1440)}일 ${Math.floor((m % 1440) / 60)}시간`
}
const clock = (ms) => {
  const d = new Date(ms)
  return [d.getHours(), d.getMinutes(), d.getSeconds()].map((n) => String(n).padStart(2, '0')).join(':')
}

// tone: 색을 직접 정할 때 (캐시 적중률처럼 "높을수록 좋은" 값)
function Meter({ label, value, text, sub, tone }) {
  const t = tone ?? level(value)
  return (
    <div className="sv-meter">
      <div className="sv-meter-top">
        <span>{label}</span>
        <b className={t}>{text}</b>
      </div>
      <div className="sv-bar"><i className={t} style={{ width: `${Math.max(0, Math.min(1, value)) * 100}%` }} /></div>
      {sub && <small>{sub}</small>}
    </div>
  )
}

function Stat({ label, value, tone }) {
  return (
    <div className="sv-stat">
      <span>{label}</span>
      <b className={tone || ''}>{value}</b>
    </div>
  )
}

// 최근 1시간 선 그래프 (series: [{ name, values, color }], 0~max)
function Chart({ title, series, max, unit, fmt }) {
  const W = 320
  const H = 110
  const n = Math.max(...series.map((s) => s.values.length), 2)
  const top = max ?? Math.max(1, ...series.flatMap((s) => s.values.filter((v) => v >= 0))) * 1.2
  const x = (i) => (i / (n - 1)) * W
  const y = (v) => H - (Math.min(v, top) / top) * H
  const last = (s) => [...s.values].reverse().find((v) => v >= 0)

  return (
    <figure className="sv-chart">
      <figcaption>
        <b>{title}</b>
        <span>
          {series.map((s) => (
            <em key={s.name} style={{ '--c': s.color }}>{s.name} {last(s) == null ? '-' : (fmt ? fmt(last(s)) : `${last(s)}${unit}`)}</em>
          ))}
        </span>
      </figcaption>
      <div className="sv-plot">
      <span className="sv-max">{fmt ? fmt(top) : `${Math.round(top)}${unit}`}</span>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img" aria-label={`${title} 최근 1시간`}>
        {[0.25, 0.5, 0.75].map((g) => <line key={g} x1="0" x2={W} y1={H * g} y2={H * g} className="sv-grid" />)}
        {series.map((s) => {
          const pts = s.values.map((v, i) => (v >= 0 ? `${x(i + n - s.values.length).toFixed(1)},${y(v).toFixed(1)}` : null)).filter(Boolean).join(' ')
          return pts && <polyline key={s.name} points={pts} fill="none" stroke={s.color} strokeWidth="1.8" vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
        })}
      </svg>
      </div>
      <div className="sv-axis"><span>{n * 5 >= 3600 ? '1시간 전' : `${Math.round((n * 5) / 60)}분 전`}</span><span>지금</span></div>
    </figure>
  )
}

function AdminServerPage() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [at, setAt] = useState(null)

  const load = useCallback(() => {
    fetch('/api/admin/server')
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

  const s = data?.now
  const h = data?.history ?? []
  const app = data?.app
  const db = data?.db ?? {}
  const fresh = at && s && at - s.t < 20000   // 받은 시각 기준 (렌더 중 Date.now() 금지)

  return (
    <main className="admin-page">
      <AdminTabs />
      <div className="admin-head">
        <h1 className="admin-title">서버 상태</h1>
        <p className="admin-desc">
          <span className={`sv-live ${fresh ? '' : 'sv-live-off'}`}>●</span> {fresh ? `실시간 · 5초마다 · ${clock(s.t)}` : '값을 기다리는 중…'}
          {' '}· 70% 넘으면 노랑, 90% 넘으면 빨강 · 오래 높으면 메일·📲 경보
        </p>
      </div>

      {error && <p className="admin-empty admin-error">{error}</p>}
      {!error && data && !s && <p className="admin-empty">서버가 막 켜졌어요. 10초쯤 뒤에 첫 값이 나와요.</p>}

      {s && (
        <>
          <div className="sv-grid2">
            {/* ===== 운영 서버 ===== */}
            <section className="sv-card">
              <h2>🖥 운영 서버 <small>EC2 · Docker · Spring Boot</small></h2>
              <Meter label="CPU (서버 전체)" value={s.cpu} text={pct(s.cpu)} sub={`우리 앱 ${pct(s.appCpu)} · 코어 ${app.cpus}개`} />
              <Meter label="메모리" value={ratio(s.memUsed, s.memTotal)} text={pct(ratio(s.memUsed, s.memTotal))} sub={`${gb(s.memUsed)} / ${gb(s.memTotal)}`} />
              <Meter label="Java 힙" value={ratio(s.heapUsed, s.heapMax)} text={pct(ratio(s.heapUsed, s.heapMax))} sub={`${gb(s.heapUsed)} / ${gb(s.heapMax)}`} />
              <Meter label="디스크" value={ratio(s.diskUsed, s.diskTotal)} text={pct(ratio(s.diskUsed, s.diskTotal))} sub={`${gb(s.diskUsed)} / ${gb(s.diskTotal)}`} />
              <div className="sv-stats">
                <Stat label="켜진 지" value={since(app.startedAt)} />
                <Stat label="스레드" value={s.threads} />
                <Stat label="실시간 접속" value={`${s.live}명`} />
                <Stat label="Java" value={app.java} />
              </div>
              <p className="sv-note">실시간 접속 = 토론방 {app.boardLive} · 메시지 {app.dmLive} (SSE 연결 수)</p>
            </section>

            {/* ===== DB 서버 ===== */}
            <section className="sv-card">
              <h2>🗄 DB 서버 <small>{db.version || 'PostgreSQL'} · RDS</small></h2>
              <div className="sv-stats sv-stats-top">
                <Stat label="응답 속도" value={s.dbOk ? `${s.dbMs}ms` : '연결 안 됨'} tone={!s.dbOk ? 'sv-bad' : s.dbMs >= 300 ? 'sv-warn' : 'sv-ok'} />
                <Stat label="초당 트랜잭션" value={s.dbTps < 0 ? '-' : s.dbTps} />
                <Stat label="가장 오래 도는 쿼리" value={s.longestSec < 0 ? '-' : `${s.longestSec}초`} tone={s.longestSec >= 10 ? 'sv-bad' : s.longestSec >= 3 ? 'sv-warn' : ''} />
                <Stat label="켜진 지" value={since(db.startedAt)} />
              </div>
              <Meter label="연결 수 (전체 DB)" value={ratio(s.dbConnAll, s.dbMax)} text={`${s.dbConnAll < 0 ? '-' : s.dbConnAll} / ${s.dbMax < 0 ? '-' : s.dbMax}`} sub={`이 DB(${db.name ?? '-'}) ${s.dbConn < 0 ? '-' : s.dbConn}개`} />
              <Meter label="캐시 적중률" value={s.cacheHit} text={pct(s.cacheHit)} tone={s.cacheHit < 0 ? '' : s.cacheHit >= 0.99 ? 'sv-ok' : s.cacheHit >= 0.95 ? 'sv-warn' : 'sv-bad'} sub="높을수록 디스크 대신 메모리에서 읽음 (99%↑ 정상)" />
              <div className="sv-chips">
                {data.connStates.map((c) => <span key={c.state}>{c.state} {c.count}</span>)}
                <span>DB 크기 {gb(db.sizeBytes)}</span>
                {db.deadlocks > 0 && <span className="sv-bad">교착 누적 {db.deadlocks}</span>}
              </div>
            </section>
          </div>

          {/* ===== 최근 1시간 ===== */}
          <h2 className="sv-h2">최근 1시간</h2>
          <div className="sv-charts">
            <Chart title="CPU" max={100} unit="%" series={[
              { name: '서버', values: h.map((x) => (x.cpu < 0 ? -1 : Math.round(x.cpu * 100))), color: '#3b82f6' },
              { name: '우리 앱', values: h.map((x) => (x.appCpu < 0 ? -1 : Math.round(x.appCpu * 100))), color: '#f59e0b' },
            ]} />
            <Chart title="메모리" max={100} unit="%" series={[
              { name: '서버', values: h.map((x) => Math.round(ratio(x.memUsed, x.memTotal) * 100)), color: '#8b5cf6' },
              { name: 'Java 힙', values: h.map((x) => Math.round(ratio(x.heapUsed, x.heapMax) * 100)), color: '#22c55e' },
            ]} />
            <Chart title="DB 응답 속도" unit="ms" series={[
              { name: '응답', values: h.map((x) => (x.dbOk ? x.dbMs : -1)), color: '#ec4899' },
            ]} />
            <Chart title="DB 연결 · 트랜잭션" unit="" series={[
              { name: '연결', values: h.map((x) => x.dbConnAll), color: '#06b6d4' },
              { name: '초당 트랜잭션', values: h.map((x) => x.dbTps), color: '#eab308' },
            ]} />
          </div>

          {/* ===== 큰 테이블 ===== */}
          {data.topTables?.length > 0 && (
            <section className="sv-card sv-tables">
              <h2>큰 테이블 <small>1분마다 갱신</small></h2>
              <table>
                <thead><tr><th>테이블</th><th>크기</th><th>행(추정)</th></tr></thead>
                <tbody>
                  {data.topTables.map((t) => (
                    <tr key={t.table}><td>{t.table}</td><td>{gb(t.bytes)}</td><td>{Number(t.rows).toLocaleString('ko-KR')}</td></tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}

          <p className="sv-note">
            RDS 장비의 CPU·디스크 IO·네트워크는 AWS 콘솔(CloudWatch)에서 볼 수 있어요. 여기는 서버와 DB가 직접 알려주는 값이에요.
          </p>
        </>
      )}
    </main>
  )
}

export default AdminServerPage
