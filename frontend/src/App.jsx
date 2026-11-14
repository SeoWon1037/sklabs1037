// ============================================
// App.jsx
// - 앱의 뼈대: 상단바 + "주소에 맞는 화면" 연결 + 하단 정보
// - 백엔드의 @RequestMapping처럼 "어떤 주소 → 어떤 화면"을 정하는 곳
// ============================================
import { Routes, Route, useLocation } from 'react-router-dom'
import './App.css'
import Header from './components/layout/Header'
import Footer from './components/layout/Footer'
import ErrorBoundary from './components/layout/ErrorBoundary'
import VoiceCommand from './components/voice/VoiceCommand'
import InstallBanner from './components/layout/InstallBanner'
import MainPage from './pages/main/MainPage'
import { Suspense, useEffect } from 'react'
import { trackPage } from './track'
import { rememberRef } from './invite'
import { lazyPage, preloadWhenIdle } from './lazyPage'

// 첫 화면(MainPage)만 처음에 같이 받고, 나머지 화면은 들어갈 때 받아옴 (lazyPage.js)
const AppInstallPage = lazyPage(() => import('./pages/app/AppInstallPage'))
const MessagesPage = lazyPage(() => import('./pages/messages/MessagesPage'))
const StockPage = lazyPage(() => import('./pages/stock/StockPage'))
const UserSignUpPage = lazyPage(() => import('./pages/user/UserSignUpPage'))
const UserLoginPage = lazyPage(() => import('./pages/user/UserLoginPage'))
const UserOAuthResultPage = lazyPage(() => import('./pages/user/UserOAuthResultPage'))
const UserFindPage = lazyPage(() => import('./pages/user/UserFindPage'))
const UserMyPage = lazyPage(() => import('./pages/user/UserMyPage'))
const AiChatPage = lazyPage(() => import('./pages/ai/AiChatPage'))
const MockTradePage = lazyPage(() => import('./pages/mock/MockTradePage'))
const AdminFeedbackPage = lazyPage(() => import('./pages/admin/AdminFeedbackPage'))
const AdminOpsPage = lazyPage(() => import('./pages/admin/AdminOpsPage'))
const AdminReportsPage = lazyPage(() => import('./pages/admin/AdminReportsPage'))
const AdminImprovePage = lazyPage(() => import('./pages/admin/AdminImprovePage'))
const AdminQaPage = lazyPage(() => import('./pages/admin/AdminQaPage'))
const AdminResetsPage = lazyPage(() => import('./pages/admin/AdminResetsPage'))
const AdminInquiriesPage = lazyPage(() => import('./pages/admin/AdminInquiriesPage'))
const AdminAutofixPage = lazyPage(() => import('./pages/admin/AdminAutofixPage'))
const AdminServerPage = lazyPage(() => import('./pages/admin/AdminServerPage'))
const AdminApiPage = lazyPage(() => import('./pages/admin/AdminApiPage'))
const AdminAiCostPage = lazyPage(() => import('./pages/admin/AdminAiCostPage'))
const MarketCalendarPage = lazyPage(() => import('./pages/calendar/MarketCalendarPage'))
const StatusPage = lazyPage(() => import('./pages/status/StatusPage'))
const SupportPage = lazyPage(() => import('./pages/support/SupportPage'))
const LeaguePage = lazyPage(() => import('./pages/league/LeaguePage'))
const PredictPage = lazyPage(() => import('./pages/predict/PredictPage'))
const PersonaPage = lazyPage(() => import('./pages/persona/PersonaPage'))
const QuizPage = lazyPage(() => import('./pages/quiz/QuizPage'))
const ActivityPage = lazyPage(() => import('./pages/me/ActivityPage'))
const DuelPage = lazyPage(() => import('./pages/duel/DuelPage'))
const SharePage = lazyPage(() => import('./pages/share/SharePage'))
const IdeasPage = lazyPage(() => import('./pages/ideas/IdeasPage'))
const CouplePage = lazyPage(() => import('./pages/couple/CouplePage'))
const PlansPage = lazyPage(() => import('./pages/plans/PlansPage'))
const PcPage = lazyPage(() => import('./pages/pc/PcPage'))
const AboutPage = lazyPage(() => import('./pages/about/AboutPage'))
const LottoPage = lazyPage(() => import('./pages/lotto/LottoPage'))
const BeautyLabPage = lazyPage(() => import('./pages/beauty/BeautyLabPage'))
const AdminStatsPage = lazyPage(() => import('./pages/admin/AdminStatsPage'))
const MarketPage = lazyPage(() => import('./pages/market/MarketPage'))
const MarketItemPage = lazyPage(() => import('./pages/market/MarketItemPage'))
const MarketWritePage = lazyPage(() => import('./pages/market/MarketWritePage'))
const MarketMyPage = lazyPage(() => import('./pages/market/MarketMyPage'))
const MarketUserPage = lazyPage(() => import('./pages/market/MarketUserPage'))
const AdminMarketPage = lazyPage(() => import('./pages/admin/AdminMarketPage'))
const AdminErrorsPage = lazyPage(() => import('./pages/admin/AdminErrorsPage'))
const TranslatePage = lazyPage(() => import('./pages/translate/TranslatePage'))
const TreePage = lazyPage(() => import('./pages/tree/TreePage'))
const AdminSelfCheckPage = lazyPage(() => import('./pages/admin/AdminSelfCheckPage'))
const WhatIfPage = lazyPage(() => import('./pages/whatif/WhatIfPage'))

