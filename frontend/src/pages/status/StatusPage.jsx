// ============================================
// 🟢 서비스 상태 (주소: /status, 로그인 없이 누구나) — 1분마다 새로고침
// - 웹 · DB · 주식 시세 · 뉴스 · AI 기능 · 자동 화면 점검(QA 봇)이 지금 제대로 도는지
// - 초록 정상 / 노랑 늦어짐·일부 멈춤 / 빨강 장애
// - 백엔드: GET /api/status (StatusService, 30초 캐시)
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './StatusPage.css'

const OVERALL = {
  OK: ['모든 서비스가 정상이에요', '✅'],
  WARN: ['일부 서비스가 늦어지고 있어요', '⚠️'],
  DOWN: ['일부 서비스에 장애가 있어요', '🔴'],
}
const DOT = { OK: '정상', WARN: '지연', DOWN: '장애' }
const QA = { PASS: '통과', WARN: '작은 문제', FAIL: '문제 발견' }
const SOURCE = { DEPLOY: '배포 직후', SCHEDULE: '매일 아침', MANUAL: '수동' }

const time = (ms) => {
  const d = new Date(ms)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function StatusPage() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    fetch('/api/status')
      .then((r) => r.json())
      .then((j) => {
        if (!j.success) throw new Error()
        setData(j)
        setError('')
      })
      .catch(() => setError('서버에 연결하지 못했어요. 사이트 전체가 잠시 멈췄을 수 있어요.'))
  }, [])

  useEffect(() => {
    load()
    const t = setInterval(() => { if (document.visibilityState === 'visible') load() }, 60000)
    return () => clearInterval(t)
  }, [load])

  const [title, icon] = error ? ['서버에 연결할 수 없어요', '🔴'] : OVERALL[data?.overall] ?? ['확인하는 중…', '⏳']
  const tone = error ? 'DOWN' : data?.overall ?? 'OK'

  return (
    <main className="st-page">
      <h1>🟢 서비스 상태</h1>

      <section className={`st-banner st-${tone}`} aria-live="polite">
        <span aria-hidden="true">{icon}</span>
        <div>
          <strong>{title}</strong>
          <p>{error || (data ? `${time(data.at)} 확인 · 1분마다 새로 확인해요` : '')}</p>
        </div>
      </section>

      {data && (
        <>
          <ul className="st-list">
            {data.items.map((it) => (
              <li key={it.key} className="st-row">
                <span className={`st-dot st-${it.status}`} aria-hidden="true" />
                <div>
                  <strong>{it.label}</strong>
                  <p>{it.detail}</p>
                </div>
                <em className={`st-tag st-${it.status}`}>{DOT[it.status]}</em>
              </li>
            ))}
          </ul>

          {data.checks.length > 0 && (
            <section className="st-checks">
              <h2>최근 자동 점검</h2>
              <p className="st-muted">AI QA 봇이 배포 직후와 매일 아침, 진짜 브라우저로 휴대폰·PC 화면을 열어 확인해요. 새로 생긴 심각한 문제는 자동으로 이전 버전으로 되돌려요.</p>
              <ol className="st-timeline">
                {data.checks.map((c) => (
                  <li key={c.at}>
                    <span className={`st-dot st-${c.status === 'PASS' ? 'OK' : c.status === 'WARN' ? 'WARN' : 'DOWN'}`} aria-hidden="true" />
                    <span>{time(c.at)}</span>
                    <span className="st-muted">{SOURCE[c.source] ?? c.source}</span>
                    <b>{QA[c.status] ?? c.status}</b>
                  </li>
                ))}
              </ol>
            </section>
          )}
        </>
      )}

      <p className="st-note">
        문제가 계속되면 <Link to="/support?from=/status">문의하기</Link>로 알려주세요.
        {data && !data.tradingToday && ' 오늘은 증시가 쉬는 날이라 시세가 바뀌지 않아요.'}
      </p>
    </main>
  )
}

export default StatusPage
