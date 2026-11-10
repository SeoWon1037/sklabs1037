// ============================================
// ⏳ 그때 샀더라면 (주소: /whatif, 로그인 없이 누구나)
// - 종목 · 언제 · 얼마를 고르면 "지금 얼마가 됐을지"를 보여줌 → 이미지 카드 · 링크로 공유
// - 주소에 조건을 남김 (/whatif?s=005930&d=2024-11-08&a=1000000) → 공유받은 사람도 같은 결과
// - 계산은 서버가 종가로 함 (산 날 = 고른 날 또는 그 뒤 첫 거래일). 배당 · 세금 · 수수료 제외
//   액면분할처럼 가격 기준이 바뀐 날이 끼면 답 대신 "이 날부터 다시" 안내
// - 공유 주소는 /api/og/whatif?… (카톡 미리보기에 결과가 보이게 서버가 만든 작은 페이지 → 열면 이 화면으로)
// - 백엔드: GET /api/whatif?symbol=&from=&amount= , 종목 검색 GET /api/stock/search?q=
// ============================================
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { drawWhatIfCard, saveCanvas } from '../../components/me/shareImage'
import './WhatIfPage.css'

const PICKS = [['005930', '삼성전자'], ['000660', 'SK하이닉스'], ['035420', 'NAVER'], ['035720', '카카오'], ['005380', '현대차'], ['069500', 'KODEX 200']]
const PERIODS = [['1m', '1달 전', 1], ['3m', '3달 전', 3], ['6m', '6달 전', 6], ['1y', '1년 전', 12], ['3y', '3년 전', 36], ['5y', '5년 전', 60]]
const AMOUNTS = [[100000, '10만 원'], [1000000, '100만 원'], [10000000, '1,000만 원']]

const pad = (n) => String(n).padStart(2, '0')
const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
// 오늘에서 달 수만큼 앞 (그 달에 같은 날이 없으면 말일로)
const monthsAgo = (months) => {
  const now = new Date()
  const d = new Date(now.getFullYear(), now.getMonth() - months, 1)
  d.setDate(Math.min(now.getDate(), new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()))
  return iso(d)
}
const daysAgo = (days) => {
  const d = new Date()
  d.setDate(d.getDate() - days)
  return iso(d)
}

const won = (n) => `${Math.round(Number(n)).toLocaleString('ko-KR')}원`
const dayText = (d) => `${Number(d.slice(0, 4))}년 ${Number(d.slice(5, 7))}월 ${Number(d.slice(8, 10))}일`
const signed = (n) => `${Number(n) >= 0 ? '+' : ''}${Number(n).toFixed(2)}%`
const heldText = (days) => {
  if (days < 30) return `${days}일`
  const months = Math.round(days / 30.44)
  return months >= 12 ? `${Math.floor(months / 12)}년${months % 12 ? ` ${months % 12}개월` : ''}` : `${months}개월`
}
// 이름 뒤 조사: 받침이 있으면 "을", 없으면 "를" (한글·숫자가 아니면 "을(를)")
const eul = (name) => {
  const ch = String(name).trim().slice(-1)
  const code = ch.charCodeAt(0)
  if (code >= 0xac00 && code <= 0xd7a3) return (code - 0xac00) % 28 ? '을' : '를'
  if (/[0-9]/.test(ch)) return '013678'.includes(ch) ? '을' : '를'
  return '을(를)'
}