// 주소 → 탭 제목 (앞부분이 맞는 것 중 가장 긴 것, 없으면 SKLabs)
const TITLES = {
  '/': 'SKLabs - AI 챗봇 · 모의투자 · 중고거래', '/invest': 'SKLabs - AI 챗봇 · 모의투자 · 중고거래',
  '/stock': '주식 종목 · SKLabs', '/mock': '모의투자 · SKLabs', '/ai': 'AI 챗봇 · SKLabs', '/plans': '내 약속 · 음성 브리핑 · SKLabs', '/pc': '내 PC 프로그램 실행 · SKLabs', '/whatif': '그때 샀더라면 · SKLabs', '/tree': '글자 트리 · SKLabs', '/translate': '번역 · SKLabs',
  '/calendar': '증시 일정 · SKLabs', '/league': 'AI 리그 · SKLabs', '/predict': '예측 게임 · SKLabs', '/quiz': '뉴스 퀴즈 · SKLabs',
  '/persona': '투자 성향 테스트 · SKLabs', '/market': '중고거래 · SKLabs', '/market/new': '판매하기 · SKLabs', '/market/my': '내 장터 · SKLabs',
  '/messages': '메시지 · SKLabs', '/about': '만든 사람 · SKLabs', '/status': '서비스 상태 · SKLabs', '/login': '로그인 · SKLabs', '/admin': '관리자 · SKLabs',
}
const titleOf = (pathname) => {
  if (TITLES[pathname]) return TITLES[pathname]
  const key = Object.keys(TITLES).filter((k) => k !== '/' && pathname.startsWith(`${k}/`)).sort((a, b) => b.length - a.length)[0]
  return key ? TITLES[key] : 'SKLabs'
}

