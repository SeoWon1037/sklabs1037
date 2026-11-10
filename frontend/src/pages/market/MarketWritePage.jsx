// ============================================
// 🛍️ 상품 올리기 · 고치기 (주소: /market/new, /market/:id/edit — 로그인 필요)
// - 사진 최대 5장 (첫 사진이 대표) → 올리기 전에 이 화면에서 줄임 (긴 변 1280px + 목록용 360px)
// - 카테고리 · 제목 · 가격(0원 = 나눔) · 상태 · 거래 방법 · 지역 · 설명
// - ✨ AI 글쓰기 도우미: 제목과 메모만 쓰면 제목·설명·카테고리 초안 (하루 10번, 없는 사실은 빈칸으로)
// - 규칙 검사(금지 품목 등)는 서버 MarketRules → 걸리면 이유를 보여줌
// - 백엔드: POST /api/market/items, POST /api/market/items/{id}, POST /api/market/ai-write
// ============================================
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { CATEGORIES, CONDITIONS, MAX_PHOTOS, TRADE_TYPES, getJson, guideText, postJson, shrinkPhoto } from './marketUtil'
import './Market.css'

const EMPTY = { category: '', title: '', price: '', description: '', cond: 'USED', tradeType: 'BOTH', region: '' }

function MarketWritePage() {

  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const fileInput = useRef(null)

  const [form, setForm] = useState(EMPTY)
  const [photos, setPhotos] = useState([])        // { key, src, id?(이미 올린 사진), image?, thumb?(새 사진) }
  const [ready, setReady] = useState(!editing)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const [aiNote, setAiNote] = useState('')
  const [guide, setGuide] = useState(null)        // 📊 비슷한 상품 시세 (3개 이상 있을 때만)

  // 로그인 확인 + (고치기면) 지금 내용 불러오기
  useEffect(() => {
    let ignore = false
    getJson('/api/user/me').then((me) => {
      if (ignore) return
      if (!me.loggedIn) return navigate('/login', { replace: true })
      if (me.user?.demo) setError('체험 계정에서는 상품을 올릴 수 없어요. 회원가입하면 바로 올릴 수 있어요.')
      if (!editing) return
      getJson(`/api/market/items/${id}`).then((j) => {
        if (ignore) return
        if (!j.success || !j.mine) return setError(j.message || '내 상품만 고칠 수 있어요.')
        setForm({
          category: j.category, title: j.title, price: String(j.price), description: j.description,
          cond: j.cond, tradeType: j.tradeType, region: j.region ?? '',
        })
        setPhotos(j.photoIds.map((pid, i) => ({ key: `old-${pid}`, id: pid, src: j.photos[i] })))
        setReady(true)
      })
    })
    return () => { ignore = true }
  }, [id, editing, navigate])

  // 📊 카테고리와 제목이 정해지면 잠깐 기다렸다가 비슷한 상품 시세를 물어봄
  const guideTitle = form.title.trim()
  useEffect(() => {
    if (!form.category || guideTitle.length < 2) return undefined
    let ignore = false
    const t = setTimeout(() => {
      getJson(`/api/market/price-guide?category=${form.category}&title=${encodeURIComponent(guideTitle)}`)
        .then((j) => { if (!ignore) setGuide(j.success && j.priceGuide?.count ? { ...j.priceGuide, key: `${form.category}:${guideTitle}` } : null) })
    }, 600)
    return () => {
      ignore = true
      clearTimeout(t)
    }
  }, [form.category, guideTitle])

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  const addPhotos = async (e) => {
    const files = Array.from(e.target.files ?? []).slice(0, MAX_PHOTOS - photos.length)
    e.target.value = ''
    if (files.length === 0) return
    setBusy('photo')
    setError('')
    try {
      const added = []
      for (const f of files) {
        const p = await shrinkPhoto(f)
        added.push({ key: `new-${f.name}-${f.size}-${f.lastModified}`, src: p.image, ...p })
      }
      setPhotos((prev) => [...prev, ...added.filter((a) => !prev.some((x) => x.key === a.key))].slice(0, MAX_PHOTOS))
    } catch (err) {
      setError(err.message)
    }
    setBusy('')
  }

  const removePhoto = (key) => setPhotos((prev) => prev.filter((p) => p.key !== key))
  const makeFirst = (key) => setPhotos((prev) => [prev.find((p) => p.key === key), ...prev.filter((p) => p.key !== key)])

  const aiWrite = async () => {
    setBusy('ai')
    setError('')
    setAiNote('')
    const j = await postJson('/api/market/ai-write', { name: form.title, memo: form.description })
    setBusy('')
    if (!j.success) return setError(j.message)
    setForm((f) => ({ ...f, title: j.title, description: j.description, category: f.category || j.category }))
    setAiNote(`AI가 초안을 썼어요. 괄호로 남긴 빈칸과 사실이 맞는지 꼭 확인하고 고쳐주세요. (오늘 ${j.remaining}번 남음)`)
  }

  const submit = async (e) => {
    e.preventDefault()
    setBusy('save')
    setError('')
    const body = {
      ...form,
      price: form.price === '' ? '' : Number(String(form.price).replaceAll(',', '')),
      photos: photos.filter((p) => !p.id).map((p) => ({ image: p.image, thumb: p.thumb })),
    }
    // 고치기: 남길 사진 번호 (순서대로). 새 사진은 그 뒤에 붙음
    if (editing) body.keepPhotoIds = photos.filter((p) => p.id).map((p) => p.id)
    const j = await postJson(editing ? `/api/market/items/${id}` : '/api/market/items', body)
    setBusy('')
    if (!j.success) return setError(j.message)
    navigate(`/market/${j.id}`, { replace: true })
  }

  const priceNum = Number(String(form.price).replaceAll(',', ''))
  const canSubmit = ready && !busy && form.category && form.title.trim().length >= 2 && form.description.trim().length >= 10 && form.price !== '' && priceNum >= 0

  return (
    <main className="mk-page mk-write">
      <Link to={editing ? `/market/${id}` : '/'} className="mk-back">‹ {editing ? '상품으로' : '장터로'}</Link>
      <h1>{editing ? '상품 고치기' : '상품 올리기'}</h1>

      <form onSubmit={submit} className="mk-form">
        <div className="mk-field">
          <span className="mk-label">사진 <small>{photos.length}/{MAX_PHOTOS} · 첫 사진이 대표 사진이에요</small></span>
          <div className="mk-photo-row">
            {photos.length < MAX_PHOTOS && (
              <button type="button" className="mk-photo-add" onClick={() => fileInput.current?.click()} disabled={busy === 'photo'}>
                <span aria-hidden="true">📷</span>{busy === 'photo' ? '줄이는 중…' : '사진 추가'}
              </button>
            )}
            {photos.map((p, i) => (
              <div key={p.key} className="mk-photo-item">
                <img src={p.src} alt={`올릴 사진 ${i + 1}`} />
                {i === 0 ? <span className="mk-photo-main">대표</span> : <button type="button" className="mk-photo-first" onClick={() => makeFirst(p.key)}>대표로</button>}
                <button type="button" className="mk-photo-x" onClick={() => removePhoto(p.key)} aria-label={`사진 ${i + 1} 빼기`}>✕</button>
              </div>
            ))}
          </div>
          <input ref={fileInput} type="file" accept="image/*" multiple hidden onChange={addPhotos} />
        </div>

        <label className="mk-field">
          <span className="mk-label">제목</span>
          <input value={form.title} onChange={set('title')} maxLength={40} placeholder="브랜드, 모델명, 핵심 특징 (예: 아이폰 15 프로 256GB 블루)" required />
        </label>

        <div className="mk-field">
          <span className="mk-label">카테고리</span>
          <div className="mk-chips">
            {CATEGORIES.map(([code, name, icon]) => (
              <button key={code} type="button" className={form.category === code ? 'mk-chip-on' : ''} aria-pressed={form.category === code}
                onClick={() => setForm((f) => ({ ...f, category: code }))}>
                {icon} {name}
              </button>
            ))}
          </div>
        </div>

        <div className="mk-two">
          <label className="mk-field">
            <span className="mk-label">가격 <small>나눔은 0원</small></span>
            <span className="mk-price-input">
              <input value={form.price} onChange={(e) => setForm((f) => ({ ...f, price: e.target.value.replace(/[^0-9]/g, '').slice(0, 8) }))}
                inputMode="numeric" placeholder="0" required aria-label="가격" />
              원
            </span>
            {form.price !== '' && <small className="mk-muted">{priceNum === 0 ? '나눔 🎁으로 올라가요' : `${priceNum.toLocaleString('ko-KR')}원`}</small>}
            {guide && form.category && guideTitle.length >= 2 && <small className="mk-guide">📊 {guideText(guide)}</small>}
          </label>
          <label className="mk-field">
            <span className="mk-label">거래 희망 지역 <small>선택</small></span>
            <input value={form.region} onChange={set('region')} maxLength={20} placeholder="예: 서울 강남구" />
          </label>
        </div>

        <div className="mk-two">
          <div className="mk-field">
            <span className="mk-label">상품 상태</span>
            <div className="mk-chips">
              {CONDITIONS.map(([v, label]) => (
                <button key={v} type="button" className={form.cond === v ? 'mk-chip-on' : ''} aria-pressed={form.cond === v} onClick={() => setForm((f) => ({ ...f, cond: v }))}>{label}</button>
              ))}
            </div>
          </div>
          <div className="mk-field">
            <span className="mk-label">거래 방법</span>
            <div className="mk-chips">
              {TRADE_TYPES.map(([v, label]) => (
                <button key={v} type="button" className={form.tradeType === v ? 'mk-chip-on' : ''} aria-pressed={form.tradeType === v} onClick={() => setForm((f) => ({ ...f, tradeType: v }))}>{label}</button>
              ))}
            </div>
          </div>
        </div>

        <div className="mk-field">
          <span className="mk-label">
            설명 <small>{form.description.length}/1000</small>
            <button type="button" className="mk-ai-btn" onClick={aiWrite} disabled={!!busy || form.title.trim().length < 2}
              title="제목과 지금 쓴 메모로 AI가 판매 글 초안을 써요">
              {busy === 'ai' ? 'AI가 쓰는 중…' : '✨ AI로 다듬기'}
            </button>
          </span>
          <textarea aria-label="설명" value={form.description} onChange={set('description')} rows={7} maxLength={1000}
            placeholder={'구매 시기, 사용 기간, 하자, 구성품을 적어주세요.\n메모만 짧게 쓰고 [✨ AI로 다듬기]를 눌러도 돼요. (예: 작년 겨울 구매, 3번 입음, 드라이 완료)'} required />
        </div>
        {aiNote && <p className="mk-notice" role="status">{aiNote}</p>}

        {error && <p className="mk-error" role="alert">{error}</p>}

        <div className="mk-submit">
          <p className="mk-muted">술·담배·의약품·계정·신분증 등은 거래할 수 없어요. 신고가 3번 쌓이면 자동으로 숨겨져요.</p>
          <button type="submit" className="mk-chat" disabled={!canSubmit}>
            {busy === 'save' ? '올리는 중…' : editing ? '고친 내용 저장' : '상품 올리기'}
          </button>
        </div>
      </form>
    </main>
  )
}

export default MarketWritePage
