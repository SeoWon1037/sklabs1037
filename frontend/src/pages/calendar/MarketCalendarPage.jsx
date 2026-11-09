// ============================================
// 📅 증시 일정 (주소: /calendar, 로그인 없이 누구나)
// - 다가오는 일정 (D-day) · 달력 (날짜를 누르면 그날 일정)
// - 휴장일 · 🇺🇸 FOMC 금리 결정(한국 시간) · 🇰🇷 한국은행 금통위 · 선물·옵션 만기일(동시만기 = 네 마녀의 날)
// - 백엔드: GET /api/market/calendar?month=YYYY-MM (MarketCalendar: 발표된 일정 표 + 만기일 규칙)
// ============================================
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import './MarketCalendarPage.css'

const KINDS = {
  HOLIDAY: { label: '휴장', cls: 'mc-holiday' },
  FOMC: { label: 'FOMC', cls: 'mc-fomc' },
  BOK: { label: '금통위', cls: 'mc-bok' },
  EXPIRY: { label: '만기', cls: 'mc-expiry' },
  OPEN: { label: '개장', cls: 'mc-open' },
}
const WEEK = ['일', '월', '화', '수', '목', '금', '토']

const parse = (s) => {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d))
}
const md = (s) => {
  const d = parse(s)
  return `${d.getUTCMonth() + 1}/${d.getUTCDate()} (${WEEK[d.getUTCDay()]})`
}
const dday = (date, today) => {
  const n = Math.round((parse(date) - parse(today)) / 86400000)
  return n === 0 ? '오늘' : n === 1 ? '내일' : `D-${n}`
}
const shift = (month, n) => {
  const [y, m] = month.split('-').map(Number)
  const d = new Date(Date.UTC(y, m - 1 + n, 1))
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
}

function EventRow({ e, today }) {
  const k = KINDS[e.kind]
  return (
    <li className={`mc-event ${k.cls} ${e.big ? 'mc-big' : ''}`}>
      <span className="mc-when">
        <b>{md(e.date)}</b>
        {today && <em>{dday(e.date, today)}</em>}
      </span>
      <div className="mc-what">
        <strong>{e.title}{e.time && <small> · {e.time}</small>}</strong>
        <p>{e.detail}</p>
      </div>
    </li>
  )
}

