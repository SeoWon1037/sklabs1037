// ============================================
// 📸 우리의 기념일 앨범 (커플 화면 아래)
// - 지난 기념일(100일 · 200일 · 1주년 …)마다 사진 칸, 아무 때나 올리는 "추억" 칸
// - 다음 기념일은 🔒 그날 열려요
// - 사진은 올리기 전에 이 화면에서 긴 변 1600px JPEG로 줄임 (휴대폰 원본 10MB → 보통 0.5MB 안팎)
// - 두 사람만 볼 수 있음
// - 백엔드: GET/POST /api/couple/photos, POST /api/couple/photos/{id}/delete, GET /api/couple/photos/{id}/image
// ============================================
import { useEffect, useRef, useState } from 'react'

const MAX_SIDE = 1600
const dot = (d) => d.replaceAll('-', '.')

// 사진 줄이기 → data URL (JPEG)
const shrink = (file) =>
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

function CoupleAlbum() {

  const [album, setAlbum] = useState(null)
  const [draft, setDraft] = useState(null)      // { label, image, caption }
  const [view, setView] = useState(null)        // 크게 보기
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const fileRef = useRef(null)
  const target = useRef('')

  useEffect(() => {
    let ignore = false
    fetch('/api/couple/photos')
      .then((res) => res.json())
      .then((json) => { if (!ignore && json.success) setAlbum(json) })
      .catch(() => {})
    return () => { ignore = true }
  }, [])

  const pick = (label) => {
    target.current = label
    setMsg('')
    fileRef.current?.click()
  }

  const onFile = async (e) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    try {
      const image = await shrink(file)
      setDraft({ label: target.current, image, caption: '' })
    } catch (err) {
      setMsg(err.message)
    }
  }

  const send = async (url, body) => {
    setBusy(true)
    setMsg('')
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: body ? { 'Content-Type': 'application/json' } : undefined,
        body: body ? JSON.stringify(body) : undefined,
      })
      const json = await res.json()
      if (!json.success) throw new Error(json.message)
      setAlbum(json)
      return true
    } catch (err) {
      setMsg(err.message || '처리하지 못했어요.')
      return false
    } finally {
      setBusy(false)
    }
  }

  const upload = async () => {
    const ok = await send('/api/couple/photos', { milestone: draft.label, caption: draft.caption, image: draft.image })
    if (ok) setDraft(null)
  }

  const remove = async (p) => {
    const ok = await send(`/api/couple/photos/${p.id}/delete`)
    if (ok) setView(null)
  }

  if (!album) return null

  return (
    <section className="ca">
      <h2>📸 우리의 기념일 앨범</h2>
      <input ref={fileRef} type="file" accept="image/*" hidden onChange={onFile} />

      <div className="ca-locked">
        🔒 <b>{album.next.label}</b> 칸은 {dot(album.next.date)}에 열려요 {album.next.dDay > 0 && `(D-${album.next.dDay})`}
      </div>

      {album.slots.map((s) => (
        <div key={s.label} className="ca-slot">
          <div className="ca-slot-head">
            <strong>{s.label === '추억' ? '💭 추억' : `🎉 ${s.label}`}</strong>
            {s.date && <span>{dot(s.date)}</span>}
            {s.photos.length < album.perSlot && (
              <button type="button" className="ca-add" onClick={() => pick(s.label)} disabled={busy}>+ 사진</button>
            )}
          </div>
          {s.photos.length > 0 ? (
            <div className="ca-grid">
              {s.photos.map((p) => (
                <button key={p.id} type="button" className="ca-thumb" onClick={() => setView(p)}>
                  <img src={p.src} alt={p.caption || `${s.label} 사진`} loading="lazy" />
                </button>
              ))}
            </div>
          ) : (
            <button type="button" className="ca-empty" onClick={() => pick(s.label)}>
              {s.label === '추억' ? '아무 날 사진이나 올려보세요' : `${s.label} 기념사진을 올려보세요`}
            </button>
          )}
        </div>
      ))}

      {msg && <p className="ca-msg">{msg}</p>}

      {/* 올리기 전 미리보기 */}
      {draft && (
        <div className="ca-modal" role="dialog" aria-modal="true" aria-label="사진 올리기" onClick={() => !busy && setDraft(null)}>
          <div className="ca-modal-box" onClick={(e) => e.stopPropagation()}>
            <strong>{draft.label} 사진</strong>
            <img src={draft.image} alt="올릴 사진 미리보기" className="ca-preview" />
            <input
              value={draft.caption}
              onChange={(e) => setDraft({ ...draft, caption: e.target.value })}
              placeholder="한 줄 남기기 (선택)"
              maxLength={100}
            />
            <div className="ca-modal-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setDraft(null)} disabled={busy}>취소</button>
              <button type="button" className="btn btn-primary" onClick={upload} disabled={busy}>{busy ? '올리는 중...' : '올리기'}</button>
            </div>
          </div>
        </div>
      )}

      {/* 크게 보기 */}
      {view && (
        <div className="ca-modal" role="dialog" aria-modal="true" aria-label="사진 보기" onClick={() => setView(null)}>
          <div className="ca-modal-box ca-view" onClick={(e) => e.stopPropagation()}>
            <img src={view.src} alt={view.caption || '기념일 사진'} />
            {view.caption && <p className="ca-caption">{view.caption}</p>}
            <div className="ca-modal-actions">
              <button type="button" className="ca-del" onClick={() => remove(view)} disabled={busy}>사진 지우기</button>
              <button type="button" className="btn btn-ghost" onClick={() => setView(null)}>닫기</button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}

export default CoupleAlbum
