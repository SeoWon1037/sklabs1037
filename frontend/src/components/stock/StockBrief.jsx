// ============================================
// 🧾 종목 한 장 요약 (종목 상세, 탭 위)
// - AI가 📰 뉴스 · 💬 토론 분위기 · 📈 가격 흐름을 한 줄씩 (투자 판단·전망은 하지 않음)
// - 종목마다 3시간 저장 → 그 사이엔 바로 보임, 오래됐으면 "새로 요약" (로그인)
// - 백엔드: GET/POST /api/stock/brief?stockId=
// ============================================
import { useEffect, useState } from 'react'
import './StockBrief.css'

const ago = (ms) => {
  const m = Math.floor((Date.now() - ms) / 60000)
  if (m < 1) return '방금'
  if (m < 60) return `${m}분 전`
  if (m < 1440) return `${Math.floor(m / 60)}시간 전`
  return `${Math.floor(m / 1440)}일 전`
}

function StockBrief({ stockId }) {

  const [data, setData] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let ignore = false
    fetch(`/api/stock/brief?stockId=${stockId}`)
      .then((res) => res.json())
      .then((json) => { if (!ignore && json.success) setData(json) })
      .catch(() => {})
    return () => { ignore = true }
  }, [stockId])

  const make = async () => {
    setBusy(true)
    setError('')
    try {
      const res = await fetch(`/api/stock/brief?stockId=${stockId}`, { method: 'POST' })
      const json = await res.json()
      if (!json.success) throw new Error(json.message)
      setData(json)
    } catch (e) {
      setError(e.message || '요약하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  if (!data || (!data.brief && !data.aiReady)) return null

  const b = data.brief

  return (
    <section className="sb">
      <div className="sb-head">
        <strong>🧾 AI 한 장 요약</strong>
        {b && <span className="sb-time">{ago(b.insAt)} 정리</span>}
      </div>

      {b ? (
        <ul className="sb-lines">
          {b.lines.map((l) => (
            <li key={l.key}>
              <span className="sb-label">{l.icon} {l.label}</span>
              <span className="sb-text">{l.text}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="sb-intro">최근 뉴스 · 토론방 분위기 · 가격 흐름을 AI가 세 줄로 정리해 드려요.</p>
      )}

      {b?.news?.length > 0 && (
        <div className="sb-news">
          {b.news.map((n) => (
            <a key={n.link || n.title} href={n.link} target="_blank" rel="noopener noreferrer">{n.title}<small>{n.press}</small></a>
          ))}
        </div>
      )}

      {data.aiReady && !data.fresh && (
        <button type="button" className="btn btn-ghost sb-btn" onClick={make} disabled={busy}>
          {busy ? 'AI가 정리하는 중... (5초쯤)' : b ? '🔄 새로 요약' : '✨ AI 요약 보기'}
        </button>
      )}
      {error && <p className="sb-error">{error}</p>}
      <p className="sb-note">AI 요약은 참고용이며 투자 판단의 근거가 아니에요.</p>
    </section>
  )
}

export default StockBrief
