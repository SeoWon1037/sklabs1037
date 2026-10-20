// ============================================
// 메인 화면 (주소: /) — 네이버 메인 구조, 줄마다 왼쪽·오른쪽 칸의 위아래 끝이 딱 맞게
// - 위: 로고 + 검색창(= AI 비서에게 질문), 아래에 바로가기 아이콘
// - 1줄: 💰 모의투자 배너(TOP 3)          | 로그인 카드
// - 2줄: 뉴스(+💬토론 탭, AI 요약은 눌러야 펼침, 안에서 스크롤) | 오늘의 시세(시가총액 TOP 3 | ⭐ 관심 종목) + 💬 지금 토론 중 + 날씨
//   → 뉴스 칸은 오른쪽 세 칸 높이만큼 길어짐
// - 휴대폰: 한 줄씩 (배너 → 로그인 → 뉴스 → 시세 → 토론 → 날씨)
// - 백엔드 API
//   GET /api/news?category=&stockId=&size=   뉴스 목록
//   GET /api/news/summary?stockId=           AI 뉴스 요약 (NewsSummaryBox)
//   GET /api/stock/dashboard?days=5          종목 시세 + 시가총액 (뉴스 탭 이름 + 시가총액 TOP 3)
//   GET /api/user/me                         로그인 상태
//   GET /api/stock/board/recent?size=        전 종목 최근 토론 글 (💬 토론 탭 + 지금 토론 중 박스)
//   GET /api/mock/ranking?size=3             모의투자 수익률 TOP 3 (MockBanner)
//   GET /api/watchlist                       관심 종목 (⭐ 관심 종목 탭)
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import useBoardStream from '../../hooks/useBoardStream'
import './MainPage.css'
import WeatherBox from '../../components/weather/WeatherBox'
import NewsSummaryBox from '../../components/news/NewsSummaryBox'
import MockBanner from '../../components/mock/MockBanner'
import TodayCard from '../../components/me/TodayCard'
import AllMenu from '../../components/layout/AllMenu'
import { SERVICES, SHORTCUT_KEYS } from '../../components/layout/menuData'

// 바로가기 아이콘
// 바로가기 (위 상단 메뉴와 같은 순서 + 기업 정보 · 투자 성향 · 뉴스 퀴즈)

// 오른쪽 아래 "지금 토론 중"에 보여줄 글 개수
// - 새 글은 실시간(SSE)으로 바로 반영, 혹시 연결이 안 될 때를 대비해 5분마다 한 번 더
const HOT_BOARD_SIZE = 3
const HOT_REFRESH_MS = 5 * 60 * 1000

// 뉴스 탭에 보여줄 종목 (종목코드, 이 순서대로 표시)
const NEWS_TAB_SYMBOLS = ['005930', '000660', '035420']   // 삼성전자, SK하이닉스, NAVER

// 한 번에 보여줄 뉴스 개수 (더보기 누르면 늘어남)
const PAGE_SIZE = 15

const formatPrice = (value) => (value == null ? '-' : Number(value).toLocaleString('ko-KR'))

// 오늘의 시세에 보여줄 종목 수 (시가총액 큰 순)
const MARKET_CAP_TOP = 3

// 시가총액: 1,284,561,429,074,000 → 1,284조
const formatCap = (value) => {
  const n = Number(value)
  if (!n) return ''
  return n >= 1e12 ? `${Math.round(n / 1e12).toLocaleString('ko-KR')}조` : `${Math.round(n / 1e8).toLocaleString('ko-KR')}억`
}

