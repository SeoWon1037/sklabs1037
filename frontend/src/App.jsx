// ============================================
// App.jsx
// - 앱의 뼈대: 상단바 + "주소에 맞는 화면" 연결 + 하단 정보
// - 백엔드의 @RequestMapping처럼 "어떤 주소 → 어떤 화면"을 정하는 곳
// ============================================
import { Routes, Route } from 'react-router-dom'
import './App.css'
import Header from './components/layout/Header'
import Footer from './components/layout/Footer'
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
import LottoPage from './pages/lotto/LottoPage'

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
        <Route path="/ai" element={<AiChatPage />} />                    {/* AI 주식 비서 */}
        <Route path="/admin" element={<AdminOpsPage />} />               {/* 관리자: 운영 상태 (기본) */}
        <Route path="/admin/ops" element={<AdminOpsPage />} />           {/* 관리자: 운영 상태 (메일 속 링크) */}
        <Route path="/admin/feedback" element={<AdminFeedbackPage />} /> {/* 관리자: AI 피드백 대시보드 */}
        <Route path="/signup" element={<UserSignUpPage />} />            {/* 회원가입 */}
        <Route path="/login" element={<UserLoginPage />} />              {/* 로그인 */}
        <Route path="/find" element={<UserFindPage />} />                {/* 아이디·비밀번호 찾기 */}
        <Route path="/mypage" element={<UserMyPage />} />                {/* 마이페이지 */}
        <Route path="/lotto" element={<LottoPage />} />                  {/* 오늘의 숫자 (로또 / 연금복권) */}
        <Route path="/oauth/result" element={<UserOAuthResultPage />} /> {/* 소셜 로그인 결과 (팝업 안에서 열림) */}
      </Routes>

      {/* 하단 정보: 모든 화면 공통 (AI 비서·소셜 로그인 팝업 제외) */}
      <Footer />
    </>
  )
}

export default App