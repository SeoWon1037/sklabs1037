// ============================================
// 뷰티 랩 > ⚖️ 성분 비교
// - 두 제품의 전성분(사진 또는 글)을 사전 기준으로 맞춰서 비교
//   공통 / A에만 / B에만, 겹치는 정도(%), 기능성·향료·알레르기 착향제·내 피부 주의 성분 수
// - 백엔드 API: POST /api/beauty/compare
// ============================================
import { useState } from 'react'
import { Link } from 'react-router-dom'
import ProductInput from './ProductInput'
import { SKIN_TYPES, postJson } from './beautyUtils'

const EMPTY = { mode: 'TEXT', image: null, text: '', name: '' }

const SAMPLE_A = { mode: 'TEXT', image: null, name: '시카 크림', text: '정제수, 글리세린, 부틸렌글라이콜, 나이아신아마이드, 병풀추출물, 마데카소사이드, 판테놀, 향료, 리모넨, 페녹시에탄올' }
const SAMPLE_B = { mode: 'TEXT', image: null, name: '세라마이드 크림', text: '정제수, 글리세린, 부틸렌글라이콜, 세라마이드엔피, 콜레스테롤, 스쿠알란, 판테놀, 병풀추출물, 1,2-헥산다이올, 에틸헥실글리세린' }

// 두 제품 숫자 비교 줄
const metricRows = (a, b) => [
  ['성분 수', a.count, b.count],
  ['기능성 고시 성분', Object.keys(a.functional ?? {}).join(', ') || '없음', Object.keys(b.functional ?? {}).join(', ') || '없음'],
  ['향료', a.fragrance ? '있음' : '없음', b.fragrance ? '있음' : '없음'],
  ['알레르기 착향제', `${a.allergens?.length ?? 0}개`, `${b.allergens?.length ?? 0}개`],
]

function CompareTab({ loggedIn, remaining, result, onResult }) {

  const [a, setA] = useState(EMPTY)
  const [b, setB] = useState(EMPTY)
  const [skinType, setSkinType] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const filled = (p) => (p.mode === 'IMAGE' ? Boolean(p.image) : p.text.trim().length > 0)
  const payload = (p) => ({ name: p.name, image: p.mode === 'IMAGE' ? p.image : null, text: p.mode === 'TEXT' ? p.text : null })

  const submit = async () => {
    if (loading || !filled(a) || !filled(b)) return
    setLoading(true)
    setError('')
    try {
      const json = await postJson('/api/beauty/compare', { a: payload(a), b: payload(b), skinType })
      if (json.success) onResult(json)
      else setError(json.message)
    } catch {
      setError('서버에 연결할 수 없습니다.')
    } finally {
      setLoading(false)
    }
  }

  const r = result

  return (
    <div className="bl-tab">
      <div className="bl-two">
        <ProductInput label="A" value={a} onChange={setA} />
        <ProductInput label="B" value={b} onChange={setB} />
      </div>

      <section className="bl-card">
        <div className="bl-field bl-field-top">
          <span className="bl-label">내 피부 타입 (선택)</span>
          <div className="bl-chips">
            <button type="button" className={`bl-chip ${skinType === null ? 'bl-chip-active' : ''}`} onClick={() => setSkinType(null)}>선택 안 함</button>
            {SKIN_TYPES.map((s) => (
              <button key={s} type="button" className={`bl-chip ${skinType === s ? 'bl-chip-active' : ''}`} onClick={() => setSkinType(s)}>{s}</button>
            ))}
          </div>
        </div>
        {error && <p className="bl-error" role="alert">{error}</p>}
        {loggedIn ? (
          <div className="bl-submit">
            <button type="button" className="bl-primary" onClick={submit} disabled={loading || !filled(a) || !filled(b)}>
              {loading ? '비교하는 중...' : '두 제품 비교하기'}
            </button>
            {remaining != null && <span className="bl-remaining">오늘 {remaining}번 남음</span>}
            <button type="button" className="bl-link" onClick={() => { setA(SAMPLE_A); setB(SAMPLE_B) }}>예시 넣기</button>
          </div>
        ) : (
          <p className="bl-login"><Link to="/login">로그인</Link>하면 비교할 수 있어요.</p>
        )}
      </section>

      {r && (
        <>
          <section className="bl-card">
            <div className="bl-summary-head">
              <div>
                <span className="bl-eyebrow">성분 비교</span>
                <h2>{r.a.productName} vs {r.b.productName}</h2>
              </div>
              <div className="bl-similarity" aria-label={`성분 ${r.similarity}% 겹침`}>
                <strong>{r.similarity}%</strong>
                <small>겹침</small>
              </div>
            </div>
            <ul className="bl-highlights">
              {r.verdicts?.map((v) => <li key={v}>{v}</li>)}
            </ul>

            <div className="bl-compare-table" role="table">
              <div className="bl-compare-row bl-compare-head" role="row">
                <span role="columnheader" />
                <strong role="columnheader">{r.a.productName}</strong>
                <strong role="columnheader">{r.b.productName}</strong>
              </div>
              {metricRows(r.a, r.b).map(([k, va, vb]) => (
                <div key={k} className="bl-compare-row" role="row">
                  <span role="rowheader">{k}</span>
                  <b role="cell">{va}</b>
                  <b role="cell">{vb}</b>
                </div>
              ))}
              {r.skinType && (
                <div className="bl-compare-row" role="row">
                  <span role="rowheader">{r.skinType} 주의</span>
                  <b role="cell" className={r.a.skinCautions.length < r.b.skinCautions.length ? 'bl-better' : ''}>{r.a.skinCautions.length}개</b>
                  <b role="cell" className={r.b.skinCautions.length < r.a.skinCautions.length ? 'bl-better' : ''}>{r.b.skinCautions.length}개</b>
                </div>
              )}
            </div>
          </section>

          <div className="bl-three">
            <section className="bl-card">
              <h3 className="bl-card-title">A에만 ({r.onlyA.length})</h3>
              <p className="bl-names">{r.onlyA.join(', ') || '없음'}</p>
            </section>
            <section className="bl-card bl-common">
              <h3 className="bl-card-title">공통 ({r.common.length})</h3>
              <p className="bl-names">{r.common.join(', ') || '없음'}</p>
            </section>
            <section className="bl-card">
              <h3 className="bl-card-title">B에만 ({r.onlyB.length})</h3>
              <p className="bl-names">{r.onlyB.join(', ') || '없음'}</p>
            </section>
          </div>
          <p className="bl-disclaimer">
            겹침(%)은 성분 이름 기준이에요. 같은 성분도 함량은 표시되지 않아 실제 효과·자극 차이와 다를 수 있어요.
          </p>
        </>
      )}
    </div>
  )
}

export default CompareTab
