// ============================================
// 마이페이지 "☀️ 아침 브리핑 메일" 칸
// - 켜면 평일 아침 8시에 메일 1통: 시장 AI 요약 + 관심 종목 어제 종가·등락(+AI 뉴스 요약) + 내 모의투자
// - "나에게 지금 보내보기"로 미리보기 (10분에 한 번)
// - 백엔드 API: GET /api/briefing, POST /api/briefing { enabled }, POST /api/briefing/test
// ============================================
import { useEffect, useState } from 'react'
import '../alert/PriceAlertPanel.css'
import './MorningBriefBox.css'

function MorningBriefBox() {

  const [brief, setBrief] = useState(null)
  const [msg, setMsg] = useState({ ok: false, text: '' })
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    fetch('/api/briefing')
      .then((res) => res.json())
      .then((json) => setBrief(json.success ? json : { error: true }))
      .catch(() => setBrief({ error: true }))
  }, [])

  const post = async (url, body) => {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: body ? JSON.stringify(body) : undefined,
    })
    return res.json()
  }

  const toggle = async () => {
    if (busy || !brief) return
    const next = !brief.enabled
    setBusy(true)
    setMsg({ ok: false, text: '' })
    try {
      const json = await post('/api/briefing', { enabled: next })
      if (json.success) {
        setBrief((b) => ({ ...b, enabled: next }))
        setMsg({ ok: true, text: next ? '켰어요. 평일 아침 8시에 보내드려요.' : '껐어요.' })
      } else {
        setMsg({ ok: false, text: json.message })
      }
    } catch {
      setMsg({ ok: false, text: '서버에 연결할 수 없습니다.' })
    } finally {
      setBusy(false)
    }
  }

  const sendTest = async () => {
    if (busy) return
    setBusy(true)
    setMsg({ ok: false, text: '' })
    try {
      const json = await post('/api/briefing/test')
      setMsg(json.success ? { ok: true, text: '보냈어요. 메일함을 확인해보세요. (스팸함도 한 번)' } : { ok: false, text: json.message })
    } catch {
      setMsg({ ok: false, text: '서버에 연결할 수 없습니다.' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="mypage-box brief-box">
      <div className="mypage-box-head">
        <h2 className="mypage-box-title">☀️ 아침 브리핑 메일</h2>
        {brief && !brief.error && brief.hasEmail && (
          <button
            type="button"
            role="switch"
            aria-checked={Boolean(brief.enabled)}
            className={`brief-switch ${brief.enabled ? 'brief-switch-on' : ''}`}
            onClick={toggle}
            disabled={busy}
          >
            <span className="brief-switch-knob" />
            <span className="brief-switch-label">{brief.enabled ? '받는 중' : '꺼짐'}</span>
          </button>
        )}
      </div>

      <p className="mypage-desc">
        평일 아침 8시, 장 시작 전에 메일 한 통으로 정리해드려요.
      </p>
      <ul className="brief-points">
        <li>📰 시장 한눈에 — AI 뉴스 요약 3줄</li>
        <li>⭐ 내 관심 종목 — 어제 종가·등락률, 주요 종목은 AI 뉴스 요약 한 줄</li>
        <li>💰 내 모의투자 — 총자산·수익률, 보유 종목 수익률</li>
      </ul>

      {brief === null && <p className="mypage-desc">불러오는 중...</p>}
      {brief?.error && <p className="alert-msg alert-msg-error">불러오지 못했어요.</p>}
      {brief && !brief.error && !brief.hasEmail && (
        <p className="alert-msg alert-msg-error">이 계정에는 이메일이 없어서 브리핑 메일을 받을 수 없어요.</p>
      )}

      {brief && !brief.error && brief.hasEmail && (
        <div className="brief-actions">
          <button type="button" className="brief-test" onClick={sendTest} disabled={busy}>
            나에게 지금 보내보기
          </button>
          {brief.lastSentDay && <span className="brief-last">마지막 발송 {brief.lastSentDay.slice(5).replace('-', '/')}</span>}
        </div>
      )}

      {msg.text && <p className={`alert-msg ${msg.ok ? 'alert-msg-ok' : 'alert-msg-error'}`}>{msg.text}</p>}
    </section>
  )
}

export default MorningBriefBox