function MarketCalendarPage() {

  const [params, setParams] = useSearchParams()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [picked, setPicked] = useState('')
  const month = params.get('month') ?? ''

  useEffect(() => {
    let ignore = false
    fetch(`/api/market/calendar${month ? `?month=${month}` : ''}`)
      .then((r) => r.json())
      .then((j) => {
        if (ignore) return
        if (!j.success) throw new Error(j.message)
        setData(j)
        setError('')
      })
      .catch((e) => { if (!ignore) setError(e.message || '일정을 불러오지 못했어요.') })
    return () => { ignore = true }
  }, [month])

  const cells = useMemo(() => {
    if (!data) return []
    const first = parse(`${data.month}-01`)
    const days = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0)).getUTCDate()
    const byDay = new Map()
    data.events.forEach((e) => byDay.set(e.date, [...(byDay.get(e.date) ?? []), e]))
    const out = Array.from({ length: first.getUTCDay() }, (_, i) => ({ key: `b${i}` }))
    for (let d = 1; d <= days; d++) {
      const date = `${data.month}-${String(d).padStart(2, '0')}`
      out.push({ key: date, date, d, dow: (first.getUTCDay() + d - 1) % 7, events: byDay.get(date) ?? [] })
    }
    return out
  }, [data])

  const go = (n) => {
    setPicked('')
    setParams({ month: shift(data.month, n) }, { replace: true })
  }

  const pickedEvents = picked ? (data?.events ?? []).filter((e) => e.date === picked) : []
  const [y, m] = (data?.month ?? '').split('-')

  return (
    <main className="mc-page">
      <header className="mc-head">
        <h1>📅 증시 일정</h1>
        {data && (
          <p className={`mc-today ${data.tradingToday ? 'mc-today-open' : 'mc-today-closed'}`}>
            오늘 {md(data.today)} · {data.tradingToday ? '장이 열리는 날이에요 (09:00 ~ 15:30)' : '증시가 쉬는 날이에요'}
          </p>
        )}
      </header>

      {error && <p className="mc-error" role="alert">{error}</p>}
      {!error && !data && <p className="mc-muted">불러오는 중...</p>}

      {data && (
        <div className="mc-grid">
          <section className="mc-card mc-upcoming" aria-label="다가오는 일정">
            <h2>다가오는 일정</h2>
            {data.upcoming.length === 0
              ? <p className="mc-muted">두 달 안에 정해진 일정이 없어요.</p>
              : <ul className="mc-list">{data.upcoming.map((e) => <EventRow key={`${e.date}${e.kind}`} e={e} today={data.today} />)}</ul>}
          </section>

          <section className="mc-card mc-month" aria-label={`${y}년 ${Number(m)}월 달력`}>
            <div className="mc-nav">
              <button type="button" onClick={() => go(-1)} disabled={data.month <= data.minMonth} aria-label="이전 달">‹</button>
              <h2>{y}년 {Number(m)}월</h2>
              <button type="button" onClick={() => go(1)} disabled={data.month >= data.maxMonth} aria-label="다음 달">›</button>
            </div>

            <div className="mc-cal" role="grid">
              {WEEK.map((w, i) => <span key={w} className={`mc-dow ${i === 0 ? 'mc-sun' : i === 6 ? 'mc-sat' : ''}`}>{w}</span>)}
              {cells.map((c) => c.date ? (
                <button
                  key={c.key}
                  type="button"
                  className={[
                    'mc-day',
                    c.date === data.today ? 'mc-is-today' : '',
                    c.date === picked ? 'mc-picked' : '',
                    c.dow === 0 || c.events.some((e) => e.kind === 'HOLIDAY') ? 'mc-sun' : c.dow === 6 ? 'mc-sat' : '',
                  ].join(' ')}
                  onClick={() => setPicked(c.date === picked ? '' : c.date)}
                  aria-label={`${c.d}일${c.events.length ? ` · ${c.events.map((e) => e.title).join(', ')}` : ''}`}
                >
                  <span className="mc-num">{c.d}</span>
                  <span className="mc-tags">
                    {c.events.map((e) => <i key={e.kind} className={KINDS[e.kind].cls}>{KINDS[e.kind].label}</i>)}
                  </span>
                </button>
              ) : <span key={c.key} />)}
            </div>

            <div className="mc-legend">
              {Object.entries(KINDS).map(([k, v]) => <span key={k} className={v.cls}><i />{v.label}</span>)}
            </div>

            {picked && (
              <div className="mc-picked-list">
                {pickedEvents.length === 0
                  ? <p className="mc-muted">{md(picked)} · 정해진 일정이 없어요.</p>
                  : <ul className="mc-list">{pickedEvents.map((e) => <EventRow key={e.kind} e={e} />)}</ul>}
              </div>
            )}

            {!picked && data.events.length > 0 && (
              <ul className="mc-list mc-month-list">
                {data.events.map((e) => <EventRow key={`${e.date}${e.kind}`} e={e} />)}
              </ul>
            )}
          </section>
        </div>
      )}

      <p className="mc-note">
        FOMC는 미국 발표 시각(오후 2시)을 한국 시간으로 바꿔서 보여줘요. 휴장일·회의 날짜는 발표 기관 사정으로 바뀔 수 있어요.
        시장이 어떻게 반응할지 궁금하면 <Link to="/ai?q=이번 FOMC가 한국 증시에 어떤 영향을 줄까?">AI 비서</Link>에게 물어보세요.
      </p>
    </main>
  )
}

export default MarketCalendarPage
