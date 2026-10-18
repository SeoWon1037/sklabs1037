// ============================================
// 메인 화면 (주소: /) — 네이버 메인 스타일
// - 위: 검색창(= AI 비서에게 질문) + 바로가기
// - 2×2 배치 (위·아래 줄마다 양쪽 박스 끝이 맞음)
//   · 왼쪽 위: 뉴스 (박스 안에서 스크롤)    · 오른쪽 위: 로그인 + 내 관심 종목(로그인 시) + 오늘의 시세
//   · 왼쪽 아래: 날씨                       · 오른쪽 아래: 추천 질문
// - 백엔드 API
//   GET /api/news?category=&stockId=&size=   뉴스 목록
//   GET /api/news/summary?stockId=           AI 뉴스 요약 (NewsSummaryBox)
//   GET /api/stock/dashboard?days=5          종목 시세 (탭 이름 + 시세 위젯)
//   GET /api/user/me                         로그인 상태
// ============================================
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import './MainPage.css'
import WeatherBox from '../../components/weather/WeatherBox'
import NewsSummaryBox from '../../components/news/NewsSummaryBox'
import WatchlistBox from '../../components/stock/WatchlistBox'

// 바로가기 아이콘
const SHORTCUTS = [
  { icon: '📈', label: '주식 종목', to: '/stock' },
  { icon: '🤖', label: 'AI 비서', to: '/ai' },
  { icon: '🍀', label: '오늘의 숫자', to: '/lotto' },
  { icon: '🏭', label: '기업 정보', to: `/ai?q=${encodeURIComponent('')}` },
]

// 오른쪽 추천 질문
const SUGGESTIONS = [
  '카카오랑 NAVER 현재가 비교해줘',
  '현대차 최근 일주일 거래량 추이는?',
  '최근 60일 수익률이 가장 낮은 건?',
]

// 뉴스 탭에 보여줄 종목 (종목코드, 이 순서대로 표시)
const NEWS_TAB_SYMBOLS = ['005930', '000660', '035420']   // 삼성전자, SK하이닉스, NAVER

// 한 번에 보여줄 뉴스 개수 (더보기 누르면 늘어남)
const PAGE_SIZE = 15

const formatPrice = (value) => (value == null ? '-' : Number(value).toLocaleString('ko-KR'))

