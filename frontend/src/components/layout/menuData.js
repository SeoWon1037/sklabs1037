// ============================================
// 메인 바로가기 · ☰ 전체 메뉴에 쓰는 서비스 목록 (한 곳에서 관리)
// - color: 아이콘 칸 배경색
// ============================================
export const SERVICES = {
  stock: { icon: '📈', label: '주식 종목', to: '/stock', color: '#22c55e', desc: '시세 · 차트 · 토론' },
  mock: { icon: '💰', label: '모의투자', to: '/mock', color: '#f59e0b', desc: '가상 1,000만 원' },
  league: { icon: '🏆', label: 'AI 리그', to: '/league', color: '#eab308', desc: 'AI vs 사람 수익률' },
  predict: { icon: '🎯', label: '예측 게임', to: '/predict', color: '#ef4444', desc: '오를까 내릴까' },
  ai: { icon: '🤖', label: 'AI 비서', to: '/ai', color: '#3b82f6', desc: '종목 · 시장 질문' },
  beauty: { icon: '💄', label: '뷰티 랩', to: '/beauty', color: '#ec4899', desc: '성분 분석 · 처방 검토' },
  lotto: { icon: '🍀', label: '오늘의 숫자', to: '/lotto', color: '#10b981', desc: '로또 · 연금복권' },
  quiz: { icon: '📰', label: '뉴스 퀴즈', to: '/quiz', color: '#64748b', desc: '매일 5문제' },
  duel: { icon: '⚔️', label: '친구 대결', to: '/duel', color: '#8b5cf6', desc: '7일 1:1 승부' },
  persona: { icon: '🧭', label: '투자 성향', to: '/persona', color: '#06b6d4', desc: '12문항 테스트' },
  company: { icon: '🏭', label: '기업 정보', to: '/ai?q=', color: '#78716c', desc: 'AI가 정리' },
  activity: { icon: '🏅', label: '내 활동', to: '/activity', color: '#f97316', desc: '배지 · AI 코치' },
  mypage: { icon: '👤', label: '마이페이지', to: '/mypage', color: '#6366f1', desc: '내 정보 · 알림' },
  ideas: { icon: '💡', label: '기능 제안', to: '/ideas', color: '#facc15', desc: '원하는 기능 투표' },
}

// 검색창 아래 한 줄 (위 메뉴와 같은 순서) + 마지막 칸은 "⋯ 전체"
export const SHORTCUT_KEYS = ['stock', 'mock', 'league', 'predict', 'ai', 'beauty', 'lotto', 'quiz', 'duel']

// ☰ 전체 메뉴: 카테고리별
export const CATEGORIES = [
  { title: '투자', icon: '📈', keys: ['stock', 'mock', 'league', 'company'] },
  { title: '게임 · 도전', icon: '🎮', keys: ['predict', 'quiz', 'duel', 'lotto'] },
  { title: 'AI 도구', icon: '✨', keys: ['ai', 'beauty', 'persona'] },
  { title: '내 공간', icon: '🙋', keys: ['activity', 'mypage', 'ideas'] },
]
