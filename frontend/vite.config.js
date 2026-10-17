// ============================================
// vite.config.js
// - React 개발 서버(Vite)의 설정 파일
// - Spring Boot의 application.properties 같은 역할
// - 이 파일은 브라우저가 아니라 Node.js가 실행함
// ============================================

// React 지원 플러그인 불러오기
// (JSX 문법 변환, HMR 같은 React 개발 기능을 Vite에 붙여줌)
import react from '@vitejs/plugin-react'

// Vite 설정 도우미 함수 불러오기
// (설정을 이 함수로 감싸면 VS Code에서 자동완성이 지원됨)
import { defineConfig } from 'vite'

// 공식 문서: https://vite.dev/config/
// export default: 이 설정을 Vite가 가져다 쓸 수 있게 내보냄
export default defineConfig({

  // ============================================
  // 플러그인 설정
  // ============================================
  // 이 프로젝트가 React 프로젝트라고 Vite에게 알려줌
  plugins: [react()],

  // ============================================
  // 개발 서버 설정 (npm run dev 할 때만 적용)
  // ============================================
  server: {

    // 프록시 설정: 프론트에서 백엔드로 요청을 대신 전달해주는 "비서" 역할
    // - 브라우저는 보안상 다른 포트(5173 → 8080)로 직접 요청하는 걸 막음 (CORS)
    // - 그래서 React는 자기 자신(5173)에게 요청하고,
    //   Vite가 뒤에서 Spring Boot(8080)로 대신 전달해줌
    proxy: {

      // "/api"로 시작하는 요청은 전부 Spring Boot로 전달
      // 예) React에서 fetch('/api/hello')
      //     → Vite가 http://localhost:8080/api/hello 로 대신 요청
      //     → 받은 결과를 React에 그대로 돌려줌
      '/api': 'http://localhost:8080',
    },
  },
})

// ※ 참고
// 이 프록시는 로컬 개발용이야.
// AWS 배포 후에는 Vite 개발 서버가 없으므로
// Spring Boot의 CORS 설정 등 다른 방식으로 연결함.