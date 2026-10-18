// ============================================
// AI 뉴스 요약 박스 (메인 화면 뉴스 탭, 주식 종목 화면 뉴스 탭에서 공통 사용)
// - stockId 가 있으면 종목 요약, 없으면 시장 요약
// - 요약이 아직 없으면 아무것도 안 보여줌
// - 백엔드 API: GET /api/news/summary?stockId=
// ============================================
import { useEffect, useState } from 'react'
import './NewsSummaryBox.css'

// "2026-10-18T16:30:05" → "10월 18일 오후 4:30"
const formatTime = (text) => {
  const d = new Date(text)
  if (Number.isNaN(d.getTime())) return ''
  const hour = d.getHours()
  const ampm = hour < 12 ? '오전' : '오후'
  const h12 = hour % 12 === 0 ? 12 : hour % 12
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${ampm} ${h12}:${String(d.getMinutes()).padStart(2, '0')}`
}

// 요약 본문 → 줄 목록 ("- " 같은 머리 기호는 떼기)
const toLines = (text) => (text ?? '')
  .split('\n')
  .map((line) => line.replace(/^\s*[-•*·]\s*/, '').trim())
  .filter(Boolean)

function NewsSummaryBox({ stockId }) {

  const [summary, setSummary] = useState(null)

  useEffect(() => {

    let ignore = false
    const url = stockId ? `/api/news/summary?stockId=${stockId}` : '/api/news/summary'

    fetch(url)
      .then((res) => res.json())
      .then((data) => {
        if (!ignore) setSummary(data.success ? data.summary : null)
      })
      .catch(() => {
        if (!ignore) setSummary(null)
      })

    return () => { ignore = true }

  }, [stockId])

  const lines = toLines(summary?.summary)
  if (lines.length === 0) return null

  return (
    <section className="news-summary">
      <div className="news-summary-head">
        <span className="news-summary-badge">✨ AI 요약</span>
        <span className="news-summary-meta">
          {formatTime(summary.summaryAt)} 기준 · 뉴스 {summary.newsCount}건
        </span>
      </div>
      <ul className="news-summary-list">
        {lines.map((line, i) => <li key={i}>{line}</li>)}
      </ul>
      <p className="news-summary-note">수집된 뉴스 제목을 AI가 요약한 내용이에요. 투자 판단의 근거로 쓰지 마세요.</p>
    </section>
  )
}

export default NewsSummaryBox