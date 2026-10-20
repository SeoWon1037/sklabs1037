// ============================================
// 📰 오늘의 뉴스 퀴즈 (주소: /quiz)
// - 매일 아침 7시 40분, 최근 뉴스로 AI가 5문제 출제 (문제마다 근거 기사)
// - 다 고르고 제출하면 서버가 채점 → 정답·해설·근거 기사 링크, 오늘 참여자 점수 분포
// - 로그인하면 하루 한 번 기록 → 이번 주 랭킹, 연속 참여 🔥
// - 백엔드: GET /api/quiz, POST /api/quiz/submit { answers }
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './QuizPage.css'

const LETTERS = ['A', 'B', 'C', 'D']

const dayLabel = (text) => {
  const d = new Date(`${text}T00:00:00+09:00`)
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${'일월화수목금토'[d.getDay()]})`
}

const comment = (score, total) => {
  if (score === total) return '🏆 만점! 오늘 뉴스는 다 꿰고 있네요'
  if (score >= total - 1) return '👏 거의 다 맞혔어요'
  if (score >= Math.ceil(total / 2)) return '🙂 절반 넘게 맞혔어요'
  return '📰 오늘 뉴스를 한 번 훑어볼까요?'
}

function QuizPage() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [answers, setAnswers] = useState([])
  const [result, setResult] = useState(null)
  const [sending, setSending] = useState(false)

  useEffect(() => {
    let ignore = false
    fetch('/api/quiz')
      .then((res) => res.json())
      .then((json) => {
        if (ignore) return
        if (!json.success) throw new Error(json.message)
        setData(json)
        if (json.myResult) setResult({ ...json.myResult, stats: json.stats, streak: json.streak })
      })
      .catch((e) => { if (!ignore) setError(e.message || '서버에 연결할 수 없습니다.') })
    return () => { ignore = true }
  }, [])

  const questions = data?.quiz?.questions || []
  const allAnswered = questions.length > 0 && questions.every((_, i) => answers[i] != null)

  const pick = (qi, oi) => {
    if (result) return
    const next = [...answers]
    next[qi] = oi
    setAnswers(next)
  }

  const submit = async () => {
    setSending(true)
    try {
      const res = await fetch('/api/quiz/submit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ answers }),
      })
      const json = await res.json()
      if (!json.success) throw new Error(json.message)
      setResult(json)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (e) {
      setError(e.message || '채점하지 못했어요.')
    } finally {
      setSending(false)
    }
  }

  const stats = result?.stats || data?.stats
  const maxCount = stats ? Math.max(1, ...stats.scoreCounts) : 1

  return (
    <main className="qz-page">
      <header className="qz-hero">
        <p className="qz-eyebrow">매일 아침 새 문제</p>
        <h1>📰 오늘의 뉴스 퀴즈</h1>
        {data?.quiz && (
          <p className="qz-desc">
            {dayLabel(data.quiz.date)} 뉴스로 만든 {questions.length}문제예요. {!data.quiz.today && '(오늘 문제는 아침 7시 40분에 나와요)'}
          </p>
        )}
      </header>

      {error && <p className="qz-empty">{error}</p>}
      {!data && !error && <p className="qz-empty">불러오는 중...</p>}
      {data && !data.quiz && <p className="qz-empty">아직 퀴즈가 없어요. 매일 아침 7시 40분에 새 문제가 나와요.</p>}

      {/* ===== 결과 ===== */}
      {result && (
        <section className="qz-result">
          <p className="qz-score"><b>{result.score}</b> / {result.total}</p>
          <p className="qz-comment">{comment(result.score, result.total)}</p>
          {result.streak > 0 && <p className="qz-streak">🔥 {result.streak}일 연속 참여</p>}
          {!data?.loggedIn && <p className="qz-muted"><Link to="/login">로그인</Link>하면 점수가 기록되고 이번 주 랭킹에 올라가요.</p>}
          {stats?.players > 0 && (
            <div className="qz-dist" aria-label="오늘 참여자 점수 분포">
              {stats.scoreCounts.map((c, s) => (
                <div key={s} className={`qz-dist-col ${s === result.score ? 'qz-dist-me' : ''}`}>
                  <span className="qz-dist-bar" style={{ height: `${(c / maxCount) * 60 + 4}px` }} />
                  <small>{s}점</small>
                </div>
              ))}
            </div>
          )}
          {stats?.players > 0 && <p className="qz-muted">오늘 {stats.players}명 참여 · 평균 {stats.avgScore}점</p>}
        </section>
      )}

      {/* ===== 문제 ===== */}
      {questions.length > 0 && (
        <ol className="qz-questions">
          {(result ? result.items : questions).map((q, qi) => (
            <li key={qi} className={`qz-q ${result ? (q.correct ? 'qz-q-ok' : 'qz-q-no') : ''}`}>
              <p className="qz-q-text"><span className="qz-q-no-label">Q{qi + 1}</span> {q.question}</p>
              <div className="qz-options">
                {q.options.map((o, oi) => {
                  const chosen = result ? q.mine === oi : answers[qi] === oi
                  const isAnswer = result && q.answer === oi
                  return (
                    <button key={oi} type="button" disabled={!!result}
                      className={`qz-option ${chosen ? 'qz-option-on' : ''} ${isAnswer ? 'qz-option-answer' : ''} ${result && chosen && !isAnswer ? 'qz-option-wrong' : ''}`}
                      onClick={() => pick(qi, oi)} aria-pressed={chosen}>
                      <span className="qz-letter">{LETTERS[oi]}</span>{o}
                    </button>
                  )
                })}
              </div>
              {result && (
                <div className="qz-explain">
                  <p>{q.correct ? '✅ 정답!' : '❌ 아쉬워요.'} {q.explanation}</p>
                  {q.sources?.map((s, i) => (
                    <a key={i} href={s.link} target="_blank" rel="noopener noreferrer" className="qz-source">📰 {s.title} <small>{s.press}</small></a>
                  ))}
                </div>
              )}
            </li>
          ))}
        </ol>
      )}

      {questions.length > 0 && !result && (
        <button type="button" className="qz-submit" onClick={submit} disabled={!allAnswered || sending}>
          {sending ? '채점 중...' : allAnswered ? '제출하고 정답 보기' : `${answers.filter((a) => a != null).length} / ${questions.length} 골랐어요`}
        </button>
      )}

      {/* ===== 이번 주 랭킹 ===== */}
      {data && (
        <section className="qz-card">
          <h2>🏆 이번 주 랭킹</h2>
          {data.ranking.length === 0 ? <p className="qz-muted">이번 주 기록이 아직 없어요. 첫 번째로 올라가 보세요!</p> : (
            <ol className="qz-ranking">
              {data.ranking.map((r, i) => (
                <li key={i}>
                  <span className="qz-rank">{['🥇', '🥈', '🥉'][i] || i + 1}</span>
                  <span className="qz-rank-name">{r.nickname}</span>
                  <span><b>{r.total}점</b> <small className="qz-muted">{r.days}일</small></span>
                </li>
              ))}
            </ol>
          )}
        </section>
      )}

      <p className="qz-note">문제는 AI가 기사 제목으로 만들고, 서버가 형식과 근거 기사를 확인해요. 틀린 문제가 보이면 근거 기사로 확인해 주세요.</p>
    </main>
  )
}

export default QuizPage
