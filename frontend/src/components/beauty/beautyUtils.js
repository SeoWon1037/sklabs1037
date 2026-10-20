// ============================================
// 뷰티 랩 공통
// ============================================

export const SKIN_TYPES = ['민감성', '건성', '지성', '여드름', '복합성', '중성']

// POST JSON
export const postJson = async (url, body) => {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  const json = await res.json().catch(() => ({ success: false, message: '서버 응답을 읽지 못했어요.' }))
  if (res.status === 413) return { success: false, message: '사진이 너무 커요. 더 작은 사진으로 올려주세요.' }
  return json
}

// 사진을 긴 변 1568px 이하 JPEG로 줄이기 (Claude 권장 크기 → 전송량·비용·속도 모두 이득)
export const resizeImage = (file, maxSide = 1568, quality = 0.85) =>
  new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      const scale = Math.min(1, maxSide / Math.max(img.width, img.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(img.width * scale)
      canvas.height = Math.round(img.height * scale)
      const ctx = canvas.getContext('2d')
      ctx.fillStyle = '#fff'
      ctx.fillRect(0, 0, canvas.width, canvas.height)
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
      URL.revokeObjectURL(url)
      resolve(canvas.toDataURL('image/jpeg', quality))
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('사진을 열 수 없어요. JPG나 PNG로 올려주세요.'))
    }
    img.src = url
  })

// 역할 → 색 (같은 역할은 늘 같은 색)
const CATEGORY_TONES = {
  보습: 'blue', 장벽: 'blue', 유연: 'blue', '보습·진정': 'blue', '보습·각질': 'blue',
  진정: 'green', '진정·보습': 'green', '진정·영양': 'green', '진정·트러블': 'green', 항산화: 'green', '항산화·브라이트닝': 'green',
  미백: 'violet', '미백·진정': 'violet', 주름: 'violet', 탄력: 'violet', 자외선차단: 'violet', '각질·여드름': 'violet', 두피: 'violet',
  각질: 'amber', '각질·보습': 'amber', 향료: 'amber', '향료·방부': 'amber', 방부제: 'amber', 방부보조: 'amber', 세정: 'amber', 청량: 'amber',
}
export const categoryTone = (c) => CATEGORY_TONES[c] ?? 'gray'

export const formatDay = (value) => {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
