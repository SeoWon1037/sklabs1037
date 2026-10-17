// ============================================
// App.jsx
// - 앱의 뼈대: 상단바 + "주소에 맞는 화면" 연결
// - 백엔드의 @RequestMapping처럼 "어떤 주소 → 어떤 화면"을 정하는 곳
// ============================================
import { Routes, Route } from 'react-router-dom'
import './App.css'
import Header from './components/Header'
import MainPage from './pages/MainPage'
import UserSignUpPage from './pages/UserSignUpPage'
import UserLoginPage from './pages/UserLoginPage'

function App() {
  return (
    <>
      {/* 상단바: 모든 화면에 공통으로 표시 */}
      <Header />

      {/* 주소에 따라 아래 화면만 바뀜 */}
      <Routes>
        <Route path="/" element={<MainPage />} />              {/* 메인 */}
        <Route path="/signup" element={<UserSignUpPage />} />  {/* 회원가입 */}
        <Route path="/login" element={<UserLoginPage />} />    {/* 로그인 */}
      </Routes>
    </>
  )
}

export default App