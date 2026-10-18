// ============================================
// App.jsx
// - 앱의 뼈대: 상단바 + "주소에 맞는 화면" 연결
// - 백엔드의 @RequestMapping처럼 "어떤 주소 → 어떤 화면"을 정하는 곳
// ============================================
import { Routes, Route } from 'react-router-dom'
import './App.css'
import Header from './components/Header'
import MainPage from './pages/MainPage'
import StockPage from './pages/StockPage'
import UserSignUpPage from './pages/UserSignUpPage'
import UserLoginPage from './pages/UserLoginPage'
import UserOAuthResultPage from './pages/UserOAuthResultPage'
import AiChatPage from './pages/AiChatPage'
import AdminFeedbackPage from './pages/AdminFeedbackPage'
import UserFindPage from './pages/UserFindPage'
import LottoPage from './pages/LottoPage'

function App() {
  return (
    <>
      {/* 상단바: 모든 화면에 공통으로 표시 */}
      <Header />

      {/* 주소에 따라 아래 화면만 바뀜 */}
      <Routes>
        <Route path="/" element={<MainPage />} />                        {/* 메인 (오늘의 뉴스) */}
        <Route path="/stock" element={<StockPage />} />                  {/* 주식 종목 */}
        <Route path="/ai" element={<AiChatPage />} />                    {/* AI 주식 비서 */}
        <Route path="/admin" element={<AdminFeedbackPage />} />          {/* 관리자: AI 피드백 대시보드 */}
        <Route path="/signup" element={<UserSignUpPage />} />            {/* 회원가입 */}
        <Route path="/login" element={<UserLoginPage />} />              {/* 로그인 */}
        <Route path="/find" element={<UserFindPage />} />                {/* 아이디·비밀번호 찾기 */}
        <Route path="/lotto" element={<LottoPage />} />
        <Route path="/oauth/result" element={<UserOAuthResultPage />} /> {/* 소셜 로그인 결과 (팝업 안에서 열림) */}
      </Routes>
    </>
  )
}

export default App