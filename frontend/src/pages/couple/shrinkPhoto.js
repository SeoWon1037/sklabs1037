// ============================================
// 📷 사진 줄이기 → data URL (JPEG) — 기념일 앨범과 약속 사진이 같이 씀
// - 올리기 전에 이 화면에서 긴 변 1600px JPEG로 줄임 (휴대폰 원본 10MB → 보통 0.5MB 안팎)
// ============================================
const MAX_SIDE = 1600

export const shrinkPhoto = (file) =>
  new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      const scale = Math.min(1, MAX_SIDE / Math.max(img.width, img.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(img.width * scale)
      canvas.height = Math.round(img.height * scale)
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height)
      URL.revokeObjectURL(url)
      resolve(canvas.toDataURL('image/jpeg', 0.85))
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('이 사진은 열 수 없어요. JPG나 PNG로 다시 골라주세요.'))
    }
    img.src = url
  })
