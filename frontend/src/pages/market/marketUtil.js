// ============================================
// 🛍️ 중고거래 공통 (카테고리 아이콘 · 가격/시간 글자 · 사진 줄이기 · 요청)
// ============================================

// 카테고리 (서버 MarketRules.CATEGORIES와 같은 코드 · 순서)
export const CATEGORIES = [
  ['DIGITAL', '디지털·가전', '📱'], ['FASHION', '의류', '👕'], ['ACC', '패션잡화', '👜'], ['BEAUTY', '뷰티·미용', '💄'],
  ['FURNITURE', '가구·인테리어', '🛋️'], ['LIVING', '생활·주방', '🍳'], ['BOOK', '도서·티켓', '📚'], ['SPORTS', '스포츠·레저', '⚽'],
  ['HOBBY', '게임·취미', '🎮'], ['KIDS', '유아동', '🧸'], ['PET', '반려동물', '🐶'], ['ETC', '기타', '📦'],
]
export const CATEGORY_ICON = Object.fromEntries(CATEGORIES.map(([code, , icon]) => [code, icon]))

export const CONDITIONS = [['NEW', '새 상품'], ['LIKE_NEW', '거의 새것'], ['USED', '사용감 있음']]
export const TRADE_TYPES = [['BOTH', '직거래·택배'], ['DIRECT', '직거래만'], ['DELIVERY', '택배만']]
export const SORTS = [['RECENT', '최신순'], ['POPULAR', '인기순'], ['LOW', '낮은 가격순'], ['HIGH', '높은 가격순']]

export const MAX_PHOTOS = 5

export const priceText = (price) => (Number(price) === 0 ? '나눔 🎁' : `${Number(price).toLocaleString('ko-KR')}원`)

// "3분 전" (기준 시각 now는 화면이 데이터를 받은 때 → 렌더 중 Date.now()를 부르지 않게)
export const timeAgo = (ms, now) => {
  const m = Math.max(0, Math.floor((now - ms) / 60000))
  if (m < 1) return '방금'
  if (m < 60) return `${m}분 전`
  if (m < 1440) return `${Math.floor(m / 60)}시간 전`
  if (m < 43200) return `${Math.floor(m / 1440)}일 전`
  return `${Math.floor(m / 43200)}달 전`
}

export const getJson = (url) => fetch(url).then((r) => r.json()).catch(() => ({ success: false, message: '서버에 연결하지 못했어요.' }))

export const postJson = (url, body) =>
  fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body ?? {}) })
    .then((r) => r.json())
    .catch(() => ({ success: false, message: '서버에 연결하지 못했어요.' }))

// 사진 한 장 → { image: 큰 그림(긴 변 1280px), thumb: 목록용(360px 정사각형으로 가운데 자르기) } 둘 다 JPEG data URL
// - 휴대폰 원본 5~10MB → 보통 150~300KB + 20KB
export const shrinkPhoto = (file) =>
  new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      const draw = (w, h, sx, sy, sw, sh, quality) => {
        const canvas = document.createElement('canvas')
        canvas.width = w
        canvas.height = h
        const ctx = canvas.getContext('2d')
        ctx.fillStyle = '#fff'   // 투명 PNG는 흰 바탕
        ctx.fillRect(0, 0, w, h)
        ctx.drawImage(img, sx, sy, sw, sh, 0, 0, w, h)
        return canvas.toDataURL('image/jpeg', quality)
      }
      const scale = Math.min(1, 1280 / Math.max(img.width, img.height))
      const image = draw(Math.round(img.width * scale), Math.round(img.height * scale), 0, 0, img.width, img.height, 0.82)
      const side = Math.min(img.width, img.height)
      const thumb = draw(360, 360, (img.width - side) / 2, (img.height - side) / 2, side, side, 0.72)
      URL.revokeObjectURL(url)
      resolve({ image, thumb })
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('이 사진은 열 수 없어요. JPG나 PNG로 다시 골라주세요.'))
    }
    img.src = url
  })
