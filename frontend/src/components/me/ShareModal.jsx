// ============================================
// 📸 내 성적 카드 만들기 (/activity 의 버튼)
// - POST /api/me/share → 지금 숫자로 카드 + 링크(/s/코드)
// - 링크 복사 / 공유하기(휴대폰 공유 창) / 이미지 저장(PNG)
// ============================================
import { useEffect, useState } from 'react'
import ShareCard from './ShareCard'
import { drawShareCard } from './shareImage'

function ShareModal({ onClose }) {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')

  useEffect(() => {
    let ignore = false
    fetch('/api/me/share', { method: 'POST' })
      .then((res) => res.json())
      .then((json) => {
        if (ignore) return
        if (json.success) setData(json)
        else setError(json.message || '카드를 만들지 못했어요.')
      })
      .catch(() => { if (!ignore) setError('서버에 연결할 수 없습니다.') })
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    return () => {
      ignore = true
      document.removeEventListener('keydown', onKey)
    }
  }, [onClose])

  const url = data ? `${window.location.origin}/s/${data.code}` : ''
  const text = data ? `${data.card.nickname}님의 이번 주 SKLabs 기록` : ''

  const flash = (msg) => {
    setToast(msg)
    setTimeout(() => setToast(''), 1800)
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url)
      flash('링크를 복사했어요')
    } catch {
      flash(url)
    }
  }

  const share = async () => {
    try { await navigator.share({ title: 'SKLabs', text, url }) } catch { /* 닫으면 그대로 */ }
  }

  const save = () => {
    drawShareCard(data.card).toBlob(async (blob) => {
      if (!blob) return
      const file = new File([blob], `sklabs-${data.code}.png`, { type: 'image/png' })
      // 휴대폰: 공유 창으로 바로 (인스타·카톡), 안 되면 내려받기
      if (navigator.canShare?.({ files: [file] })) {
        try {
          await navigator.share({ files: [file], text: `${text} ${url}` })
          return
        } catch { /* 취소하면 내려받기로 */ }
      }
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = file.name
      a.click()
      URL.revokeObjectURL(a.href)
    }, 'image/png')
  }

  return (
    <div className="act-modal" role="dialog" aria-modal="true" aria-label="내 성적 카드" onClick={onClose}>
      <div className="act-modal-box" onClick={(e) => e.stopPropagation()}>
        <div className="act-modal-head">
          <strong>📸 내 성적 카드</strong>
          <button type="button" className="act-modal-x" onClick={onClose} aria-label="닫기">✕</button>
        </div>
        {error && <p className="act-muted">{error}</p>}
        {!data && !error && <p className="act-muted">카드를 만드는 중...</p>}
        {data && (
          <>
            <ShareCard card={data.card} />
            <div className="act-modal-actions">
              <button type="button" className="btn btn-primary" onClick={save}>🖼️ 이미지 저장</button>
              <button type="button" className="btn btn-ghost" onClick={copy}>🔗 링크 복사</button>
              {typeof navigator.share === 'function' && <button type="button" className="btn btn-ghost" onClick={share}>📤 공유</button>}
            </div>
            <p className="act-muted act-modal-note">링크를 받은 사람은 지금 이 숫자만 볼 수 있어요. 이메일 같은 계정 정보는 들어가지 않아요.</p>
          </>
        )}
        {toast && <p className="act-toast">{toast}</p>}
      </div>
    </div>
  )
}

export default ShareModal