function App() {

  // 📊 화면 열기 통계 (track.js)
  const location = useLocation()
  useEffect(() => { trackPage(location.pathname) }, [location.pathname])

  // 자주 가는 화면은 한가할 때 미리 받아둠
  useEffect(() => {
    preloadWhenIdle([() => import('./pages/stock/StockPage'), () => import('./pages/mock/MockTradePage'), () => import('./pages/market/MarketPage'), () => import('./pages/whatif/WhatIfPage')])
  }, [])

  // 🤝 초대 링크(/?ref=코드)로 들어왔으면 코드를 기억 (로그인하면 Header가 서버로 보냄)
  useEffect(() => { rememberRef() }, [])

  // 브라우저 탭 · 검색 결과에 보이는 제목 (화면마다)
  useEffect(() => { document.title = titleOf(location.pathname) }, [location.pathname])

  return (
    <>
      {/* 상단바: 모든 화면에 공통으로 표시 */}
      <Header />

      {/* 주소에 따라 아래 화면만 바뀜 */}
      {/* 화면 코드를 받아오는 잠깐 동안 보이는 자리 (높이를 잡아둬서 아래 영역이 튀지 않게) */}
      {/* 화면 하나가 깨져도 사이트 전체가 흰 화면이 되지 않게 (다른 화면으로 가면 자동으로 풀림) */}
      <ErrorBoundary resetKey={location.pathname}>
      <Suspense fallback={<main className="page-loading" aria-busy="true">불러오는 중...</main>}>
      <Routes>
        <Route path="/" element={<MainPage />} />                        {/* 📈 투자 홈 (사이트 첫 화면: 뉴스 · 시세 · 날씨) */}
        <Route path="/translate" element={<TranslatePage />} />          {/* 🌐 번역 (말하거나 써서 번역, 소리로 듣기) */}
        <Route path="/tree" element={<TreePage />} />                    {/* 🎄 글자 트리 (글자로 그린 3차원 크리스마스 트리) */}
        <Route path="/whatif" element={<WhatIfPage />} />                {/* ⏳ 그때 샀더라면 (공유용 계산기) */}
        <Route path="/market" element={<MarketPage />} />                {/* 🛍️ 중고거래 장터 */}
        <Route path="/market/new" element={<MarketWritePage />} />       {/* 🛍️ 상품 올리기 */}
        <Route path="/market/my" element={<MarketMyPage />} />           {/* 🛍️ 내 판매 목록 · 찜 */}
        <Route path="/market/user/:id" element={<MarketUserPage />} />   {/* 🛍️ 판매자 프로필: 매너 온도 · 받은 후기 · 판매 상품 */}
        <Route path="/market/:id" element={<MarketItemPage />} />        {/* 🛍️ 상품 상세 → 채팅하기 */}
        <Route path="/market/:id/edit" element={<MarketWritePage />} />  {/* 🛍️ 상품 고치기 */}
        <Route path="/invest" element={<MainPage />} />                  {/* 📈 투자 홈 (예전 주소도 그대로 열리게) */}
        <Route path="/stock" element={<StockPage />} />                  {/* 주식 종목 */}
        <Route path="/mock" element={<MockTradePage />} />              {/* 모의투자 */}
        <Route path="/league" element={<LeaguePage />} />                {/* 🤖 AI vs 사람 투자 리그 */}
        <Route path="/predict" element={<PredictPage />} />              {/* 🎯 오를까 내릴까 (매일 예측 게임) */}
        <Route path="/persona" element={<PersonaPage />} />              {/* 🧭 투자 성향 테스트 */}
        <Route path="/quiz" element={<QuizPage />} />                    {/* 📰 오늘의 뉴스 퀴즈 */}
        <Route path="/activity" element={<ActivityPage />} />            {/* 🏅 내 활동 · 업적 + 🤖 AI 주간 코치 */}
        <Route path="/duel" element={<DuelPage />} />                    {/* ⚔️ 친구 대결 */}
        <Route path="/duel/:code" element={<DuelPage />} />              {/* ⚔️ 대결 초대 · 점수판 */}
        <Route path="/support" element={<SupportPage />} />              {/* 📮 문의하기 */}
        <Route path="/messages" element={<MessagesPage />} />            {/* 💬 1:1 메시지 */}
        <Route path="/app" element={<AppInstallPage />} />               {/* 📲 앱 설치 안내 (QR · 기기별) */}
        <Route path="/about" element={<AboutPage />} />                  {/* 👤 만든 사람 (로그인 없이) */}
        <Route path="/couple" element={<CouplePage />} />                {/* 💕 커플 */}
        <Route path="/pc" element={<PcPage />} />                        {/* 🖥️ 내 PC 프로그램 실행 (PC 도우미 연결) */}
        <Route path="/plans" element={<PlansPage />} />                  {/* 🗓️ 내 약속 · 🔊 음성 브리핑 */}
        <Route path="/ideas" element={<IdeasPage />} />                  {/* 💡 기능 제안 + 투표 */}
        <Route path="/s/:code" element={<SharePage />} />                {/* 📸 공유받은 성적 카드 */}
        <Route path="/ai" element={<AiChatPage />} />                    {/* AI 챗봇 */}
        <Route path="/admin" element={<AdminOpsPage />} />               {/* 관리자: 운영 상태 (기본) */}
        <Route path="/admin/ops" element={<AdminOpsPage />} />           {/* 관리자: 운영 상태 (메일 속 링크) */}
        <Route path="/admin/reports" element={<AdminReportsPage />} />   {/* 관리자: 토론방 신고 관리 */}
        <Route path="/admin/ai-improve" element={<AdminImprovePage />} /> {/* 관리자: 🔁 AI 챗봇 자기 개선 */}
        <Route path="/admin/server" element={<AdminServerPage />} />     {/* 관리자: 🖥 서버 상태 (운영 서버 + DB, 5초마다) */}
        <Route path="/status" element={<StatusPage />} />              {/* 🟢 서비스 상태 (누구나) */}
        <Route path="/calendar" element={<MarketCalendarPage />} />     {/* 📅 증시 일정 (휴장 · FOMC · 금통위 · 만기일) */}
        <Route path="/admin/selfcheck" element={<AdminSelfCheckPage />} /> {/* 관리자: 🧪 자가 점검 (빠진 SQL · 서버 키 · 옛날 시세 · 계산) */}
        <Route path="/admin/errors" element={<AdminErrorsPage />} />    {/* 관리자: 🐞 화면 오류 (사용자 브라우저에서 난 오류 모음) */}
        <Route path="/admin/market" element={<AdminMarketPage />} />    {/* 관리자: 🛍️ 장터 관리 (신고된 · 숨겨진 상품) */}
        <Route path="/admin/stats" element={<AdminStatsPage />} />      {/* 관리자: 📊 사용 통계 */}
        <Route path="/admin/ai-cost" element={<AdminAiCostPage />} />   {/* 관리자: 💸 AI 비용 (하루 한도 · 긴급 정지) */}
        <Route path="/admin/api" element={<AdminApiPage />} />           {/* 관리자: 🔌 외부 연동 (토스·Claude·메일… 호출 기록) */}
        <Route path="/admin/autofix" element={<AdminAutofixPage />} />   {/* 관리자: 🤖 AI 자동 수정 (승인 · 배포) */}
        <Route path="/admin/inquiries" element={<AdminInquiriesPage />} /> {/* 관리자: 📮 문의함 (AI 분류) */}
        <Route path="/admin/resets" element={<AdminResetsPage />} />    {/* 관리자: ♻️ 모의투자 계좌 초기화 신청 */}
        <Route path="/admin/qa" element={<AdminQaPage />} />            {/* 관리자: 🤖 AI QA 봇 */}
        <Route path="/admin/feedback" element={<AdminFeedbackPage />} /> {/* 관리자: AI 피드백 대시보드 */}
        <Route path="/signup" element={<UserSignUpPage />} />            {/* 회원가입 */}
        <Route path="/login" element={<UserLoginPage />} />              {/* 로그인 */}
        <Route path="/find" element={<UserFindPage />} />                {/* 아이디·비밀번호 찾기 */}
        <Route path="/mypage" element={<UserMyPage />} />                {/* 마이페이지 */}
        <Route path="/beauty" element={<BeautyLabPage />} />             {/* 뷰티 랩 (성분 분석 / 기획 브리프 / 성분 사전) */}
        <Route path="/lotto" element={<LottoPage />} />                  {/* 오늘의 숫자 (로또 / 연금복권) */}
        <Route path="/oauth/result" element={<UserOAuthResultPage />} /> {/* 소셜 로그인 결과 (팝업 안에서 열림) */}
      </Routes>
      </Suspense>
      </ErrorBoundary>

      {/* 하단 정보: 모든 화면 공통 (AI 챗봇·소셜 로그인 팝업 제외) */}
      <Footer />
      <VoiceCommand />   {/* 🎤 말로 시키기 (오른쪽 아래 버튼) */}

      {/* 📲 휴대폰 두 번째 방문부터 앱 설치 안내 한 줄 (닫으면 다시 안 뜸) */}
      <InstallBanner />
    </>
  )
}

export default App