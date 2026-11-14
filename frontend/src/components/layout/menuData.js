// ============================================
// 메인 바로가기 · ☰ 전체 메뉴에 쓰는 서비스 목록 (한 곳에서 관리)
// - color: 아이콘 칸 배경색
// ============================================
export const SERVICES = {
  market: { icon: '🛍️', label: '중고거래', to: '/market', color: '#f97316', desc: '사고 팔고 채팅' },
  sell: { icon: '➕', label: '판매하기', to: '/market/new', color: '#f97316', desc: '사진 한 장이면 끝' },
  mymarket: { icon: '🧺', label: '내 장터', to: '/market/my', color: '#f97316', desc: '판매 내역 · 찜' },
  invest: { icon: '📰', label: '투자 홈', to: '/', color: '#3b82f6', desc: '뉴스 · 시세 · 날씨' },
  stock: { icon: '📈', label: '주식 종목', to: '/stock', color: '#22c55e', desc: '시세 · 차트 · 토론' },
  mock: { icon: '💰', label: '모의투자', to: '/mock', color: '#f59e0b', desc: '가상 3,000만 원' },
  whatif: { icon: '⏳', label: '그때 샀더라면', to: '/whatif', color: '#ef4444', desc: '지금 얼마가 됐을까' },
  league: { icon: '🏆', label: 'AI 리그', to: '/league', color: '#eab308', desc: 'AI vs 사람 수익률' },
  predict: { icon: '🎯', label: '예측 게임', to: '/predict', color: '#ef4444', desc: '오를까 내릴까' },
  ai: { icon: '🤖', label: 'AI 챗봇', to: '/ai', color: '#3b82f6', desc: '무엇이든 질문' },
  lotto: { icon: '🍀', label: '오늘의 숫자', to: '/lotto', color: '#10b981', desc: '로또 · 연금복권' },
  quiz: { icon: '📰', label: '뉴스 퀴즈', to: '/quiz', color: '#64748b', desc: '매일 5문제' },
  duel: { icon: '⚔️', label: '친구 대결', to: '/duel', color: '#8b5cf6', desc: '7일 1:1 승부' },
  translate: { icon: '🌐', label: '번역', to: '/translate', color: '#0ea5e9', desc: '말하면 번역해서 읽어줘요' },
  tree: { icon: '🎄', label: '글자 트리', to: '/tree', color: '#16a34a', desc: '자라나는 크리스마스 트리' },
  persona: { icon: '🧭', label: '투자 성향', to: '/persona', color: '#06b6d4', desc: '12문항 테스트' },
  calendar: { icon: '📅', label: '증시 일정', to: '/calendar', color: '#0ea5e9', desc: '휴장 · 금리 · 만기일' },
  company: { icon: '🏭', label: '기업 정보', to: '/ai?q=', color: '#78716c', desc: 'AI가 정리' },
  activity: { icon: '🏅', label: '내 활동', to: '/activity', color: '#f97316', desc: '배지 · AI 코치' },
  mypage: { icon: '👤', label: '마이페이지', to: '/mypage', color: '#6366f1', desc: '내 정보 · 알림' },
  messages: { icon: '💬', label: '메시지', to: '/messages', color: '#22c55e', desc: '1:1 대화' },
  couple: { icon: '💕', label: '커플', to: '/couple', color: '#ec4899', desc: '함께한 날 세기' },
  plans: { icon: '🗓️', label: '내 약속', to: '/plans', color: '#6366f1', desc: '약속 알림 · 음성 브리핑' },
  about: { icon: '👤', label: '만든 사람', to: '/about', color: '#0ea5e9', desc: '경력 · 기술 스택' },
  app: { icon: '📲', label: '앱 설치', to: '/app', color: '#0ea5e9', desc: '휴대폰 · PC에 설치' },
  support: { icon: '📮', label: '문의하기', to: '/support', color: '#f97316', desc: '버그 · 질문 · 제안' },
  status: { icon: '🟢', label: '서비스 상태', to: '/status', color: '#22c55e', desc: '지금 잘 도는지' },
  ideas: { icon: '💡', label: '기능 제안', to: '/ideas', color: '#facc15', desc: '원하는 기능 투표' },
}

// 첫 화면 검색창 아래 한 줄: 주식 종목 · AI 챗봇 · 중고거래 셋만 (나머지는 왼쪽 위 ☰ 전체 메뉴)
// - 여기서 빠진 기능도 없어진 게 아님 → ☰ 전체 메뉴에 모두 있음
export const SHORTCUT_KEYS = ['stock', 'ai', 'market']

// ☰ 전체 메뉴: 묶음별 (모든 기능이 여기에 있음)
export const CATEGORIES = [
  { title: '시세', icon: '📈', keys: ['invest', 'stock', 'calendar', 'ai', 'translate', 'company'] },
  { title: '모의투자', icon: '💰', keys: ['mock', 'league', 'duel'] },
  { title: '놀이터', icon: '🎮', keys: ['whatif', 'predict', 'quiz', 'persona', 'tree', 'lotto'] },
  { title: '중고거래', icon: '🛍️', keys: ['market', 'sell', 'mymarket', 'messages'] },
  { title: '내 공간', icon: '🙋', keys: ['messages', 'plans', 'activity', 'couple', 'mypage'] },
  { title: 'SKLabs', icon: '🧪', keys: ['support', 'ideas', 'status', 'app', 'about'] },
]
