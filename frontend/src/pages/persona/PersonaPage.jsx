// ============================================
// 🧭 투자 성향 테스트 (주소: /persona, 결과 공유: /persona?type=SLD)
// - 12문항 → 4가지 축 점수 → 8가지 유형 + 원칙 점수, 나와 닮은 AI 리그 투자자
// - 결과는 서버에 저장해서 "나와 같은 유형 n%" 통계 (POST /api/persona/result, GET /api/persona/stats)
// - 투자 권유가 아니라 내 습관을 돌아보는 재미용 테스트
// ============================================
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AXES, AXIS_MAX, QUESTIONS, TYPES, typeOf } from './personaData'
import './PersonaPage.css'

const emptyScores = () => ({ risk: 0, horizon: 0, basis: 0, discipline: 0 })

function PersonaPage() {

  const [params, setParams] = useSearchParams()
  const shared = TYPES[params.get('type')] ? params.get('type') : null

  const [step, setStep] = useState(-1)            // -1 시작 화면, 0~11 문항, 12 결과
  const [answers, setAnswers] = useState([])      // 고른 점수
  const [scores, setScores] = useState(null)      // 결과 점수 (내가 한 테스트)
  const [stats, setStats] = useState(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    let ignore = false
    fetch('/api/persona/stats')
      .then((res) => res.json())
      .then((json) => { if (!ignore && json.success) setStats(json.stats) })
      .catch(() => {})
    return () => { ignore = true }
  }, [])

  const choose = (value) => {
    const next = [...answers.slice(0, step), value]
    setAnswers(next)

    if (step + 1 < QUESTIONS.length) {
      setStep(step + 1)
      return
    }

    // 마지막 문항 → 점수 합산
    const s = emptyScores()
    next.forEach((v, i) => { s[QUESTIONS[i].axis] += v })
    const code = typeOf(s)
    setScores(s)
    setStep(QUESTIONS.length)
    setParams({ type: code }, { replace: true })

    fetch('/api/persona/result', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ typeCode: code, ...s }),
    })
      .then((res) => res.json())
      .then((json) => { if (json.success) setStats(json.stats) })
      .catch(() => {})
  }

  const restart = () => {
    setAnswers([])
    setScores(null)
    setStep(0)
    setParams({}, { replace: true })
  }

  const share = async () => {
    const url = `${window.location.origin}/persona?type=${code}`
    const text = `나의 투자 성향은 ${TYPES[code].icon} ${TYPES[code].name}! 너는 어떤 유형이야?`
    try {
      if (navigator.share) await navigator.share({ title: 'SKLabs 투자 성향 테스트', text, url })
      else {
        await navigator.clipboard.writeText(`${text} ${url}`)
        setCopied(true)
      }
    } catch { /* 공유 취소 */ }
  }

  const code = scores ? typeOf(scores) : shared
  const type = code ? TYPES[code] : null
  const inQuiz = step >= 0 && step < QUESTIONS.length

  // ===== 문항 =====
  if (inQuiz) {
    const q = QUESTIONS[step]
    return (
      <main className="ps-page">
        <div className="ps-progress" aria-label={`${step + 1} / ${QUESTIONS.length}`}>
          <span style={{ width: `${((step + 1) / QUESTIONS.length) * 100}%` }} />
        </div>
        <p className="ps-step">Q{step + 1} / {QUESTIONS.length}</p>
        <h1 className="ps-q">{q.q}</h1>
        <div className="ps-options">
          {q.options.map(([label, value]) => (
            <button key={label} type="button" className={`ps-option ${answers[step] === value ? 'ps-option-on' : ''}`} onClick={() => choose(value)}>
              {label}
            </button>
          ))}
        </div>
        {step > 0 && <button type="button" className="ps-back" onClick={() => setStep(step - 1)}>← 이전 문항</button>}
      </main>
    )
  }

  // ===== 결과 (내 결과 또는 공유 링크) =====
  if (type) {
    const pct = stats?.types?.[code]
    return (
      <main className="ps-page">
        <p className="ps-eyebrow">{scores ? '나의 투자 성향은' : '친구의 투자 성향은'}</p>
        <section className="ps-result">
          <span className="ps-icon" aria-hidden="true">{type.icon}</span>
          <h1>{type.name}</h1>
          <p className="ps-short">{type.short}</p>
          <p className="ps-desc">{type.desc}</p>
          {pct != null && <p className="ps-stat">테스트한 {stats.total.toLocaleString('ko-KR')}명 중 <b>{pct}%</b>가 같은 유형이에요</p>}
        </section>

        {scores && (
          <section className="ps-card">
            <h2>나의 점수</h2>
            {AXES.map((a) => {
              const v = scores[a.key]
              const pos = ((v + AXIS_MAX) / (AXIS_MAX * 2)) * 100
              return (
                <div key={a.key} className="ps-axis">
                  <span className={v <= 0 ? 'ps-axis-on' : ''}>{a.left}</span>
                  <div className="ps-axis-bar"><span style={{ left: `${pos}%` }} /></div>
                  <span className={v > 0 ? 'ps-axis-on' : ''}>{a.right}</span>
                </div>
              )
            })}
          </section>
        )}

        <div className="ps-two">
          <section className="ps-card">
            <h2>👍 강점</h2>
            <ul>{type.strengths.map((s) => <li key={s}>{s}</li>)}</ul>
          </section>
          <section className="ps-card">
            <h2>⚠️ 조심할 점</h2>
            <ul>{type.watch.map((s) => <li key={s}>{s}</li>)}</ul>
          </section>
        </div>

        <section className="ps-card">
          <h2>🧪 SKLabs에서 연습해 보기</h2>
          <p>{type.practice}</p>
          <div className="ps-links">
            <Link to="/mock" className="ps-link">💰 모의투자</Link>
            <Link to="/predict" className="ps-link">🎯 예측 게임</Link>
            <Link to="/league" className="ps-link">{type.ai.icon} 나와 닮은 {type.ai.name} 보기</Link>
          </div>
        </section>

        {stats?.total > 0 && (
          <section className="ps-card">
            <h2>다른 사람들은?</h2>
            <ul className="ps-dist">
              {Object.entries(TYPES).map(([c, t]) => (
                <li key={c} className={c === code ? 'ps-dist-me' : ''}>
                  <span>{t.icon} {t.name}</span>
                  <div className="ps-dist-bar"><span style={{ width: `${stats.types[c] || 0}%` }} /></div>
                  <b>{stats.types[c] || 0}%</b>
                </li>
              ))}
            </ul>
          </section>
        )}

        <div className="ps-actions">
          {scores && <button type="button" className="ps-btn ps-btn-primary" onClick={share}>{copied ? '링크를 복사했어요' : '📤 결과 공유하기'}</button>}
          <button type="button" className={`ps-btn ${scores ? '' : 'ps-btn-primary'}`} onClick={restart}>{scores ? '다시 하기' : '나도 테스트하기'}</button>
        </div>
        <p className="ps-note">재미로 보는 성향 테스트예요. 투자 권유가 아니고, 실제 판단은 본인 책임이에요.</p>
      </main>
    )
  }

  // ===== 시작 화면 =====
  return (
    <main className="ps-page ps-intro">
      <span className="ps-icon" aria-hidden="true">🧭</span>
      <h1>나는 어떤 투자자일까?</h1>
      <p className="ps-desc">12문항, 1분이면 끝나요. 위험을 대하는 태도, 투자 기간, 판단 근거, 원칙을 보고 8가지 유형 중 하나를 알려드려요.</p>
      <div className="ps-preview">
        {Object.values(TYPES).map((t) => <span key={t.name} title={t.name}>{t.icon}</span>)}
      </div>
      <button type="button" className="ps-btn ps-btn-primary ps-start" onClick={() => setStep(0)}>테스트 시작하기</button>
      {stats?.total > 0 && <p className="ps-note">지금까지 {stats.total.toLocaleString('ko-KR')}명이 테스트했어요</p>}
    </main>
  )
}

export default PersonaPage
