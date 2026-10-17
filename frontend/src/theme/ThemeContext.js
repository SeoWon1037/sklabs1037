// ============================================
// 테마 정보를 담는 "공용 보관함"
// - Provider가 여기에 값을 넣고, useTheme이 여기서 꺼내 씀
// - 이 덕분에 컴포넌트마다 props로 일일이 안 넘겨도 됨
// ============================================
import { createContext } from 'react'

export const ThemeContext = createContext(null)