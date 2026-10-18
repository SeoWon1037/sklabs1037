// ============================================
// 오늘의 숫자 (주소: /lotto) — 복권 번호 뽑기
// - 로또 6/45   : 1~45 중 서로 다른 숫자 6개 (오름차순)
// - 연금복권 720+: 1~5조 중 1개 + 0~9 숫자 6자리 (자리마다 따로 뽑음, 중복 가능)
// - 1게임 / 5게임 뽑기, 줄별 복사
// - 서버 없이 화면에서만 동작 (새로고침하면 기록 초기화)
// ============================================
import { useState } from 'react'
import './LottoPage.css'

const MAX_HISTORY = 20   // 탭마다 화면에 남겨 둘 최대 줄 수

// 탭 목록
const TABS = [
  { value: 'LOTTO', label: '로또 6/45', desc: '1부터 45까지 숫자 중 6개를 뽑아 드려요.' },
  { value: 'PENSION', label: '연금복권 720+', desc: '1~5조 중 하나와 6자리 숫자를 뽑아 드려요.' },
]

// 0 이상 max 미만 정수 (Math.random 대신 브라우저 암호화 난수 사용)
const randomInt = (max) => {
  const buf = new Uint32Array(1)
  window.crypto.getRandomValues(buf)
  return buf[0] % max
}

// 로또: 1~45 중 6개 (중복 없이, 오름차순)
const pickLotto = () => {
  const picked = new Set()
  while (picked.size < 6) {
    picked.add(randomInt(45) + 1)
  }
  return [...picked].sort((a, b) => a - b)
}

// 연금복권: 조(1~5) + 6자리 (각 자리 0~9)
const pickPension = () => ({
  group: randomInt(5) + 1,
  digits: Array.from({ length: 6 }, () => randomInt(10)),
})

// 로또 번호 구간별 공 색 (1~10 노랑, 11~20 파랑, 21~30 빨강, 31~40 회색, 41~45 초록)
const ballClass = (n) => {
  if (n <= 10) return 'ball-yellow'
  if (n <= 20) return 'ball-blue'
  if (n <= 30) return 'ball-red'
  if (n <= 40) return 'ball-gray'
  return 'ball-green'
}

// 복사할 글자
const gameText = (type, game) => (type === 'LOTTO'
  ? game.numbers.join(', ')
  : `${game.group}조 ${game.digits.join('')}`)

// 오늘 날짜 "2026년 10월 18일 (일)"
const todayText = () => {
  const d = new Date()
  const week = ['일', '월', '화', '수', '목', '금', '토'][d.getDay()]
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${week})`
}

// ----- 번호 표시 -----

function LottoBalls({ numbers, small }) {
  return (
    <div className={`lotto-balls ${small ? '' : 'lotto-balls-big'}`}>
      {numbers.map((n) => (
        <span key={n} className={`lotto-ball ${small ? 'lotto-ball-sm' : ''} ${ballClass(n)}`}>{n}</span>
      ))}
    </div>
  )
}

function PensionNumber({ game, small }) {
  return (
    <div className={`pension ${small ? 'pension-sm' : ''}`}>
      <span className="pension-group">{game.group}<small>조</small></span>
      <div className="pension-digits">
        {game.digits.map((d, i) => (
          <span key={i} className="pension-digit">{d}</span>
        ))}
      </div>
    </div>
  )
}

let nextId = 1

function LottoPage() {

  const [type, setType] = useState('LOTTO')                   // 'LOTTO' | 'PENSION'
  const [history, setHistory] = useState({ LOTTO: [], PENSION: [] })
  const [copiedId, setCopiedId] = useState(null)

  const games = history[type]
  const latest = games[0]
  const tabInfo = TABS.find((t) => t.value === type)

  // count 줄 뽑기
  const draw = (count) => {
    const newGames = Array.from({ length: count }, () => (type === 'LOTTO'
      ? { id: nextId++, numbers: pickLotto() }
      : { id: nextId++, ...pickPension() }))

    setHistory((prev) => ({ ...prev, [type]: [...newGames, ...prev[type]].slice(0, MAX_HISTORY) }))
  }

  // 현재 탭 기록 지우기
  const clear = () => setHistory((prev) => ({ ...prev, [type]: [] }))

  // 한 줄 복사
  const copyGame = (game) => {
    navigator.clipboard?.writeText(gameText(type, game))
      .then(() => {
        setCopiedId(game.id)
        setTimeout(() => setCopiedId(null), 1500)
      })
      .catch(() => {})
  }

  return (
    <main className="lotto">

      <section className="lotto-head">
        <p className="lotto-date">{todayText()}</p>
        <h1 className="lotto-title">오늘의 숫자</h1>
      </section>

      {/* 탭 */}
      <div className="lotto-tabs">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            className={`lotto-tab ${type === t.value ? 'lotto-tab-active' : ''}`}
            onClick={() => setType(t.value)}
          >
            {t.label}
          </button>
        ))}
      </div>
      <p className="lotto-desc">{tabInfo.desc}</p>

      {/* 가장 최근에 뽑은 번호 */}
      <section className="lotto-box lotto-main">
        {!latest && <p className="lotto-empty">아래 버튼을 눌러 번호를 뽑아보세요.</p>}
        {latest && type === 'LOTTO' && <LottoBalls key={latest.id} numbers={latest.numbers} />}
        {latest && type === 'PENSION' && <PensionNumber key={latest.id} game={latest} />}

        <div className="lotto-actions">
          <button type="button" className="btn btn-primary lotto-btn" onClick={() => draw(1)}>1게임 뽑기</button>
          <button type="button" className="btn lotto-btn lotto-btn-sub" onClick={() => draw(5)}>5게임 뽑기</button>
        </div>
      </section>

      {/* 뽑은 기록 */}
      {games.length > 0 && (
        <section className="lotto-box">
          <div className="lotto-list-head">
            <h2 className="lotto-list-title">뽑은 번호</h2>
            <button type="button" className="lotto-clear" onClick={clear}>지우기</button>
          </div>
          <ul className="lotto-list">
            {games.map((g, i) => (
              <li key={g.id} className="lotto-row">
                <span className="lotto-row-no">{games.length - i}</span>
                <div className="lotto-row-body">
                  {type === 'LOTTO' ? <LottoBalls numbers={g.numbers} small /> : <PensionNumber game={g} small />}
                </div>
                <button type="button" className="lotto-copy" onClick={() => copyGame(g)}>
                  {copiedId === g.id ? '복사됨' : '복사'}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <p className="lotto-notice">
        재미로만 봐 주세요. 무작위로 뽑은 번호이며, 어떤 조합이든 당첨 확률은 같습니다.
      </p>

    </main>
  )
}

export default LottoPage