// "3분 전", "2시간 전", "1일 전"
const timeAgo = (dateText, now) => {
  const minutes = Math.floor((now - new Date(dateText).getTime()) / 60000)
  if (minutes < 1) return '방금'
  if (minutes < 60) return `${minutes}분 전`
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}시간 전`
  return `${Math.floor(minutes / (60 * 24))}일 전`
}

// 탭 → 뉴스 API 주소
const newsUrl = (tab, size) => {
  if (tab === 'ALL') return `/api/news?size=${size}`
  if (tab === 'MARKET' || tab === 'SPORTS') return `/api/news?category=${tab}&size=${size}`
  return `/api/news?stockId=${tab}&size=${size}`
}

function MainPage() {

  const navigate = useNavigate()

  // 화면을 연 시각 ("n분 전" 계산 기준)
  const [now] = useState(() => Date.now())

  // ===== 검색창 =====
  const [query, setQuery] = useState('')

  // ===== 뉴스 =====
  const [tab, setTab] = useState('ALL')          // 'ALL' | 'MARKET' | 'SPORTS' | 종목 ID
  const [size, setSize] = useState(PAGE_SIZE)
  const [news, setNews] = useState({ key: null, items: [], error: '' })
  const newsKey = `${tab}-${size}`
  const newsLoading = news.key !== newsKey        // 지금 조건의 결과가 아직 안 왔으면 로딩 중

  // ===== 시세 / 로그인 =====
  const [stocks, setStocks] = useState([])
  const [baseDate, setBaseDate] = useState(null)
  const [user, setUser] = useState(null)

  // 탭이나 개수가 바뀔 때마다 뉴스 다시 불러오기
  useEffect(() => {

    let ignore = false   // 탭을 빠르게 바꿨을 때 늦게 도착한 이전 결과는 무시

    fetch(newsUrl(tab, size))
      .then((res) => res.json())
      .then((data) => {
        if (ignore) return
        setNews(data.success
          ? { key: `${tab}-${size}`, items: data.news, error: '' }
          : { key: `${tab}-${size}`, items: [], error: data.message })
      })
      .catch(() => {
        if (!ignore) setNews({ key: `${tab}-${size}`, items: [], error: '서버에 연결할 수 없습니다.' })
      })

    return () => { ignore = true }

  }, [tab, size])

  // 처음 한 번: 시세 + 로그인 상태
  useEffect(() => {

    fetch('/api/stock/dashboard?days=5')
      .then((res) => res.json())
      .then((data) => {
        if (data.success) {
          setStocks(data.stocks)
          setBaseDate(data.baseDate)
        }
      })
      .catch(() => {})

    fetch('/api/user/me')
      .then((res) => res.json())
      .then((data) => setUser(data.loggedIn ? data.user : null))
      .catch(() => setUser(null))

  }, [])

  // 탭 바꾸기 (개수는 처음으로)
  const changeTab = (value) => {
    setTab(value)
    setSize(PAGE_SIZE)
  }

  // 검색 → AI 비서로 질문 넘기기
  const handleSearch = (e) => {
    e.preventDefault()
    const q = query.trim()
    navigate(q ? `/ai?q=${encodeURIComponent(q)}` : '/ai')
  }

  // 뉴스 탭 목록: 전체, 시장, 스포츠, 주요 종목 (NEWS_TAB_SYMBOLS에 있는 종목만)
  const tabs = [
    { value: 'ALL', label: '전체' },
    { value: 'MARKET', label: '시장' },
    { value: 'SPORTS', label: '스포츠' },
    ...NEWS_TAB_SYMBOLS
      .map((symbol) => stocks.find((s) => s.symbol === symbol))
      .filter(Boolean)
      .map((s) => ({ value: String(s.stockId), label: s.name })),
  ]

  return (
    <main className="home">

      {/* ===== 검색창 ===== */}
      <section className="home-search-area">
        <div className="home-search-row">
          <span className="home-logo">SKLabs</span>

          <form className="home-search" onSubmit={handleSearch}>
            <input
              className="home-search-input"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="종목이나 시장에 대해 물어보세요"
              maxLength={500}
            />
            <button type="submit" className="home-search-btn">✨ AI</button>
          </form>
        </div>

        {/* 바로가기 */}
        <nav className="home-shortcuts">
          {SHORTCUTS.map((s) => (
            <Link key={s.label} to={s.to} className="home-shortcut">
              <span className="home-shortcut-icon">{s.icon}</span>
              <span className="home-shortcut-label">{s.label}</span>
            </Link>
          ))}
        </nav>
      </section>

      <div className="home-grid">

        {/* ===== 왼쪽 위: 뉴스 ===== */}
        <section className="home-box home-news">
          <div className="home-news-head">
            <h2 className="home-box-title">뉴스</h2>
            <div className="home-tabs">
              {tabs.map((t) => (
                <button
                  key={t.value}
                  type="button"
                  className={`home-tab ${tab === t.value ? 'home-tab-active' : ''}`}
                  onClick={() => changeTab(t.value)}
                >
                  {t.label}
                </button>
              ))}
            </div>
          </div>

          {/* AI 요약: 시장 / 종목 탭에서만 */}
          {tab !== 'ALL' && tab !== 'SPORTS' && (
            <NewsSummaryBox key={tab} stockId={tab === 'MARKET' ? null : tab} />
          )}

          {newsLoading && news.items.length === 0 && <p className="home-empty">뉴스를 불러오는 중...</p>}
          {!newsLoading && news.error && <p className="home-empty home-error">{news.error}</p>}
          {!newsLoading && !news.error && news.items.length === 0 && <p className="home-empty">아직 수집된 뉴스가 없습니다.</p>}

          <ul className={`home-news-list ${newsLoading ? 'home-news-loading' : ''}`}>
            {news.items.map((n) => (
              <li key={n.newsId} className="home-news-item">
                <a href={n.link} target="_blank" rel="noopener noreferrer" className="home-news-title">
                  {n.title}
                </a>
                <div className="home-news-meta">
                  <span>{n.press}</span>
                  <span>·</span>
                  <span>{timeAgo(n.pubDate, now)}</span>
                  {n.stockName && <span className="home-news-tag">{n.stockName}</span>}
                </div>
              </li>
            ))}
          </ul>

          {news.items.length >= size && size < 50 && (
            <button type="button" className="home-more" onClick={() => setSize(size + PAGE_SIZE)} disabled={newsLoading}>
              {newsLoading ? '불러오는 중...' : '더보기'}
            </button>
          )}

          <p className="home-source">뉴스 출처: Google News · 30분마다 자동 수집</p>
        </section>

        {/* ===== 오른쪽 위: 로그인 + 관심 종목 + 시세 ===== */}
        <aside className="home-side">

          {/* 로그인 박스 */}
          <section className="home-box home-login">
            {user ? (
              <>
                <p className="home-login-hello"><strong>{user.nickname}</strong>님, 반가워요 👋</p>
                <p className="home-login-desc">궁금한 종목을 AI 비서에게 물어보세요.</p>
                <Link to="/ai" className="btn btn-primary home-login-btn">🤖 AI 비서 시작하기</Link>
                <Link to="/mypage" className="home-box-link">👤 마이페이지 →</Link>
              </>
            ) : (
              <>
                <p className="home-login-desc">로그인하고 AI 주식 비서를 이용해보세요.</p>
                <Link to="/login" className="btn btn-primary home-login-btn">SKLabs 로그인</Link>
              </>
            )}
          </section>

          {/* 내 관심 종목 (로그인했을 때만) */}
          {user && <WatchlistBox />}

          {/* 오늘의 시세 */}
          <section className="home-box">
            <div className="home-box-head">
              <h2 className="home-box-title">오늘의 시세</h2>
              {baseDate && <span className="home-box-sub">{baseDate} 종가</span>}
            </div>
            <ul className="home-quotes">
              {stocks.map((s) => {
                const change = Number(s.changeAmount)
                const cls = change > 0 ? 'up' : change < 0 ? 'down' : ''
                return (
                  <li key={s.symbol} className="home-quote">
                    <span className="home-quote-name">{s.name}</span>
                    <span className="home-quote-price">{formatPrice(s.closePrice)}</span>
                    <span className={`home-quote-rate ${cls}`}>
                      {change > 0 ? '▲' : change < 0 ? '▼' : ''} {s.changeRate == null ? '-' : `${Number(s.changeRate).toFixed(2)}%`}
                    </span>
                  </li>
                )
              })}
            </ul>
            <Link to="/stock" className="home-box-link">주식 종목 더보기 →</Link>
          </section>
        </aside>

        {/* ===== 왼쪽 아래: 날씨 ===== */}
        <WeatherBox />

        {/* ===== 오른쪽 아래: 추천 질문 ===== */}
        <section className="home-box home-ask">
          <h2 className="home-box-title">AI에게 물어보세요</h2>
          <ul className="home-suggestions">
            {SUGGESTIONS.map((q) => (
              <li key={q}>
                <Link to={`/ai?q=${encodeURIComponent(q)}`} className="home-suggestion">💬 {q}</Link>
              </li>
            ))}
          </ul>
        </section>

      </div>

    </main>
  )
}

export default MainPage