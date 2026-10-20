// ============================================
// 뷰티 랩 > 성분 비교의 제품 입력 칸 (사진 / 전성분 글 + 제품 이름)
// ============================================
import { useRef, useState } from 'react'
import { resizeImage } from './beautyUtils'

function ProductInput({ label, value, onChange }) {

  const fileRef = useRef(null)
  const [error, setError] = useState('')

  const pickFile = async (file) => {
    if (!file) return
    setError('')
    try {
      onChange({ ...value, image: await resizeImage(file) })
    } catch (e) {
      setError(e.message)
    }
  }

  const isImage = value.mode === 'IMAGE'

  return (
    <div className="bl-card bl-product">
      <div className="bl-product-head">
        <span className="bl-product-label">{label}</span>
        <input
          className="bl-product-name"
          value={value.name}
          onChange={(e) => onChange({ ...value, name: e.target.value })}
          placeholder="제품 이름 (선택)"
          maxLength={60}
        />
      </div>

      <div className="bl-mode bl-mode-small" role="tablist" aria-label={`${label} 입력 방식`}>
        <button type="button" role="tab" aria-selected={isImage} className={isImage ? 'bl-mode-active' : ''} onClick={() => onChange({ ...value, mode: 'IMAGE' })}>📷 사진</button>
        <button type="button" role="tab" aria-selected={!isImage} className={!isImage ? 'bl-mode-active' : ''} onClick={() => onChange({ ...value, mode: 'TEXT' })}>⌨️ 글</button>
      </div>

      {isImage ? (
        <div className={`bl-drop ${value.image ? 'bl-drop-filled' : ''}`} onDragOver={(e) => e.preventDefault()} onDrop={(e) => { e.preventDefault(); pickFile(e.dataTransfer.files?.[0]) }}>
          {value.image ? (
            <>
              <img src={value.image} alt={`${label} 성분표`} className="bl-preview bl-preview-small" />
              <button type="button" className="bl-link" onClick={() => { onChange({ ...value, image: null }); if (fileRef.current) fileRef.current.value = '' }}>다른 사진</button>
            </>
          ) : (
            <button type="button" className="bl-drop-btn" onClick={() => fileRef.current?.click()}>
              <span className="bl-drop-icon">📷</span>
              <strong>전성분 사진 올리기</strong>
            </button>
          )}
          <input ref={fileRef} type="file" accept="image/*" capture="environment" hidden onChange={(e) => pickFile(e.target.files?.[0])} />
        </div>
      ) : (
        <div className="bl-text">
          <textarea
            value={value.text}
            onChange={(e) => onChange({ ...value, text: e.target.value })}
            placeholder="전성분을 붙여 넣어주세요"
            maxLength={3000}
            rows={5}
          />
        </div>
      )}
      {error && <p className="bl-error">{error}</p>}
    </div>
  )
}

export default ProductInput
