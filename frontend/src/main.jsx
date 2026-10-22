import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './theme/theme.css'                          // 공통 색상 (먼저)
import './index.css'                                // 기본 스타일
import { ThemeProvider } from './theme/ThemeProvider'
import App from './App.jsx'
import { registerPwa } from './pwa'

registerPwa()   // 📲 서비스 워커(푸시) + 앱 설치 버튼

createRoot(document.getElementById('root')).render(
  <StrictMode>
    {/* BrowserRouter: 주소(/signup 등)에 따라 화면을 바꿔주는 기능을 앱 전체에 켜줌 */}
    <BrowserRouter>
      {/* 앱 전체를 감싸서 어디서든 테마를 쓸 수 있게 */}
      <ThemeProvider>
        <App />
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>,
)