// "3분 전", "2시간 전", "1일 전"
const timeAgo = (dateText, now) => {
  const minutes = Math.floor((now - new Date(dateText).getTime()) / 60000)
  if (minutes < 1) return '방금'
  if (minutes < 60) return `${minutes}분 전`
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}시간 전`
  return `${Math.floor(minutes / (60 * 24))}일 전`
}

// 토론 탭에서 보여줄 최근 글 개수
const BOARD_SIZE = 20

// 탭 → API 주소 (토론 탭만 토론 글, 나머지는 뉴스)
const newsUrl = (tab, size) => {
  if (tab === 'BOARD') return `/api/stock/board/recent?size=${BOARD_SIZE}`
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
  const [tab, setTab] = useState('ALL')          // 'ALL' | 'MARKET' | 'SPORTS' | 'BOARD' | 종목 ID
  const [size, setSize] = useState(PAGE_SIZE)
  const [news, setNews] = useState({ key: null, items: [], error: '' })
  const newsKey = `${tab}-${size}`
  const newsLoading = news.key !== newsKey        // 지금 조건의 결과가 아직 안 왔으면 로딩 중

  // ===== 시세 / 로그인 =====
  const [stocks, setStocks] = useState([])
  const [baseDate, setBaseDate] = useState(null)
  const [user, setUser] = useState(null)
  const [menuOpen, setMenuOpen] = useState(false)   // ☰ 전체 메뉴
  const closeMenu = useCallback(() => setMenuOpen(false), [])
  const [stockTab, setStockTab] = useState('ALL')   // 종목 카드 탭: 'ALL' | 'WATCH'
  const [watchStocks, setWatchStocks] = useState([])   // 관심 종목 (시세 포함, 전 종목 중 어떤 종목이든)

  // ===== 지금 토론 중 (오른쪽 아래) =====
  const [hotPosts, setHotPosts] = useState(null)   // null = 불러오는 중
  const [hotAt, setHotAt] = useState(() => Date.now())   // 마지막으로 불러온 시각 ("n분 전" 기준)
  const [boardTick, setBoardTick] = useState(0)          // 실시간: 어느 종목이든 새 글·삭제가 생기면 +1

  // 실시간 연결 (전 종목) → 지금 토론 중 박스 + 💬 토론 탭 다시 불러오기
  useBoardStream(null, (type) => {
    if (type === 'post' || type === 'delete') setBoardTick((n) => n + 1)
  })

  // 토론 탭을 보고 있을 때만 실시간 새 글에 반응 (뉴스 탭에서는 0으로 고정 → 다시 안 불러옴)
  const boardRefresh = tab === 'BOARD' ? boardTick : 0

  // 탭이나 개수가 바뀔 때마다 뉴스 다시 불러오기
  useEffect(() => {

    let ignore = false   // 탭을 빠르게 바꿨을 때 늦게 도착한 이전 결과는 무시

    fetch(newsUrl(tab, size))
      .then((res) => res.json())
      .then((data) => {
        if (ignore) return
        setNews(data.success
          ? { key: `${tab}-${size}`, items: tab === 'BOARD' ? data.posts : data.news, error: '' }
          : { key: `${tab}-${size}`, items: [], error: data.message })
      })
      .catch(() => {
        if (!ignore) setNews({ key: `${tab}-${size}`, items: [], error: '서버에 연결할 수 없습니다.' })
      })

    return () => { ignore = true }

  }, [tab, size, boardRefresh])

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
      .then((data) => {
        setUser(data.loggedIn ? data.user : null)
        if (!data.loggedIn) return
        return fetch('/api/watchlist')
          .then((res) => res.json())
          .then((w) => w.success && setWatchStocks(w.stocks))
      })
      .catch(() => setUser(null))

  }, [])

  // 지금 토론 중: 처음 1번 + 1분마다 다시 불러오기
  // - 다른 탭을 보고 있을 때(화면이 안 보일 때)는 건너뜀 → 쓸데없는 요청 줄이기
  // - 다른 탭에 있다가 돌아오면 바로 1번 불러옴
  useEffect(() => {

    let ignore = false

    const load = () => {
      fetch(`/api/stock/board/recent?size=${HOT_BOARD_SIZE}`)
        .then((res) => res.json())
        .then((data) => {
          if (ignore || !data.success) return
          setHotPosts(data.posts)
          setHotAt(Date.now())
        })
        .catch(() => { if (!ignore) setHotPosts((prev) => prev ?? []) })   // 실패하면 이전 글 그대로
    }

    const onVisible = () => { if (document.visibilityState === 'visible') load() }

    load()
    const timer = setInterval(() => { if (document.visibilityState === 'visible') load() }, HOT_REFRESH_MS)
    document.addEventListener('visibilitychange', onVisible)

    // 화면을 떠나면 타이머·이벤트 정리
    return () => {
      ignore = true
      clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
    }

  }, [boardTick])   // 실시간 새 글이 오면 바로 다시

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

  // 뉴스 탭 목록: 전체, 시장, 스포츠, 주요 종목 (NEWS_TAB_SYMBOLS에 있는 종목만), 토론
  const tabs = [
    { value: 'ALL', label: '전체' },
    { value: 'MARKET', label: '시장' },
    { value: 'SPORTS', label: '스포츠' },
    ...NEWS_TAB_SYMBOLS
      .map((symbol) => stocks.find((s) => s.symbol === symbol))
      .filter(Boolean)
      .map((s) => ({ value: String(s.stockId), label: s.name })),
    { value: 'BOARD', label: '💬 토론' },
  ]

  const isBoard = tab === 'BOARD'
  // 오늘의 시세: 시가총액 큰 순 TOP 3 (상장 주식 수를 아직 못 받았으면 목록 앞에서 3개)
  const capTop = [...stocks]
    .sort((a, b) => (Number(b.marketCap) || 0) - (Number(a.marketCap) || 0))
    .slice(0, MARKET_CAP_TOP)
  const quoteStocks = stockTab === 'WATCH' ? watchStocks : capTop

  return (
    <main className="home">

      {/* ===== 검색창 ===== */}
      <section className="home-search-area">
        <div className="home-search-row">
          <div className="home-brand">
            <button type="button" className="home-menu-btn" onClick={() => setMenuOpen(true)} aria-label="전체 메뉴">
              <span /><span /><span />
            </button>
            <span className="home-logo">SKLabs</span>
          </div>

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

        {/* 바로가기 (검색창 바로 아래 촘촘하게, 마지막 칸은 ☰ 전체 메뉴) */}
        <nav className="home-shortcuts">
          {SHORTCUT_KEYS.map((k) => {
            const s = SERVICES[k]
            return (
              <Link key={k} to={s.to} className="home-shortcut">
                <span className="home-shortcut-icon" style={{ '--c': s.color }}>{s.icon}</span>
                <span className="home-shortcut-label">{s.label}</span>
              </Link>
            )
          })}
          <button type="button" className="home-shortcut" onClick={() => setMenuOpen(true)}>
            <span className="home-shortcut-icon home-shortcut-more">⋯</span>
            <span className="home-shortcut-label">전체</span>
          </button>
        </nav>
      </section>

      {menuOpen && <AllMenu user={user} onClose={closeMenu} />}

      {/* 줄마다 왼쪽·오른쪽 칸 높이가 같음 (grid: 같은 줄 칸끼리 위아래 끝 맞춤) */}
      <div className="home-grid">

        {/* ===== 1줄 왼쪽: 모의투자 배너 ===== */}
        <MockBanner />

        {/* ===== 1줄 오른쪽: 로그인 카드 ===== */}
          <section className="home-box home-login">
            {user ? (
              <>
                <div className="home-login-top">
                  <span className="home-login-avatar">{user.nickname?.slice(0, 1)}</span>
                  <div className="home-login-info">
                    <p className="home-login-hello"><strong>{user.nickname}</strong>님, 반가워요 👋</p>
                    {user.email && <span className="home-login-email">{user.email}</span>}
                  </div>
                </div>
                <nav className="home-login-menu">
                  <Link to="/ai">AI 비서</Link>
                  <Link to="/mock">모의투자</Link>
                  <Link to="/activity">🏅 내 활동</Link>
                  <Link to="/mypage">마이페이지</Link>
                </nav>
              </>
            ) : (
              <div className="home-login-guest">
                <p className="home-login-desc">로그인하고 AI 비서 · 모의투자 · 관심 종목을 이용해보세요.</p>
                <Link to="/login" className="btn btn-primary home-login-btn">SKLabs 로그인</Link>
                <div className="home-login-links">
                  <Link to="/find">아이디·비밀번호 찾기</Link>
                  <Link to="/signup">회원가입</Link>
                </div>
              </div>
            )}
          </section>


        {/* ===== 2줄 왼쪽: 뉴스 (오른쪽 칸 높이에 맞추고, 넘치면 안에서 스크롤) ===== */}
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
          {tab !== 'ALL' && tab !== 'SPORTS' && !isBoard && (
            <NewsSummaryBox key={tab} stockId={tab === 'MARKET' ? null : tab} collapsible />
          )}

          {newsLoading && news.items.length === 0 && <p className="home-empty">{isBoard ? '토론 글을' : '뉴스를'} 불러오는 중...</p>}
          {!newsLoading && news.error && <p className="home-empty home-error">{news.error}</p>}
          {!newsLoading && !news.error && news.items.length === 0 && (
            <p className="home-empty">
              {isBoard
                ? <>아직 토론 글이 없어요. <Link to="/stock?tab=board">첫 글 쓰러 가기 →</Link></>
                : '아직 수집된 뉴스가 없습니다.'}
            </p>
          )}

          {/* ===== 💬 토론 탭: 전 종목 최근 글 (누르면 그 종목 토론 탭으로) ===== */}
          {isBoard && (
            <ul className={`home-news-list ${newsLoading ? 'home-news-loading' : ''}`}>
              {!newsLoading && news.items.map((p) => (
                <li key={p.postId} className="home-news-item">
                  <Link to={`/stock?symbol=${p.symbol}&tab=board`} className="home-board-link">
                    <div className="home-board-top">
                      <span className="home-news-tag home-board-stock">{p.stockName}</span>
                      {p.opinion === 'UP' && <span className="home-board-opinion home-board-up">오를 것</span>}
                      {p.opinion === 'DOWN' && <span className="home-board-opinion home-board-down">내릴 것</span>}
                    </div>
                    <span className="home-news-title home-board-text">{p.content}</span>
                    <div className="home-news-meta">
                      <span>{p.nickname}</span>
                      <span>·</span>
                      <span>{timeAgo(p.insAt, now)}</span>
                      <span>·</span>
                      <span>💬 {p.commentCount}</span>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}

          {!isBoard && (
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
          )}

          {!isBoard && news.items.length >= size && size < 50 && (
            <button type="button" className="home-more" onClick={() => setSize(size + PAGE_SIZE)} disabled={newsLoading}>
              {newsLoading ? '불러오는 중...' : '더보기'}
            </button>
          )}

          <p className="home-source">
            {isBoard ? '전 종목 최근 토론 글 · 누르면 그 종목 토론방으로 이동' : '뉴스 출처: Google News · 30분마다 자동 수집'}
          </p>
        </section>


        {/* ===== 2줄 오른쪽: 오늘의 시세 + 지금 토론 중 + 날씨 ===== */}
        <div className="home-side">

          {/* 📅 오늘의 SKLabs (로그인했을 때만, 작은 4칸) */}
          {user && <TodayCard />}

          {/* [오늘의 시세 = 시가총액 TOP 3 | ⭐ 관심 종목] */}
          <section className="home-box home-quotes-box">
            <div className="home-box-head">
              <div className="home-stock-tabs">
                <button
                  type="button"
                  className={`home-stock-tab ${stockTab === 'ALL' ? 'home-stock-tab-active' : ''}`}
                  onClick={() => setStockTab('ALL')}
                >
                  오늘의 시세
                </button>
                {user && (
                  <button
                    type="button"
                    className={`home-stock-tab ${stockTab === 'WATCH' ? 'home-stock-tab-active' : ''}`}
                    onClick={() => setStockTab('WATCH')}
                  >
                    ⭐ 관심 종목
                  </button>
                )}
              </div>
              {stockTab === 'ALL' && baseDate && <span className="home-box-sub">시가총액 TOP 3 · {baseDate.slice(5)} 종가</span>}
            </div>

            <ul className="home-quotes">
              {quoteStocks.length === 0 && (
                <li className="home-quote-empty">
                  {stockTab === 'WATCH'
                    ? <>아직 관심 종목이 없어요. <Link to="/stock">☆ 눌러 추가하기 →</Link></>
                    : '시세를 불러오는 중...'}
                </li>
              )}
              {quoteStocks.map((s, i) => {
                const change = Number(s.changeAmount)
                const cls = change > 0 ? 'up' : change < 0 ? 'down' : ''
                return (
                  <li key={s.symbol}>
                    <Link to={`/stock?symbol=${s.symbol}`} className="home-quote">
                      <span className="home-quote-rank">{stockTab === 'ALL' ? i + 1 : '★'}</span>
                      <span className="home-quote-name">
                        {s.name}
                        {stockTab === 'ALL' && s.marketCap && <small>{formatCap(s.marketCap)}</small>}
                      </span>
                      <span className="home-quote-price">{formatPrice(s.closePrice)}</span>
                      <span className={`home-quote-rate ${cls}`}>
                        {change > 0 ? '▲' : change < 0 ? '▼' : ''} {s.changeRate == null ? '-' : `${Number(s.changeRate).toFixed(2)}%`}
                      </span>
                    </Link>
                  </li>
                )
              })}
            </ul>
            <Link to="/stock" className="home-box-link">주식 종목 더보기 →</Link>
          </section>

          {/* 지금 토론 중 */}
          <section className="home-box home-ask">
            <div className="home-box-head">
              <h2 className="home-box-title">💬 지금 토론 중</h2>
              <span className="home-box-sub">전 종목</span>
            </div>

            {hotPosts === null && <p className="home-hot-empty">불러오는 중...</p>}
            {hotPosts?.length === 0 && (
              <p className="home-hot-empty">
                아직 토론 글이 없어요.<br />
                <Link to="/stock?tab=board">첫 의견 남기러 가기 →</Link>
              </p>
            )}

            {hotPosts?.length > 0 && (
              <ul className="home-hot-list">
                {hotPosts.map((p) => (
                  <li key={p.postId}>
                    <Link to={`/stock?symbol=${p.symbol}&tab=board`} className="home-hot-item">
                      <div className="home-board-top">
                        <span className="home-news-tag home-board-stock">{p.stockName}</span>
                        {p.opinion === 'UP' && <span className="home-board-opinion home-board-up">오를 것</span>}
                        {p.opinion === 'DOWN' && <span className="home-board-opinion home-board-down">내릴 것</span>}
                        <span className="home-hot-time">{timeAgo(p.insAt, hotAt)}</span>
                      </div>
                      <span className="home-hot-text">{p.content}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}

            <button type="button" className="home-box-link home-hot-more" onClick={() => changeTab('BOARD')}>
              토론 글 더보기 →
            </button>
          </section>

          {/* 날씨 */}
          <WeatherBox />
        </div>
      </div>

    </main>
  )
}

export default MainPage