// 가치 변화 선 그래프 (넣은 돈은 점선)
function ValueChart({ series, amount, up }) {

  const W = 640
  const H = 220
  const P = 8
  const values = series.map((p) => Number(p.v))
  const min = Math.min(...values, amount)
  const max = Math.max(...values, amount)
  const span = max - min || 1
  const x = (i) => P + (i / Math.max(1, series.length - 1)) * (W - P * 2)
  const y = (v) => P + (1 - (v - min) / span) * (H - P * 2)
  const line = series.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(Number(p.v)).toFixed(1)}`).join(' ')
  const last = series.length - 1

  return (
    <svg className={`wi-chart ${up ? 'wi-up' : 'wi-down'}`} viewBox={`0 0 ${W} ${H}`} role="img"
      aria-label={`${series[0].d}부터 ${series[last].d}까지 가치 변화. 시작 ${won(amount)}, 지금 ${won(values[last])}`}>
      <path className="wi-chart-area" d={`${line} L${x(last).toFixed(1)},${H - P} L${x(0).toFixed(1)},${H - P} Z`} />
      <line className="wi-chart-base" x1={P} x2={W - P} y1={y(amount)} y2={y(amount)} />
      <path className="wi-chart-line" d={line} />
      <circle className="wi-chart-dot" cx={x(last)} cy={y(values[last])} r="5" />
    </svg>
  )
}

function WhatIfPage() {

  const [params, setParams] = useSearchParams()
  const [stock, setStock] = useState({ symbol: params.get('s') || PICKS[0][0], name: params.get('s') ? '' : PICKS[0][1] })
  const [date, setDate] = useState(params.get('d') || monthsAgo(12))
  const [amount, setAmount] = useState(Number(params.get('a')) || 1000000)
  const [query, setQuery] = useState('')
  const [found, setFound] = useState([])
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)   // { message, retryFrom? }
  // 처음 들어올 때 주소에 있던 조건 (공유받은 링크)
  const [initial] = useState(() => ({ s: params.get('s'), d: params.get('d'), a: Number(params.get('a')) || 1000000 }))
  const [busy, setBusy] = useState(Boolean(initial.s && initial.d))
  const [toast, setToast] = useState('')

  const minDate = useMemo(() => monthsAgo(120 - 1), [])
  const maxDate = useMemo(() => daysAgo(1), [])

  // 서버에 계산 요청 (결과는 응답이 온 뒤에만 화면에 반영)
  const load = useCallback((symbol, from, money) => {
    return fetch(`/api/whatif?symbol=${encodeURIComponent(symbol)}&from=${from}&amount=${money}`)
      .then((r) => r.json())
      .then((j) => {
        if (!j.success) {
          setResult(null)
          return setError({ message: j.message || '계산하지 못했어요.', retryFrom: j.retryFrom })
        }
        setError(null)
        setResult(j)
        setStock({ symbol: j.stock.symbol, name: j.stock.name })
        setParams({ s: j.stock.symbol, d: from, a: String(money) }, { replace: true })
      })
      .catch(() => setError({ message: '서버에 연결하지 못했어요.' }))
      .finally(() => setBusy(false))
  }, [setParams])

  const run = (symbol, from, money) => {
    setBusy(true)
    setError(null)
    load(symbol, from, money)
  }

  // 공유받은 주소로 들어오면 바로 계산 (처음 한 번)
  useEffect(() => {
    if (initial.s && initial.d) load(initial.s, initial.d, initial.a)
  }, [initial, load])

  // 종목 검색 (0.25초 쉬었다가)
  const keyword = query.trim()
  useEffect(() => {
    if (keyword.length < 1) return undefined
    let ignore = false
    const t = setTimeout(() => {
      fetch(`/api/stock/search?q=${encodeURIComponent(keyword)}`)
        .then((r) => r.json())
        .then((j) => { if (!ignore) setFound(j.success ? j.stocks.slice(0, 8) : []) })
        .catch(() => { if (!ignore) setFound([]) })
    }, 250)
    return () => {
      ignore = true
      clearTimeout(t)
    }
  }, [keyword])

  const pick = (symbol, name) => {
    setStock({ symbol, name })
    setQuery('')
    setFound([])
  }

  const submit = (e) => {
    e.preventDefault()
    run(stock.symbol, date, amount)
  }

  const flash = (msg) => {
    setToast(msg)
    setTimeout(() => setToast(''), 1800)
  }
  // 공유용 주소: 카톡에 붙이면 결과가 담긴 미리보기 카드가 뜨고, 누르면 이 화면으로 옴 (서버 OgController)
  const shareUrl = result ? `${window.location.origin}/api/og/whatif?s=${result.stock.symbol}&d=${params.get('d') || result.buyDate}&a=${result.amount}` : ''
  const shareText = result ? `${dayText(result.buyDate)}에 ${result.stock.name}${eul(result.stock.name)} ${won(result.amount)}어치 샀더라면 지금 ${won(result.value)} (${signed(result.rate)})` : ''
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(`${shareText} ${shareUrl}`)
      flash('링크를 복사했어요')
    } catch {
      flash('복사하지 못했어요. 주소창의 주소를 복사해주세요.')
    }
  }
  const share = async () => {
    try { await navigator.share({ title: '그때 샀더라면 · SKLabs', text: shareText, url: shareUrl }) } catch { /* 닫으면 그대로 */ }
  }
  const saveImage = () => saveCanvas(drawWhatIfCard(result), `sklabs-whatif-${result.stock.symbol}.png`, `${shareText} ${shareUrl}`)

  const up = result ? Number(result.profit) >= 0 : true

  return (
    <main className="wi-page">
      <header className="wi-head">
        <h1>⏳ 그때 샀더라면</h1>
        <p>그때 그 주식을 샀다면 지금 얼마가 됐을까요? 종목과 날짜, 금액만 고르면 바로 알려드려요.</p>
      </header>

      <form className="wi-form" onSubmit={submit}>
        <fieldset className="wi-field">
          <legend>어떤 종목을</legend>
          <div className="wi-chips">
            {PICKS.map(([symbol, name]) => (
              <button key={symbol} type="button" className={stock.symbol === symbol ? 'wi-chip-on' : ''} aria-pressed={stock.symbol === symbol} onClick={() => pick(symbol, name)}>{name}</button>
            ))}
            {!PICKS.some(([symbol]) => symbol === stock.symbol) && <button type="button" className="wi-chip-on" aria-pressed="true">{stock.name || stock.symbol}</button>}
          </div>
          <div className="wi-search">
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="다른 종목 검색 (이름이나 코드)" aria-label="종목 검색" maxLength={20} />
            {keyword && found.length > 0 && (
              <ul className="wi-found">
                {found.map((s) => (
                  <li key={s.symbol}><button type="button" onClick={() => pick(s.symbol, s.name)}><b>{s.name}</b><small>{s.symbol} · {s.market}</small></button></li>
                ))}
              </ul>
            )}
          </div>
        </fieldset>

        <fieldset className="wi-field">
          <legend>언제</legend>
          <div className="wi-chips">
            {PERIODS.map(([key, label, months]) => {
              const d = monthsAgo(months)
              return <button key={key} type="button" className={date === d ? 'wi-chip-on' : ''} aria-pressed={date === d} onClick={() => setDate(d)}>{label}</button>
            })}
          </div>
          <input type="date" className="wi-date" value={date} min={minDate} max={maxDate} onChange={(e) => e.target.value && setDate(e.target.value)} aria-label="산 날짜" required />
        </fieldset>

        <fieldset className="wi-field">
          <legend>얼마나</legend>
          <div className="wi-chips">
            {AMOUNTS.map(([value, label]) => (
              <button key={value} type="button" className={amount === value ? 'wi-chip-on' : ''} aria-pressed={amount === value} onClick={() => setAmount(value)}>{label}</button>
            ))}
          </div>
          <label className="wi-amount">
            <input value={amount ? amount.toLocaleString('ko-KR') : ''} onChange={(e) => setAmount(Number(e.target.value.replace(/[^0-9]/g, '').slice(0, 11)))} inputMode="numeric" aria-label="금액" />
            <span>원</span>
          </label>
        </fieldset>

        <button type="submit" className="wi-submit" disabled={busy || amount < 10000}>{busy ? '계산하는 중...' : '지금 얼마일까? 계산하기'}</button>
        {amount > 0 && amount < 10000 && <p className="wi-muted">금액은 1만 원 이상으로 넣어주세요.</p>}
      </form>

      {error && (
        <div className="wi-error" role="alert">
          <p>{error.message}</p>
          {error.retryFrom && (
            <button type="button" className="wi-btn" onClick={() => { setDate(error.retryFrom); run(stock.symbol, error.retryFrom, amount) }}>
              {dayText(error.retryFrom)}부터 계산하기
            </button>
          )}
        </div>
      )}

      {result && (
        <section className={`wi-result ${up ? 'wi-up' : 'wi-down'}`} aria-live="polite">
          <p className="wi-ask">{dayText(result.buyDate)}에 <b>{result.stock.name}</b>{eul(result.stock.name)} {won(result.amount)}어치 샀더라면</p>
          <p className="wi-value">{won(result.value)}</p>
          <p className="wi-rate">{signed(result.rate)} <span>({up ? '+' : '−'}{won(Math.abs(result.profit))}) · {heldText(result.days)} 보유</span></p>

          {result.clamped && <p className="wi-note">ℹ️ 고른 날짜({dayText(result.askedDate)})의 시세 기록이 없어서, 기록이 있는 가장 이른 날({dayText(result.firstDate)})부터 계산했어요.</p>}

          <ValueChart series={result.series} amount={result.amount} up={up} />
          <p className="wi-chart-cap"><span>{result.buyDate}</span><span>점선: 넣은 돈 {won(result.amount)}</span><span>{result.nowDate}</span></p>

          <dl className="wi-facts">
            <div><dt>산 날 종가</dt><dd>{won(result.buyPrice)}<small>{result.buyDate}</small></dd></div>
            <div><dt>최근 종가</dt><dd>{won(result.nowPrice)}<small>{result.nowDate}</small></dd></div>
            <div><dt>가장 높았을 때</dt><dd>{won(result.peak.value)}<small>{result.peak.date}</small></dd></div>
            <div><dt>가장 낮았을 때</dt><dd>{won(result.trough.value)}<small>{result.trough.date}</small></dd></div>
          </dl>

          {result.bench && (
            <p className="wi-bench">
              📊 같은 날 같은 돈을 <b>{result.bench.name}</b>에 넣었다면 {won(result.bench.value)} ({signed(result.bench.rate)})
              {' → '}{Number(result.rate) >= Number(result.bench.rate) ? '시장보다 잘했어요' : '시장이 더 잘했어요'}
            </p>
          )}

          <div className="wi-actions">
            <button type="button" className="wi-btn wi-btn-primary" onClick={saveImage}>🖼️ 이미지로 저장 · 공유</button>
            <button type="button" className="wi-btn" onClick={copy}>🔗 링크 복사</button>
            {typeof navigator.share === 'function' && <button type="button" className="wi-btn" onClick={share}>📤 공유</button>}
          </div>
          {toast && <p className="wi-toast" role="status">{toast}</p>}

          <div className="wi-next">
            <Link to="/mock" className="wi-btn">💰 가상 3,000만 원으로 지금부터 해보기</Link>
            <Link to={`/stock?symbol=${result.stock.symbol}`} className="wi-btn">📈 {result.stock.name} 차트 · 토론 보기</Link>
          </div>
        </section>
      )}

      <p className="wi-foot">
        계산 방법: 고른 날(쉬는 날이면 그 뒤 첫 거래일)의 종가로 사서 가장 최근 거래일 종가로 계산해요. 소수점 주식까지 샀다고 가정하고, 배당 · 세금 · 수수료는 넣지 않았어요.
        액면분할처럼 가격 기준이 바뀐 날이 끼어 있으면 틀린 답을 내지 않도록 계산하지 않아요. 과거 수익률이 앞으로의 수익을 뜻하지 않아요.
      </p>
    </main>
  )
}

export default WhatIfPage
