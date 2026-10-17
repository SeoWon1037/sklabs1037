// ============================================
// 테마 상태를 관리하는 컴포넌트
// - 앱 전체를 감싸서, 안쪽 어디서든 테마를 쓸 수 있게 해줌
// - 선택한 테마를 브라우저에 저장 → 새로고침해도 유지
// ============================================
import { useEffect, useState } from 'react'
import { ThemeContext } from './ThemeContext'

const STORAGE_KEY = 'theme'

// 처음 테마 정하기
// 1순위: 예전에 사용자가 고른 값 (브라우저에 저장된 값)
// 2순위: 사용자 PC/폰의 시스템 설정 (다크 모드 쓰는 중이면 다크)
function getInitialTheme() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    // 시크릿 모드 등 저장소를 못 쓰는 환경이면 무시
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function ThemeProvider({ children }) {
  // theme: 현재 테마 ('light' 또는 'dark')
  const [theme, setTheme] = useState(getInitialTheme)

  // theme이 바뀔 때마다 실행
  useEffect(() => {
    // <html data-theme="dark"> 처럼 표시 → theme.css의 다크 색상이 적용됨
    document.documentElement.dataset.theme = theme
    try {
      localStorage.setItem(STORAGE_KEY, theme)
    } catch {
      // 저장 실패해도 화면 동작에는 문제없음
    }
  }, [theme])

  // 라이트 ↔ 다크 전환
  const toggleTheme = () => {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'))
  }

  // 보관함에 theme, toggleTheme을 넣어서 안쪽 컴포넌트들에게 제공
  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  )
}