// ============================================
// 어느 컴포넌트에서든 테마를 꺼내 쓰는 함수
// 사용법: const { theme, toggleTheme } = useTheme()
// ============================================
import { useContext } from 'react'
import { ThemeContext } from './ThemeContext'

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme은 ThemeProvider 안에서만 사용할 수 있어요')
  }
  return context
}