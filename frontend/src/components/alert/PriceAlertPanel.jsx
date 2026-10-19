// ============================================
// 목표가 알림 창 (주식 종목 화면에서 🔔 알림 버튼을 누르면 종목 헤더 아래에 열림)
// - [이상 | 이하] + 목표가 (± 버튼은 한 호가씩, 현재가 ±5% / ±10% 빠른 선택)
// - 아래에 이 종목에 걸어둔 내 알림 목록 (삭제)
// - 백엔드 API
//   GET    /api/alerts?stockId=1        이 종목 내 알림
//   POST   /api/alerts { stockId, condition, targetPrice }
//   DELETE /api/alerts/{alertId}
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './PriceAlertPanel.css'

// 한국 주식 호가 단위 (백엔드 MockMarketHours.tickSize와 같은 규칙)
const tickSize = (p) => (p < 2000 ? 1 : p < 5000 ? 5 : p < 20000 ? 10 : p < 50000 ? 50 : p < 200000 ? 100 : p < 500000 ? 500 : 1000)
const roundTick = (p) => {
  const t = tickSize(p)
  return Math.round(p / t) * t
}
const won = (v) => Math.round(Number(v)).toLocaleString('ko-KR')

const QUICK = [-10, -5, 5, 10]

function PriceAlertPanel({ stockId, stockName, price, onClose }) {

  const [alerts, setAlerts] = useState(null)       // null = 불러오는 중
  const [loggedIn, setLoggedIn] = useState(true)
  const [condition, setCondition] = useState('ABOVE')
  const [target, setTarget] = useState(() => String(roundTick(Number(price) * 1.05)))
  const [msg, setMsg] = useState(null)              // { type, text }
  const [saving, setSaving] = useState(false)

  const load = useCallback(() => {
    return fetch(`/api/alerts?stockId=${stockId}`)
      .then((res) => res.json())
      .then((json) => {
        setLoggedIn(Boolean(json.loggedIn))
        setAlerts(json.success ? json.alerts : [])
      })
      .catch(() => setAlerts([]))
  }, [stockId])

  useEffect(() => { load() }, [load])

  const targetNum = Number(target) || 0
  const gap = price ? ((targetNum - Number(price)) / Number(price)) * 100 : 0

  // 빠른 선택: + 는 "이상", - 는 "이하"
  const pickQuick = (pct) => {
    setCondition(pct > 0 ? 'ABOVE' : 'BELOW')
    setTarget(String(roundTick(Number(price) * (1 + pct / 100))))
    setMsg(null)
  }

  const step = (dir) => {
    const now = targetNum || Math.round(Number(price))
    const t = tickSize(dir > 0 ? now : now - 1)
    const next = dir > 0 ? Math.floor(now / t) * t + t : Math.ceil(now / t) * t - t
    setTarget(String(Math.max(t, next)))
    setMsg(null)
  }

  const submit = async () => {
    if (targetNum < 1 || saving) return
    setSaving(true)
    setMsg(null)
    try {
      const res = await fetch('/api/alerts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ stockId, condition, targetPrice: targetNum }),
      })
      const json = await res.json()
      if (!json.success) {
        setMsg({ type: 'error', text: json.message })
        return
      }
      setMsg({ type: 'ok', text: `${stockName}이(가) ${won(targetNum)}원 ${condition === 'ABOVE' ? '이상' : '이하'}가 되면 메일로 알려드릴게요.` })
      await load()
    } catch {
      setMsg({ type: 'error', text: '서버에 연결할 수 없습니다.' })
    } finally {
      setSaving(false)
    }
  }

  const remove = async (alertId) => {
    await fetch(`/api/alerts/${alertId}`, { method: 'DELETE' }).catch(() => {})
    load()
  }

  return (
    <section className="alert-panel">
      <div className="alert-panel-head">
        <strong>🔔 {stockName} 가격 알림</strong>
        <button type="button" className="alert-panel-close" onClick={onClose} aria-label="닫기">✕</button>
      </div>

      {!loggedIn ? (
        <p className="alert-panel-desc">
          로그인하면 목표가에 도달했을 때 메일로 알려드려요. <Link to="/login">로그인 →</Link>
        </p>
      ) : (
        <>
          <div className="alert-form">
            <div className="alert-seg">
              {[['ABOVE', '이상이 되면'], ['BELOW', '이하가 되면']].map(([v, label]) => (
                <button
                  key={v}
                  type="button"
                  className={`${condition === v ? 'alert-seg-active' : ''} ${v === 'ABOVE' ? 'alert-up' : 'alert-down'}`}
                  onClick={() => { setCondition(v); setMsg(null) }}
                >
                  {label}
                </button>
              ))}
            </div>

            <div className="alert-price">
              <button type="button" onClick={() => step(-1)} aria-label="한 호가 내리기">−</button>
              <input
                type="number"
                inputMode="numeric"
                value={target}
                onChange={(e) => { setTarget(e.target.value.replace(/[^0-9]/g, '')); setMsg(null) }}
                aria-label="목표가"
              />
              <span>원</span>
              <button type="button" onClick={() => step(1)} aria-label="한 호가 올리기">+</button>
            </div>

            <button type="button" className="alert-submit" onClick={submit} disabled={targetNum < 1 || saving}>
              {saving ? '저장 중...' : '알림 받기'}
            </button>
          </div>

          <div className="alert-quick">
            {QUICK.map((p) => (
              <button key={p} type="button" onClick={() => pickQuick(p)} className={p > 0 ? 'alert-up' : 'alert-down'}>
                {p > 0 ? '+' : ''}{p}%
              </button>
            ))}
            <span className="alert-gap">
              현재가 {won(price)}원 대비 <b className={gap > 0 ? 'alert-up' : gap < 0 ? 'alert-down' : ''}>{gap > 0 ? '+' : ''}{gap.toFixed(2)}%</b>
            </span>
          </div>

          {msg && <p className={`alert-msg alert-msg-${msg.type}`}>{msg.text}</p>}

          {/* 이 종목에 걸어둔 알림 */}
          {alerts && alerts.length > 0 && (
            <ul className="alert-list">
              {alerts.map((a) => (
                <li key={a.alertId} className={a.status === 'SENT' ? 'alert-sent' : ''}>
                  <span className={`alert-badge ${a.status === 'SENT' ? 'alert-badge-sent' : ''}`}>
                    {a.status === 'SENT' ? '보냄' : '켜짐'}
                  </span>
                  <span className="alert-item-text">
                    {won(a.targetPrice)}원 <b className={a.condition === 'ABOVE' ? 'alert-up' : 'alert-down'}>{a.condition === 'ABOVE' ? '이상' : '이하'}</b>
                    {a.status === 'SENT' && a.sentPrice && <small> · {won(a.sentPrice)}원에 알림</small>}
                  </span>
                  <button type="button" onClick={() => remove(a.alertId)}>삭제</button>
                </li>
              ))}
            </ul>
          )}

          <p className="alert-note">
            정규장(평일 09:00~15:30)에 1분마다 확인해서, 조건을 넘으면 가입한 이메일로 1번 보내드려요.
            {' '}<Link to="/mypage">마이페이지</Link>에서 전체 알림을 관리할 수 있어요.
          </p>
        </>
      )}
    </section>
  )
}

export default PriceAlertPanel
