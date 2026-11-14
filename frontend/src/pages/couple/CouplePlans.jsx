// ============================================
// 💕 함께하는 약속 (커플 화면 안)
// - 두 사람 중 누가 만들어도 둘 다에게 보이고, 하루 전 · 당일 오전 9시에 둘 다 알림을 받음
// - 내 약속 화면(/plans)의 "💕 커플과 함께" 약속과 같은 것 (GET/POST /api/plans, shared: true)
// - 📷 사진: 약속마다 4장 (POST /api/plans/{id}/photos, 올리기 전에 1600px JPEG로 줄임), 지난 약속의 사진은 "다녀온 약속"에 90일 동안
// - 지울 수 있는 사람은 만든 사람
// - reloadKey가 바뀌면 다시 불러옴 (🎤 챗봇이 약속을 잡았을 때)
// ============================================
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Countdown from './Countdown'
import { shrinkPhoto } from './shrinkPhoto'

const PHOTOS_PER_PLAN = 4

const todayKst = () => new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10)

function CouplePlans({ partnerName, reloadKey }) {

  const [plans, setPlans] = useState(null)
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState(() => ({ title: '', date: todayKst(), time: '', place: '' }))
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const [past, setPast] = useState([])          // 지난 약속 중 사진이 있는 것
  const [view, setView] = useState(null)        // 크게 보는 사진 { src, title }
  const fileRef = useRef(null)
  const target = useRef(null)                   // 사진을 올릴 약속 id

  const load = useCallback(() => {
    fetch('/api/plans')
      .then((res) => res.json())
      .then((json) => setPlans(json.success ? json.plans.filter((p) => p.shared) : []))
      .catch(() => setPlans([]))
    fetch('/api/plans/past')
      .then((res) => res.json())
      .then((json) => setPast(json.success ? json.plans.filter((p) => p.shared) : []))
      .catch(() => setPast([]))
  }, [])

  // 📷 사진 고르기 → 줄여서 올리기
  const pick = (planId) => {
    target.current = planId
    fileRef.current?.click()
  }

  const onFile = async (e) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file || !target.current) return
    setBusy(true)
    setMsg('사진을 올리는 중...')
    try {
      const image = await shrinkPhoto(file)
      const json = await fetch(`/api/plans/${target.current}/photos`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ image }),
      }).then((res) => res.json())
      if (!json.success) throw new Error(json.message)
      setMsg('📷 사진을 올렸어요.')
      load()
    } catch (err) {
      setMsg(err.message || '사진을 올리지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  const removePhoto = async (photo) => {
    if (!window.confirm('이 사진을 지울까요?')) return
    const json = await fetch(`/api/plans/photos/${photo.id}/delete`, { method: 'POST' }).then((res) => res.json()).catch(() => ({}))
    if (json.success) {
      setView(null)
      load()
    } else setMsg(json.message ?? '지우지 못했어요.')
  }

  // 약속 한 개의 사진 줄: 작은 사진들 + [＋ 사진]
  const photoRow = (p, canAdd) => (
    <div className="cp-plans-photos">
      {p.photos.map((f) => (
        <button key={f.id} type="button" className="cp-plans-thumb" onClick={() => setView({ ...f, title: p.title })} aria-label={`${p.title} 사진 크게 보기`}>
          <img src={f.src} alt="" loading="lazy" />
        </button>
      ))}
      {canAdd && p.photos.length < PHOTOS_PER_PLAN && (
        <button type="button" className="cp-plans-addphoto" onClick={() => pick(p.id)} disabled={busy}>📷 사진</button>
      )}
    </div>
  )

  useEffect(() => { load() }, [load, reloadKey])

  const add = async (e) => {
    e.preventDefault()
    if (busy || form.title.trim() === '') return
    setBusy(true)
    setMsg('')
    try {
      const json = await fetch('/api/plans', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form, shared: true }),
      }).then((res) => res.json())
      if (!json.success) throw new Error(json.message)
      setForm((f) => ({ ...f, title: '', time: '', place: '' }))
      setOpen(false)
      setMsg(`💕 약속을 잡았어요. ${partnerName}님에게도 알렸어요.`)
      load()
    } catch (err) {
      setMsg(err.message || '약속을 저장하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  const remove = async (p) => {
    if (!window.confirm(`"${p.title}" 약속을 지울까요? ${partnerName}님의 목록에서도 사라져요.`)) return
    const json = await fetch(`/api/plans/${p.id}/delete`, { method: 'POST' }).then((res) => res.json()).catch(() => ({}))
    if (json.success) load()
    else setMsg(json.message ?? '지우지 못했어요.')
  }

  return (
    <section className="cp-box cp-plans">
      <div className="cp-plans-head">
        <h2>🗓️ 함께하는 약속</h2>
        <button type="button" className="cp-bday-btn cp-bday-add" onClick={() => setOpen((o) => !o)}>{open ? '닫기' : '약속 잡기'}</button>
      </div>

      {open && (
        <form className="cp-plans-form" onSubmit={add}>
          <input className="cp-plans-wide" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} maxLength={60} placeholder="무슨 약속? (예: 저녁 데이트)" aria-label="약속 이름" />
          <input type="date" value={form.date} min={todayKst()} onChange={(e) => setForm({ ...form, date: e.target.value })} required aria-label="날짜" />
          <input type="time" value={form.time} onChange={(e) => setForm({ ...form, time: e.target.value })} aria-label="시간 (선택)" />
          <input className="cp-plans-wide" value={form.place} onChange={(e) => setForm({ ...form, place: e.target.value })} maxLength={40} placeholder="장소 (선택, 예: 강남)" aria-label="장소" />
          <button type="submit" className="btn btn-primary cp-plans-wide" disabled={busy || form.title.trim() === ''}>💕 약속 잡기</button>
        </form>
      )}

      {plans === null && <p className="cp-muted">불러오는 중...</p>}
      {plans && plans.length === 0 && !open && <p className="cp-muted">아직 함께하는 약속이 없어요. [약속 잡기]를 누르거나 🎤에 “토요일 7시 데이트 잡아줘”라고 말해 보세요.</p>}

      {plans && plans.length > 0 && (
        <ul className="cp-plans-list">
          {plans.map((p) => (
            <li key={p.id}>
              <div className="cp-plans-what">
                <b>{p.repeat && '🔁 '}{p.title}</b>
                <span>{Number(p.date.slice(5, 7))}월 {Number(p.date.slice(8, 10))}일 {p.weekday}{p.time ? ` ${p.time}` : ''}{p.place ? ` · ${p.place}` : ''}{p.mine ? '' : ` · ${partnerName}님이 잡음`}</span>
                <Countdown date={p.date} time={p.time} done={p.time ? '지금이에요! 💕' : '오늘이에요! 💕'} />
                {photoRow(p, true)}
              </div>
              {p.mine && <button type="button" className="cp-plans-del" onClick={() => remove(p)} aria-label={`${p.title} 약속 지우기`}>✕</button>}
            </li>
          ))}
        </ul>
      )}

      {/* 지난 약속 중 사진이 있는 것 (최근 90일) */}
      {past.length > 0 && (
        <>
          <h3 className="cp-plans-sub">📷 다녀온 약속</h3>
          <ul className="cp-plans-list">
            {past.map((p) => (
              <li key={p.id}>
                <div className="cp-plans-what">
                  <b>{p.title}</b>
                  <span>{Number(p.date.slice(5, 7))}월 {Number(p.date.slice(8, 10))}일 {p.weekday}{p.place ? ` · ${p.place}` : ''}</span>
                  {photoRow(p, true)}
                </div>
              </li>
            ))}
          </ul>
        </>
      )}

      <input ref={fileRef} type="file" accept="image/*" hidden onChange={onFile} />

      {/* 사진 크게 보기 */}
      {view && (
        <div className="cp-plans-view" role="dialog" aria-modal="true" aria-label={`${view.title} 사진`} onClick={() => setView(null)}>
          <img src={view.src} alt={`${view.title} 사진`} onClick={(e) => e.stopPropagation()} />
          <div onClick={(e) => e.stopPropagation()}>
            <button type="button" className="btn btn-ghost" onClick={() => removePhoto(view)}>지우기</button>
            <button type="button" className="btn btn-primary" onClick={() => setView(null)}>닫기</button>
          </div>
        </div>
      )}

      {msg && <p className="cp-msg" role="status">{msg}</p>}
      <p className="cp-muted cp-plans-foot">하루 전과 당일 오전 9시에 두 분 모두에게 알려드려요. 혼자만의 약속은 <Link to="/plans">내 약속</Link>에서 잡을 수 있어요.</p>
    </section>
  )
}

export default CouplePlans
