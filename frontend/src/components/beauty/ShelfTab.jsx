// ============================================
// 뷰티 랩 > 🧴 내 화장대 · 성분 궁합
// - 쓰는 화장품을 담아두고(이 브라우저에만 저장) 아침·저녁 루틴 순서와 같이 쓰면 안 좋은 조합을 확인
//   예) 레티놀 + AHA·BHA 같은 루틴 → 자극 주의 / 아침 레티놀인데 선크림 없음 → 주의
// - AI 없이 성분 사전 + 규칙 (로그인 없이 누구나)
// - 백엔드 API: POST /api/beauty/shelf/check
// ============================================
import { useEffect, useState } from 'react'
import { SKIN_TYPES, postJson } from './beautyUtils'

const STORAGE_KEY = 'sklabs.shelf.v1'
const MAX = 12

const STEPS = [
  ['CLEANSER', '🫧 클렌저'],
  ['TONER', '💧 토너'],
  ['SERUM', '🧪 세럼·앰플'],
  ['CREAM', '🧴 크림·로션'],
  ['SUNSCREEN', '☀️ 선크림'],
]
const TIMES = [['AM', '아침'], ['PM', '저녁'], ['BOTH', '아침·저녁']]

const LEVEL = {
  HIGH: { label: '주의', cls: 'bl-badge-red' },
  MEDIUM: { label: '확인', cls: 'bl-badge-amber' },
  LOW: { label: '참고', cls: 'bl-tag-blue' },
  INFO: { label: '알아두기', cls: 'bl-tag-green' },
}

// 이름으로 단계 추측 (직접 바꿀 수 있음)
const guessStep = (name) => {
  if (/클렌|폼|오일|워시|cleans/i.test(name)) return 'CLEANSER'
  if (/토너|스킨|패드|toner/i.test(name)) return 'TONER'
  if (/선|자외선|spf|sun/i.test(name)) return 'SUNSCREEN'
  if (/크림|로션|밤|cream|lotion/i.test(name)) return 'CREAM'
  return 'SERUM'
}

const SAMPLE = [
  { name: '약산성 폼 클렌저', step: 'CLEANSER', time: 'BOTH', ingredients: '정제수, 글리세린, 소듐코코일글리시네이트, 코카미도프로필베타인, 부틸렌글라이콜, 시트릭애시드, 향료' },
  { name: 'BHA 각질 토너', step: 'TONER', time: 'BOTH', ingredients: '정제수, 부틸렌글라이콜, 살리실릭애씨드, 나이아신아마이드, 판테놀, 1,2-헥산다이올' },
  { name: '비타민C 세럼', step: 'SERUM', time: 'AM', ingredients: '정제수, 아스코빅애시드, 프로판다이올, 토코페롤, 페룰릭애시드, 소듐하이알루로네이트' },
  { name: '레티놀 나이트 크림', step: 'CREAM', time: 'PM', ingredients: '정제수, 카프릴릭/카프릭트라이글리세라이드, 글리세린, 레티놀, 세라마이드엔피, 스쿠알란, 리모넨' },
]

const load = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
    if (Array.isArray(saved?.products)) return saved
  } catch { /* 저장소를 못 쓰는 환경 */ }
  return { products: [], skinType: null }
}

const emptyDraft = () => ({ name: '', step: 'SERUM', time: 'BOTH', ingredients: '' })

