// ============================================
// 🗓️ 내 약속 · 🔊 음성 브리핑 (주소: /plans, 로그인 필요)
// - 💕 커플과 함께: 상대의 목록 · 알림에도 나옴 (지울 수 있는 사람은 만든 사람) / 🔁 매주 반복: 12주치를 한 번에
// - 약속: 직접 추가하거나 AI 챗봇에게 "토요일 저녁 7시 강남에서 민지랑 약속 잡아줘"
//   → 하루 전 · 당일 오전 9시에 알림(+📲 푸시), 누르면 AI 챗봇이 소리로 읽어 줌
// - 음성 브리핑: 아침 7시(날씨 + 오늘 약속) · 장 마감 15:40(관심 종목) 켜기/끄기, 날씨 지역, [지금 들어보기]
// - 알림 읽어 주기: 사이트를 열어 둔 동안 새 알림이 오면 소리로 읽기 (이 브라우저에만 기억)
// - API: GET/POST /api/plans, POST /api/plans/{id}/delete, GET/POST /api/voice/settings, GET /api/voice/brief?kind=
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { canListen, canSpeak, speak, stopSpeaking } from '../../voice/speech'
import { READ_ALOUD_KEY } from '../../voice/readAloud'
import { alwaysOn, onAlwaysChange, setAlways } from '../../voice/alwaysListen'
import { onAiActed } from '../../voice/aiActed'
import './PlansPage.css'

// 오늘 날짜 (한국 시간, YYYY-MM-DD) — 날짜 칸의 가장 이른 값
const todayKst = () => new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10)

const NEAR = ['오늘', '내일', '모레']

