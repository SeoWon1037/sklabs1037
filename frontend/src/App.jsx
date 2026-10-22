// ============================================
// App.jsx
// - 앱의 뼈대: 상단바 + "주소에 맞는 화면" 연결 + 하단 정보
// - 백엔드의 @RequestMapping처럼 "어떤 주소 → 어떤 화면"을 정하는 곳
// ============================================
import { Routes, Route } from 'react-router-dom'
import './App.css'
import Header from './components/layout/Header'
import Footer from './components/layout/Footer'
import InstallBanner from './components/layout/InstallBanner'
import AppInstallPage from './pages/app/AppInstallPage'
import MessagesPage from './pages/messages/MessagesPage'
import MainPage from './pages/main/MainPage'
import StockPage from './pages/stock/StockPage'
import UserSignUpPage from './pages/user/UserSignUpPage'
import UserLoginPage from './pages/user/UserLoginPage'
import UserOAuthResultPage from './pages/user/UserOAuthResultPage'
import UserFindPage from './pages/user/UserFindPage'
import UserMyPage from './pages/user/UserMyPage'
import AiChatPage from './pages/ai/AiChatPage'
import MockTradePage from './pages/mock/MockTradePage'
import AdminFeedbackPage from './pages/admin/AdminFeedbackPage'
import AdminOpsPage from './pages/admin/AdminOpsPage'
import AdminReportsPage from './pages/admin/AdminReportsPage'
import AdminImprovePage from './pages/admin/AdminImprovePage'
import AdminQaPage from './pages/admin/AdminQaPage'
import AdminResetsPage from './pages/admin/AdminResetsPage'
import AdminInquiriesPage from './pages/admin/AdminInquiriesPage'
import SupportPage from './pages/support/SupportPage'
import LeaguePage from './pages/league/LeaguePage'
import PredictPage from './pages/predict/PredictPage'
import PersonaPage from './pages/persona/PersonaPage'
import QuizPage from './pages/quiz/QuizPage'
import ActivityPage from './pages/me/ActivityPage'
import DuelPage from './pages/duel/DuelPage'
import SharePage from './pages/share/SharePage'
import IdeasPage from './pages/ideas/IdeasPage'
import CouplePage from './pages/couple/CouplePage'
import AboutPage from './pages/about/AboutPage'
import LottoPage from './pages/lotto/LottoPage'
import BeautyLabPage from './pages/beauty/BeautyLabPage'

function App() {
  return (
    <>
      {/* 상단바: 모든 화면에 공통으로 표시 */}
      <Header />

      {/* 주소에 따라 아래 화면만 바뀜 */}
      <Routes>
        <Route path="/" element={<MainPage />} />                        {/* 메인 (오늘의 뉴스) */}
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
        <Route path="/ideas" element={<IdeasPage />} />                  {/* 💡 기능 제안 + 투표 */}
        <Route path="/s/:code" element={<SharePage />} />                {/* 📸 공유받은 성적 카드 */}
        <Route path="/ai" element={<AiChatPage />} />                    {/* AI 주식 비서 */}
        <Route path="/admin" element={<AdminOpsPage />} />               {/* 관리자: 운영 상태 (기본) */}
        <Route path="/admin/ops" element={<AdminOpsPage />} />           {/* 관리자: 운영 상태 (메일 속 링크) */}
        <Route path="/admin/reports" element={<AdminReportsPage />} />   {/* 관리자: 토론방 신고 관리 */}
        <Route path="/admin/ai-improve" element={<AdminImprovePage />} /> {/* 관리자: 🔁 AI 비서 자기 개선 */}
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

      {/* 하단 정보: 모든 화면 공통 (AI 비서·소셜 로그인 팝업 제외) */}
      <Footer />

      {/* 📲 휴대폰 두 번째 방문부터 앱 설치 안내 한 줄 (닫으면 다시 안 뜸) */}
      <InstallBanner />
    </>
  )
}

export default App