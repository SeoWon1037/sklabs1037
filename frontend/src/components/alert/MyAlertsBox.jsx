// ============================================
// 마이페이지 "🔔 내 가격 알림" 칸
// - 전체 알림: 종목 · 조건 · 지금 가격(목표까지 몇 %) · 상태
// - 켜짐: 삭제 / 보냄: 다시 켜기 · 삭제
// - 백엔드 API: GET /api/alerts, DELETE /api/alerts/{id}, POST /api/alerts/{id}/reactivate
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './PriceAlertPanel.css'

const won = (v) => Math.round(Number(v)).toLocaleString('ko-KR')

// "10.20 09:31"
const formatTime = (ms) => {
  const d = new Date(ms)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function MyAlertsBox({ hasEmail = true }) {

  const [alerts, setAlerts] = useState(null)
  const [msg, setMsg] = useState('')

  const load = useCallback(() => {
    return fetch('/api/alerts')
      .then((res) => res.json())
      .then((json) => setAlerts(json.success ? json.alerts : []))
      .catch(() => setAlerts([]))
  }, [])

  useEffect(() => { load() }, [load])

  const act = async (url, method) => {
    setMsg('')
    try {
      const res = await fetch(url, { method })
      const json = await res.json()
      if (!json.success) setMsg(json.message)
    } catch {
      setMsg('서버에 연결할 수 없습니다.')
    }
    load()
  }

  const activeCount = alerts?.filter((a) => a.status === 'ACTIVE').length ?? 0

  return (
    <section className="mypage-box">
      <div className="mypage-box-head">
        <h2 className="mypage-box-title">🔔 내 가격 알림</h2>
        <span className="mypage-count">켜짐 {activeCount}개</span>
      </div>

      {!hasEmail && (
        <p className="alert-msg alert-msg-error">이 계정에는 이메일이 없어서 알림 메일을 받을 수 없어요.</p>
      )}

      {alerts === null && <p className="mypage-desc">불러오는 중...</p>}

      {alerts?.length === 0 && (
        <p className="mypage-desc">
          아직 걸어둔 알림이 없어요. 주식 종목 화면에서 <b>🔔 알림</b>을 눌러 목표가를 정해보세요.
          {' '}<Link to="/stock" className="mypage-link">주식 종목 →</Link>
        </p>
      )}

      {alerts?.length > 0 && (
        <ul className="my-alert-list">
          {alerts.map((a) => {
            const gap = a.price ? ((Number(a.targetPrice) - Number(a.price)) / Number(a.price)) * 100 : null
            const sent = a.status === 'SENT'
            return (
              <li key={a.alertId} className={sent ? 'alert-sent' : ''}>
                <span className={`alert-badge ${sent ? 'alert-badge-sent' : ''}`}>{sent ? '보냄' : '켜짐'}</span>
                <div className="my-alert-main">
                  <Link to={`/stock?symbol=${a.symbol}`} className="my-alert-name">{a.name}</Link>
                  <span>
                    {won(a.targetPrice)}원{' '}
                    <b className={a.condition === 'ABOVE' ? 'alert-up' : 'alert-down'}>{a.condition === 'ABOVE' ? '이상' : '이하'}</b>
                  </span>
                  <small>
                    {sent
                      ? `${formatTime(a.sentAt)} · ${won(a.sentPrice)}원에 알림`
                      : gap !== null && `지금 ${won(a.price)}원 · 목표까지 ${gap > 0 ? '+' : ''}${gap.toFixed(2)}%`}
                  </small>
                </div>
                <div className="my-alert-actions">
                  {sent && (
                    <button type="button" onClick={() => act(`/api/alerts/${a.alertId}/reactivate`, 'POST')}>다시 켜기</button>
                  )}
                  <button type="button" onClick={() => act(`/api/alerts/${a.alertId}`, 'DELETE')}>삭제</button>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {msg && <p className="alert-msg alert-msg-error">{msg}</p>}
    </section>
  )
}

export default MyAlertsBox
