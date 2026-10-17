// ============================================
// 메인 화면 (주소: /)
// ============================================
import { useEffect, useState } from 'react'

function MainPage() {

  // 백엔드 응답 메시지 (처음엔 '불러오는 중...')
  const [message, setMessage] = useState('불러오는 중...')

  // 화면이 처음 뜰 때 백엔드 연결 확인
  useEffect(() => {

    // '/api/hello' → vite.config.js 프록시가 localhost:8080/api/hello 로 전달
    fetch('/api/hello')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.text()
      })
      .then((data) => setMessage(data))
      .catch((err) => setMessage(`연결 실패: ${err.message}`))

  }, [])   // [] : 처음 한 번만 실행

  return (
    <main className="main">
      <h1>SKLabs 주식 분석 - 시즌1</h1>
      <p>백엔드 응답: {message}</p>
    </main>
  )
}

export default MainPage