function ShelfTab() {

  const [shelf, setShelf] = useState(load)
  const [draft, setDraft] = useState(emptyDraft)
  const [editing, setEditing] = useState(null)     // 고치는 제품 번호 (null = 새 제품)
  const [open, setOpen] = useState(false)          // 제품 추가 칸 열림
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(shelf)) } catch { /* 저장 못 해도 동작 */ }
  }, [shelf])

  const products = shelf.products

  const save = () => {
    if (!draft.name.trim() || !draft.ingredients.trim()) {
      setError('제품 이름과 전성분을 넣어주세요.')
      return
    }
    setError('')
    const next = editing == null ? [...products, draft].slice(0, MAX) : products.map((p, i) => (i === editing ? draft : p))
    setShelf({ ...shelf, products: next })
    setDraft(emptyDraft())
    setEditing(null)
    setOpen(false)
    setResult(null)
  }

  const edit = (i) => {
    setDraft(products[i])
    setEditing(i)
    setOpen(true)
  }

  const remove = (i) => {
    setShelf({ ...shelf, products: products.filter((_, k) => k !== i) })
    setResult(null)
  }

  const check = async () => {
    setLoading(true)
    setError('')
    try {
      const json = await postJson('/api/beauty/shelf/check', { products, skinType: shelf.skinType })
      if (!json.success) throw new Error(json.message)
      setResult(json)
    } catch (e) {
      setError(e.message === 'Failed to fetch' ? '서버에 연결할 수 없습니다.' : e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="bl-tab">
      <section className="bl-card">
        <p className="bl-intro">
          쓰고 있는 화장품을 화장대에 담아 두면 <strong>아침·저녁 바르는 순서</strong>와 <strong>같이 쓰면 자극이 생길 수 있는 조합</strong>을 알려줘요.
          제품은 이 브라우저에만 저장돼요.
        </p>

        <div className="bl-field">
          <span className="bl-label">내 피부 타입 (선택)</span>
          <div className="bl-chips">
            {SKIN_TYPES.map((t) => (
              <button key={t} type="button" className={`bl-chip ${shelf.skinType === t ? 'bl-chip-active' : ''}`} aria-pressed={shelf.skinType === t}
                onClick={() => setShelf({ ...shelf, skinType: shelf.skinType === t ? null : t })}>
                {t}
              </button>
            ))}
          </div>
        </div>

        <div className="fm-head">
          <span className="bl-label">내 화장대 ({products.length}개)</span>
          <div className="fm-head-actions">
            {products.length === 0 && <button type="button" className="bl-link" onClick={() => setShelf({ ...shelf, products: SAMPLE })}>예시 화장대 넣기</button>}
            {products.length > 0 && <button type="button" className="bl-link" onClick={() => { setShelf({ ...shelf, products: [] }); setResult(null) }}>비우기</button>}
          </div>
        </div>

        {products.length === 0 && <p className="bl-muted bl-small">아직 담은 제품이 없어요. 아래에서 제품을 추가해 보세요.</p>}

        <ul className="sh-list">
          {products.map((p, i) => (
            <li key={i} className="sh-item">
              <span className="sh-step">{(STEPS.find((s) => s[0] === p.step) || STEPS[2])[1].split(' ')[0]}</span>
              <div className="sh-body">
                <b>{p.name}</b>
                <span className="bl-muted bl-small">{(STEPS.find((s) => s[0] === p.step) || STEPS[2])[1].split(' ').slice(1).join(' ')} · {(TIMES.find((t) => t[0] === p.time) || TIMES[2])[1]}</span>
              </div>
              <button type="button" className="bl-link" onClick={() => edit(i)}>수정</button>
              <button type="button" className="fm-del" onClick={() => remove(i)} aria-label={`${p.name} 빼기`}>×</button>
            </li>
          ))}
        </ul>

        {open ? (
          <div className="sh-form">
            <input className="fm-input" value={draft.name} maxLength={60} placeholder="제품 이름 (예: 비타민C 세럼)" aria-label="제품 이름"
              onChange={(e) => setDraft({ ...draft, name: e.target.value, step: editing == null ? guessStep(e.target.value) : draft.step })} />
            <div className="bl-chips">
              {STEPS.map(([v, label]) => (
                <button key={v} type="button" className={`bl-chip ${draft.step === v ? 'bl-chip-active' : ''}`} onClick={() => setDraft({ ...draft, step: v })}>{label}</button>
              ))}
            </div>
            <div className="bl-chips">
              {TIMES.map(([v, label]) => (
                <button key={v} type="button" className={`bl-chip ${draft.time === v ? 'bl-chip-active' : ''}`} onClick={() => setDraft({ ...draft, time: v })}>{label}</button>
              ))}
            </div>
            <div className="bl-text">
              <textarea rows={4} value={draft.ingredients} maxLength={3000} aria-label="전성분"
                placeholder="전성분을 붙여넣어 주세요 (쉼표로 구분)&#10;예: 정제수, 글리세린, 나이아신아마이드, ..."
                onChange={(e) => setDraft({ ...draft, ingredients: e.target.value })} />
            </div>
            <div className="sh-form-actions">
              <button type="button" className="bl-ghost" onClick={() => { setOpen(false); setEditing(null); setDraft(emptyDraft()) }}>취소</button>
              <button type="button" className="bl-primary" onClick={save}>{editing == null ? '화장대에 담기' : '고치기'}</button>
            </div>
          </div>
        ) : (
          products.length < MAX && <button type="button" className="sh-add" onClick={() => setOpen(true)}>+ 제품 추가</button>
        )}

        {error && <p className="bl-error" role="alert">{error}</p>}

        <div className="bl-submit">
          <button type="button" className="bl-primary" onClick={check} disabled={loading || products.length === 0 || open}>
            {loading ? '확인 중...' : '궁합 확인하기'}
          </button>
          <span className="bl-remaining">AI 없이 성분 사전과 규칙으로 판단해요</span>
        </div>
      </section>

      {result && (
        <>
          <section className="bl-card">
            <h3 className="bl-card-title">{result.findings.some((f) => f.level === 'HIGH') ? '⚠️ 같이 쓸 때 조심할 조합이 있어요' : result.findings.length ? '👀 확인해 볼 점' : '✅ 큰 문제 없는 조합이에요'}</h3>
            {result.findings.length === 0 && <p className="bl-muted">아침·저녁 루틴 모두 자극이 겹치는 조합을 찾지 못했어요.</p>}
            <ul className="fm-issues">
              {result.findings.map((f, i) => (
                <li key={i} className="fm-issue">
                  <span className={`bl-badge ${LEVEL[f.level].cls}`}>{LEVEL[f.level].label}</span>
                  <div className="fm-issue-body">
                    <p><strong>{f.title}</strong></p>
                    <p>{f.detail}</p>
                    <p className="fm-basis">{f.products.join(' · ')}</p>
                  </div>
                </li>
              ))}
            </ul>
          </section>

          <div className="sh-routines">
            {[['AM', '☀️ 아침 루틴'], ['PM', '🌙 저녁 루틴']].map(([k, title]) => (
              <section key={k} className="bl-card">
                <h3 className="bl-card-title">{title}</h3>
                {result.routines[k].length === 0 ? <p className="bl-muted bl-small">이 시간에 쓰는 제품이 없어요.</p> : (
                  <ol className="sh-routine">
                    {result.routines[k].map((s, i) => (
                      <li key={i}><span className="sh-no">{i + 1}</span><b>{s.name}</b><span className="bl-muted bl-small">{s.stepLabel}</span></li>
                    ))}
                  </ol>
                )}
              </section>
            ))}
          </div>

          <section className="bl-card">
            <h3 className="bl-card-title">제품별 주요 성분</h3>
            <ul className="sh-products">
              {result.products.map((p, i) => (
                <li key={i}>
                  <b>{p.name}</b> <span className="bl-muted bl-small">성분 {p.count}개 중 사전에서 {p.matched}개 확인</span>
                  <div className="bl-chips sh-chips">
                    {p.actives.map((a) => <span key={a.code} className="bl-badge bl-badge-violet" title={a.ingredients.join(', ')}>{a.label}</span>)}
                    {p.allergens.length > 0 && <span className="bl-badge bl-badge-amber" title={p.allergens.join(', ')}>알레르기 착향제 {p.allergens.length}</span>}
                    {p.caution.length > 0 && <span className="bl-badge bl-badge-red" title={p.caution.join(', ')}>{result.skinType} 주의 {p.caution.length}</span>}
                    {p.actives.length === 0 && p.allergens.length === 0 && p.caution.length === 0 && <span className="bl-muted bl-small">눈에 띄는 활성 성분 없음</span>}
                  </div>
                </li>
              ))}
            </ul>
          </section>

          <p className="bl-disclaimer">일반적인 사용 안내예요. 피부 반응은 사람마다 달라서, 새 제품은 팔 안쪽에 먼저 발라 보고 따갑거나 붉어지면 사용을 멈춰 주세요.</p>
        </>
      )}
    </div>
  )
}

export default ShelfTab
