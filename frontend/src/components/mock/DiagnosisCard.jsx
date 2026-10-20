// ============================================
// 🩺 포트폴리오 AI 진단 (모의투자 화면, 보유 종목 아래)
// - 서버가 숫자로 점검: 분산 · 현금 비중 · 매도 승률 · 손익비 · 거래 빈도 → 좋음/주의/위험
// - AI가 한 줄 요약 · 관찰 3개 · 이번 주 해볼 습관 2개 (종목 추천은 하지 않음)
// - 하루 3번, 마지막 결과는 다시 열어도 보임
// - 백엔드: GET/POST /api/mock/diagnosis
// ============================================
import { useEffect, useState } from 'react'
import './DiagnosisCard.css'

const LEVEL = { good: '좋음', warn: '주의', bad: '위험', info: '참고' }

const when = (ms) => {
  const d = new Date(ms)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function DiagnosisCard() {

  const [data, setData] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let ignore = false
    fetch('/api/mock/diagnosis')
      .then((res) => res.json())
      .then((json) => { if (!ignore && json.success) setData(json) })
      .catch(() => {})
    return () => { ignore = true }
  }, [])

  const run = async () => {
    setBusy(true)
    setError('')
    try {
      const res = await fetch('/api/mock/diagnosis', { method: 'POST' })
      const json = await res.json()
      if (!json.success) throw new Error(json.message)
      setData(json)
    } catch (e) {
      setError(e.message || '진단하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  if (!data) return null

  const r = data.result
  const s = data.stats

  return (
    <section className="mock-card mock-diag">
      <div className="mock-card-head diag-head">
        <h2>🩺 AI 포트폴리오 진단</h2>
        {data.createdAt && <span className="mock-card-sub">{when(data.createdAt)} 기준</span>}
      </div>

      {!r && <p className="diag-intro">지금 보유 종목과 최근 거래를 숫자로 점검하고, AI가 투자 습관을 짚어드려요. (종목 추천은 하지 않아요)</p>}

      {r && (
        <>
          <p className="diag-summary">{r.summary}</p>

          <ul className="diag-checks">
            {s.checks.map((c) => (
              <li key={c.key} className={`diag-check diag-${c.level}`}>
                <span className="diag-level">{LEVEL[c.level]}</span>
                <b>{c.label}</b>
                <span className="diag-value">{c.value}</span>
                <small>{c.hint}</small>
              </li>
            ))}
          </ul>

          <div className="diag-obs">
            {r.observations.map((o) => (
              <div key={o.title} className={`diag-ob diag-ob-${o.tone}`}>
                <b>{o.tone === 'good' ? '👍' : '⚠️'} {o.title}</b>
                <p>{o.detail}</p>
              </div>
            ))}
          </div>

          <div className="diag-habits">
            <strong>이번 주 해볼 습관</strong>
            <ol>
              {r.habits.map((h) => (
                <li key={h.title}><b>{h.title}</b><span>{h.detail}</span></li>
              ))}
            </ol>
          </div>
        </>
      )}

      {data.aiReady && (
        <div className="diag-foot">
          <button type="button" className="btn btn-primary" onClick={run} disabled={busy || data.remaining <= 0}>
            {busy ? 'AI가 포트폴리오를 보는 중... (10초쯤)' : r ? '🔄 다시 진단' : '🩺 진단 받기'}
          </button>
          <span className="diag-left">오늘 {data.remaining}번 남음</span>
        </div>
      )}
      {error && <p className="diag-error">{error}</p>}
    </section>
  )
}

export default DiagnosisCard
