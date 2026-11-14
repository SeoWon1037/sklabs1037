// ============================================
// 🔠 글자 크기 (☰ 전체 메뉴 맨 아래에서 고름, 이 브라우저에 저장)
// - 화면 전체를 비율로 키움 (CSS zoom) → 글자 · 버튼 · 여백이 같이 커져서 모양이 안 깨짐
// - 앱 시작 때(main.jsx) 바로 적용해서 깜빡이지 않게
// - 휴대폰에서는 상단바만 원래 크기 (AllMenu.css, 아이콘이 많아 두 줄로 밀리지 않게)
// ============================================
export const TEXT_SIZES = [
  { key: 's', label: '작게', zoom: 0.92 },
  { key: 'm', label: '보통', zoom: 1 },
  { key: 'l', label: '크게', zoom: 1.08 },
  { key: 'xl', label: '아주 크게', zoom: 1.15 },
]

const STORAGE_KEY = 'textSize'

export function getTextSize() {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    if (TEXT_SIZES.some((s) => s.key === v)) return v
  } catch {
    // 저장소를 못 쓰는 환경이면 보통
  }
  return 'm'
}

export function applyTextSize(key) {
  const size = TEXT_SIZES.find((s) => s.key === key) ?? TEXT_SIZES[1]
  document.body.style.zoom = size.zoom === 1 ? '' : String(size.zoom)
  document.documentElement.dataset.textSize = size.key
  // 화면 높이·너비(vh, vw)도 같이 커지므로, 꽉 찬 화면(AI 챗봇·메시지·메뉴)은 CSS에서 이 값으로 나눠서 맞춤
  document.documentElement.style.setProperty('--tz', String(size.zoom))
  try {
    localStorage.setItem(STORAGE_KEY, size.key)
  } catch {
    // 저장 실패해도 지금 화면에는 적용됨
  }
}
