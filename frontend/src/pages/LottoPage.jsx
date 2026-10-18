// ============================================
// 오늘의 숫자 (주소: /lotto) — 로또 번호 뽑기
// - 1~45 중 서로 다른 숫자 6개를 뽑아 오름차순으로 표시
// - 1게임 / 5게임 뽑기, 줄별 복사
// - 서버 없이 화면에서만 동작 (새로고침하면 기록 초기화)
// ============================================
import { useState } from 'react'
import './LottoPage.css'

const MAX_NUMBER = 45
const PICK_COUNT = 6
const MAX_HISTORY = 20   // 화면에 남겨 둘 최대 줄 수

// 0 이상 max 미만 정수 (Math.random 대신 브라우저 암호화 난수 사용)
const randomInt = (max) => {
  const buf = new Uint32Array(1)
  window.crypto.getRandomValues(buf)
  return buf[0] % max
}

// 1~45 중 6개 (중복 없이, 오름차순)
const pickNumbers = () => {
  const picked = new Set()
  while (picked.size < PICK_COUNT) {
    picked.add(randomInt(MAX_NUMBER) + 1)
  }
  return [...picked].sort((a, b) => a - b)
}

// 번호 구간별 공 색 (1~10 노랑, 11~20 파랑, 21~30 빨강, 31~40 회색, 41~45 초록)
const ballClass = (n) => {
  if (n <= 10) return 'ball-yellow'
  if (n <= 20) return 'ball-blue'
  if (n <= 30) return 'ball-red'
  if (n <= 40) return 'ball-gray'
  return 'ball-green'
}

// 오늘 날짜 "2026년 10월 18일 (일)"
const todayText = () => {
  const d = new Date()
  const week = ['일', '월', '화', '수', '목', '금', '토'][d.getDay()]
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${week})`
}

let nextId = 1

function LottoPage() {

  // 뽑은 줄 목록 (최신이 위)
  const [games, setGames] = useState([])
  const [copiedId, setCopiedId] = useState(null)

  // count 줄 뽑기
  const draw = (count) => {
    const newGames = Array.from({ length: count }, () => ({ id: nextId++, numbers: pickNumbers() }))
    setGames((prev) => [...newGames, ...prev].slice(0, MAX_HISTORY))
  }

  // 한 줄 복사
  const copyGame = (game) => {
    navigator.clipboard?.writeText(game.numbers.join(', '))
      .then(() => {
        setCopiedId(game.id)
        setTimeout(() => setCopiedId(null), 1500)
      })
      .catch(() => {})
  }

  const latest = games[0]

  return (
    <main className="lotto">

      <section className="lotto-head">
        <p className="lotto-date">{todayText()}</p>
        <h1 className="lotto-title">오늘의 숫자</h1>
        <p className="lotto-desc">1부터 45까지 숫자 중 6개를 뽑아 드려요.</p>
      </section>

      {/* 가장 최근에 뽑은 번호 */}
      <section className="lotto-box lotto-main">
        {latest ? (
          <div className="lotto-balls lotto-balls-big">
            {latest.numbers.map((n) => (
              <span key={n} className={`lotto-ball ${ballClass(n)}`}>{n}</span>
            ))}
          </div>
        ) : (
          <p className="lotto-empty">아래 버튼을 눌러 번호를 뽑아보세요.</p>
        )}

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
            <button type="button" className="lotto-clear" onClick={() => setGames([])}>지우기</button>
          </div>
          <ul className="lotto-list">
            {games.map((g, i) => (
              <li key={g.id} className="lotto-row">
                <span className="lotto-row-no">{games.length - i}</span>
                <div className="lotto-balls">
                  {g.numbers.map((n) => (
                    <span key={n} className={`lotto-ball lotto-ball-sm ${ballClass(n)}`}>{n}</span>
                  ))}
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