function PlansPage() {

  const [plans, setPlans] = useState(null)        // null = 불러오는 중
  const [needLogin, setNeedLogin] = useState(false)
  const [settings, setSettings] = useState(null)
  const [form, setForm] = useState(() => ({ title: '', date: todayKst(), time: '', place: '', shared: false, weekly: false }))
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const [readAloud, setReadAloud] = useState(() => {
    try { return localStorage.getItem(READ_ALOUD_KEY) === '1' } catch { return false }
  })

  const [always, setAlwaysState] = useState(alwaysOn)
  useEffect(() => onAlwaysChange(setAlwaysState), [])

  const load = useCallback(() => {
    fetch('/api/plans')
      .then((res) => {
        if (res.status === 401) setNeedLogin(true)
        return res.json()
      })
      .then((json) => setPlans(json.success ? json.plans : []))
      .catch(() => setPlans([]))
  }, [])

  // 🎤 챗봇이 약속을 잡거나 지웠으면 목록을 다시
  useEffect(() => onAiActed(load), [load])

  useEffect(() => {
    load()
    fetch('/api/voice/settings')
      .then((res) => res.json())
      .then((json) => { if (json.success) setSettings(json) })
      .catch(() => {})
    return () => stopSpeaking()
  }, [load])

  const add = async (e) => {
    e.preventDefault()
    if (busy || form.title.trim() === '') return
    setBusy(true)
    setMsg('')
    try {
      const res = await fetch('/api/plans', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      })
      const json = await res.json()
      if (json.success) {
        setForm((f) => ({ ...f, title: '', time: '', place: '', shared: false, weekly: false }))
        setMsg(`저장했어요: ${json.plan.text}`)
        load()
      } else {
        setMsg(json.message)
      }
    } catch {
      setMsg('서버에 연결할 수 없습니다.')
    } finally {
      setBusy(false)
    }
  }

  // all: 🔁 반복 약속을 이 약속부터 뒤의 것까지 모두
  const remove = async (p, all = false) => {
    if (!window.confirm(all ? `"${p.title}" 반복 약속을 이 날부터 모두 지울까요?` : `"${p.title}" 약속을 지울까요?`)) return
    const res = await fetch(`/api/plans/${p.id}/delete${all ? '?all=true' : ''}`, { method: 'POST' }).then((r) => r.json()).catch(() => ({}))
    if (res.success) load()
    else setMsg(res.message ?? '지우지 못했어요.')
  }

  const save = async (next) => {
    setSettings((s) => ({ ...s, ...next }))
    const body = { morning: settings.morning, close: settings.close, region: settings.region, ...next }
    const json = await fetch('/api/voice/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }).then((r) => r.json()).catch(() => ({}))
    if (json.success) setSettings(json)
    else setMsg(json.message ?? '설정을 저장하지 못했어요.')
  }

  // 지금 들어보기 (버튼을 누른 순간이라 소리가 바로 남)
  const listen = async (kind) => {
    const json = await fetch(`/api/voice/brief?kind=${kind}`).then((r) => r.json()).catch(() => ({}))
    if (!json.success) return setMsg(json.message ?? '브리핑을 가져오지 못했어요.')
    setMsg(json.text)
    speak(json.text)
  }

  const toggleReadAloud = () => {
    const next = !readAloud
    setReadAloud(next)
    try { localStorage.setItem(READ_ALOUD_KEY, next ? '1' : '0') } catch { /* 기억 못 해도 됨 */ }
    if (next) speak('이제 새 알림이 오면 소리로 읽어 드릴게요.')
    else stopSpeaking()
  }

  if (needLogin) {
    return (
      <main className="plans-page">
        <h1>🗓️ 내 약속 · 음성 브리핑</h1>
        <p className="plans-muted">약속을 저장하고 알림을 받으려면 로그인이 필요해요.</p>
        <Link to="/login" className="btn btn-primary plans-login">로그인하러 가기</Link>
      </main>
    )
  }

  return (
    <main className="plans-page">
      <header className="plans-head">
        <h1>🗓️ 내 약속 · 음성 브리핑</h1>
        <p className="plans-muted">약속은 하루 전과 당일 오전 9시에 알려드려요. 알림을 누르면 AI 챗봇이 소리로 읽어 줘요.</p>
      </header>

      <div className="plans-grid">
        <section className="plans-card">
          <h2>약속</h2>

          <form className="plans-form" onSubmit={add}>
            <label className="plans-wide">
              <span>무슨 약속</span>
              <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} maxLength={60} placeholder="예: 민지랑 저녁" />
            </label>
            <label>
              <span>날짜</span>
              <input type="date" value={form.date} min={todayKst()} onChange={(e) => setForm({ ...form, date: e.target.value })} required />
            </label>
            <label>
              <span>시간 (선택)</span>
              <input type="time" value={form.time} onChange={(e) => setForm({ ...form, time: e.target.value })} />
            </label>
            <label className="plans-wide">
              <span>장소 (선택)</span>
              <input value={form.place} onChange={(e) => setForm({ ...form, place: e.target.value })} maxLength={40} placeholder="예: 강남" />
            </label>
            <div className="plans-checks plans-wide">
              <label><input type="checkbox" checked={form.shared} onChange={(e) => setForm({ ...form, shared: e.target.checked })} /> 💕 커플과 함께 <small>상대에게도 보이고 알림이 가요</small></label>
              <label><input type="checkbox" checked={form.weekly} onChange={(e) => setForm({ ...form, weekly: e.target.checked })} /> 🔁 매주 반복 <small>12주치를 만들어요</small></label>
            </div>
            <button type="submit" className="btn btn-primary plans-wide" disabled={busy || form.title.trim() === ''}>약속 추가</button>
          </form>

          <p className="plans-tip">
            말로도 돼요: <Link to="/ai?q=토요일 저녁 7시 강남에서 민지랑 약속 잡아줘">AI 챗봇에게 “토요일 저녁 7시 강남에서 민지랑 약속 잡아줘”</Link>
          </p>

          {plans === null && <p className="plans-muted">불러오는 중…</p>}
          {plans && plans.length === 0 && <p className="plans-empty">아직 약속이 없어요.</p>}
          {plans && plans.length > 0 && (
            <ul className="plans-list">
              {plans.map((p) => (
                <li key={p.id} className={p.day === '오늘' ? 'plans-today' : ''}>
                  <div className="plans-when">
                    <strong>{NEAR.includes(p.day) ? p.day : `${Number(p.date.slice(5, 7))}월 ${Number(p.date.slice(8, 10))}일`}</strong>
                    <span>{NEAR.includes(p.day) ? `${p.date.slice(5).replace('-', '/')} ${p.weekday}` : `${p.weekday} · ${p.date.slice(0, 4)}`}</span>
                  </div>
                  <div className="plans-what">
                    <b>{p.shared && <span title="커플과 함께">💕 </span>}{p.repeat && <span title="매주 반복">🔁 </span>}{p.title}</b>
                    <span>{[p.time ?? '시간 미정', p.place, p.mine ? null : '상대가 만든 약속'].filter(Boolean).join(' · ')}</span>
                  </div>
                  {p.mine && p.repeat && <button type="button" className="plans-del plans-del-all" onClick={() => remove(p, true)} aria-label={`${p.title} 반복 약속 모두 지우기`}>전체</button>}
                  {p.mine && <button type="button" className="plans-del" onClick={() => remove(p)} aria-label={`${p.title} 약속 지우기`}>✕</button>}
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="plans-card">
          <h2>🔊 음성 브리핑</h2>

          {settings === null ? <p className="plans-muted">불러오는 중…</p> : (
            <>
              <div className="plans-set">
                <div>
                  <b>🌤️ 아침 브리핑 · 매일 7시</b>
                  <span>오늘 날씨와 오늘 약속을 알려드려요. 비 · 눈 예보가 있으면 출퇴근길 주의도 함께요.</span>
                </div>
                <button type="button" className={`plans-switch ${settings.morning ? 'plans-on' : ''}`} onClick={() => save({ morning: !settings.morning })} aria-pressed={settings.morning}>
                  {settings.morning ? '켜짐' : '꺼짐'}
                </button>
              </div>

              <label className="plans-region">
                <span>날씨 지역</span>
                <select value={settings.region} onChange={(e) => save({ region: e.target.value })}>
                  {settings.regions.map((r) => <option key={r.code} value={r.code}>{r.name}</option>)}
                </select>
              </label>

              <div className="plans-set">
                <div>
                  <b>📈 장 마감 브리핑 · 평일 15:40</b>
                  <span>내 관심 종목이 오늘 얼마나 오르고 내렸는지 알려드려요.</span>
                </div>
                <button type="button" className={`plans-switch ${settings.close ? 'plans-on' : ''}`} onClick={() => save({ close: !settings.close })} aria-pressed={settings.close}>
                  {settings.close ? '켜짐' : '꺼짐'}
                </button>
              </div>

              {canListen() && (
                <div className="plans-set">
                  <div>
                    <b>🟢 항상 듣기</b>
                    <span>켜 두면 버튼 없이 “챗봇아, 오늘 날씨 어때?”처럼 부르면 돼요. 사이트가 열려 있는 동안만 들어요. (이 기기에서만)</span>
                  </div>
                  <button type="button" className={`plans-switch ${always ? 'plans-on' : ''}`} onClick={() => setAlways(!always)} aria-pressed={always}>
                    {always ? '켜짐' : '꺼짐'}
                  </button>
                </div>
              )}

              {canSpeak() && (
                <>
                  <div className="plans-set">
                    <div>
                      <b>📣 알림 읽어 주기</b>
                      <span>사이트를 열어 둔 동안 새 알림이 오면 소리로 읽어요. (이 기기에서만)</span>
                    </div>
                    <button type="button" className={`plans-switch ${readAloud ? 'plans-on' : ''}`} onClick={toggleReadAloud} aria-pressed={readAloud}>
                      {readAloud ? '켜짐' : '꺼짐'}
                    </button>
                  </div>

                  <div className="plans-listen">
                    <button type="button" className="btn btn-ghost" onClick={() => listen('morning')}>▶ 아침 브리핑 듣기</button>
                    <button type="button" className="btn btn-ghost" onClick={() => listen('close')}>▶ 장 마감 듣기</button>
                    <button type="button" className="btn btn-ghost" onClick={() => listen('plan')}>▶ 약속 듣기</button>
                  </div>
                </>
              )}

              <p className="plans-note">
                사이트가 꺼져 있을 때는 스스로 소리를 낼 수 없어서, 시간이 되면 알림(📲 푸시)을 보내 드려요. 알림을 누르면 바로 읽어 줘요.
                푸시는 오른쪽 위 🔔에서 켤 수 있어요. 실시간 교통 정보는 아직 준비 중이에요.
              </p>
            </>
          )}
        </section>
      </div>

      {msg && <p className="plans-msg" role="status">{msg}</p>}
    </main>
  )
}

export default PlansPage
