// ============================================
// 뷰티 랩 > 🔬 성분 분석
// - 📷 사진: 화면에서 줄인 뒤 업로드 → AI가 전성분을 "읽기만" → 성분 사전으로 판단
// - ⌨️ 글: 전성분을 붙여 넣으면 서버가 나눠서 사전으로 판단 (AI 호출 없음)
// ============================================
import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { SKIN_TYPES, categoryTone, postJson, resizeImage } from './beautyUtils'

const SAMPLE = '정제수, 부틸렌글라이콜, 글리세린, 나이아신아마이드, 1,2-헥산다이올, 병풀추출물, 판테놀, 소듐하이알루로네이트, 카보머, 트로메타민, 알란토인, 아데노신, 에틸헥실글리세린, 향료, 리모넨, 리날룰, 다이소듐이디티에이'

function AnalyzeTab({ loggedIn, remaining, result, onResult }) {

  const [mode, setMode] = useState('IMAGE')       // IMAGE | TEXT
  const [image, setImage] = useState(null)        // dataURL (줄인 사진)
  const [text, setText] = useState('')
  const [skinType, setSkinType] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [showAll, setShowAll] = useState(false)
  const fileRef = useRef(null)

  const pickFile = async (file) => {
    if (!file) return
    setError('')
    try {
      setImage(await resizeImage(file))
    } catch (e) {
      setError(e.message)
    }
  }

  const ready = mode === 'IMAGE' ? Boolean(image) : text.trim().length > 0

  const analyze = async () => {
    if (!ready || loading) return
    setLoading(true)
    setError('')
    try {
      const json = await postJson('/api/beauty/analyze', {
        image: mode === 'IMAGE' ? image : null,
        text: mode === 'TEXT' ? text : null,
        skinType,
      })
      if (json.success) {
        onResult(json)
        setShowAll(false)
      } else {
        setError(json.message)
      }
    } catch {
      setError('서버에 연결할 수 없습니다.')
    } finally {
      setLoading(false)
    }
  }

  const rows = result?.ingredients ?? []
  const visibleRows = showAll ? rows : rows.slice(0, 20)

  return (
    <div className="bl-tab">
      {/* ===== 입력 ===== */}
      <section className="bl-card">
        <div className="bl-mode" role="tablist" aria-label="입력 방식">
          <button type="button" role="tab" aria-selected={mode === 'IMAGE'} className={mode === 'IMAGE' ? 'bl-mode-active' : ''} onClick={() => setMode('IMAGE')}>📷 사진으로</button>
          <button type="button" role="tab" aria-selected={mode === 'TEXT'} className={mode === 'TEXT' ? 'bl-mode-active' : ''} onClick={() => setMode('TEXT')}>⌨️ 전성분 붙여넣기</button>
        </div>

        {mode === 'IMAGE' ? (
          <div
            className={`bl-drop ${image ? 'bl-drop-filled' : ''}`}
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => { e.preventDefault(); pickFile(e.dataTransfer.files?.[0]) }}
          >
            {image ? (
              <>
                <img src={image} alt="올린 성분표 사진" className="bl-preview" />
                <button type="button" className="bl-link" onClick={() => { setImage(null); if (fileRef.current) fileRef.current.value = '' }}>다른 사진</button>
              </>
            ) : (
              <button type="button" className="bl-drop-btn" onClick={() => fileRef.current?.click()}>
                <span className="bl-drop-icon">📷</span>
                <strong>제품 뒷면 "전성분" 사진 올리기</strong>
                <small>휴대폰에서는 바로 촬영할 수 있어요 · 성분표가 크고 선명하게</small>
              </button>
            )}
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              capture="environment"
              hidden
              onChange={(e) => pickFile(e.target.files?.[0])}
            />
          </div>
        ) : (
          <div className="bl-text">
            <textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="전성분을 그대로 붙여 넣어주세요. 예) 정제수, 글리세린, 나이아신아마이드, ..."
              maxLength={3000}
              rows={5}
            />
            <button type="button" className="bl-link" onClick={() => setText(SAMPLE)}>예시 넣기</button>
          </div>
        )}

        <div className="bl-field">
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
            <button type="button" className="bl-primary" onClick={analyze} disabled={!ready || loading}>
              {loading ? (mode === 'IMAGE' ? 'AI가 성분표를 읽는 중...' : '분석 중...') : '성분 분석하기'}
            </button>
            {remaining != null && <span className="bl-remaining">오늘 {remaining}번 남음</span>}
          </div>
        ) : (
          <p className="bl-login"><Link to="/login">로그인</Link>하면 분석할 수 있어요. 성분 사전은 로그인 없이 볼 수 있어요.</p>
        )}
      </section>

      {/* ===== 결과 ===== */}
      {result && (
        <>
          <section className="bl-card bl-summary">
            <div className="bl-summary-head">
              <div>
                <h2>{result.productName || '분석 결과'}</h2>
                <p className="bl-muted">
                  {result.productType && `${result.productType} · `}성분 {result.count}개 · 사전에서 찾음 {result.matchedCount}개
                  {result.skinType && ` · ${result.skinType} 피부 기준`}
                </p>
              </div>
              <div className="bl-badges">
                {Object.keys(result.functional ?? {}).map((t) => <span key={t} className="bl-badge bl-badge-violet">{t} 기능성 성분</span>)}
                {result.allergens?.length > 0 && <span className="bl-badge bl-badge-amber">알레르기 착향제 {result.allergens.length}</span>}
                {result.fragrance && <span className="bl-badge bl-badge-amber">향료</span>}
                {result.skinCautions?.length > 0 && <span className="bl-badge bl-badge-red">{result.skinType} 주의 {result.skinCautions.length}</span>}
              </div>
            </div>

            <ul className="bl-highlights">
              {result.highlights?.map((h) => <li key={h}>{h}</li>)}
            </ul>

            {result.skinCautions?.length > 0 && (
              <div className="bl-cautions">
                <strong>{result.skinType} 피부라면 확인해보세요</strong>
                <ul>
                  {result.skinCautions.map((c) => <li key={c.name}><b>{c.name}</b> {c.caution}</li>)}
                </ul>
              </div>
            )}

            {result.readNote && <p className="bl-muted bl-note">📷 읽기 메모: {result.readNote}</p>}

            <div className="bl-cats">
              {Object.entries(result.categories ?? {}).sort((a, b) => b[1] - a[1]).map(([c, n]) => (
                <span key={c} className={`bl-tag bl-tag-${categoryTone(c)}`}>{c} {n}</span>
              ))}
            </div>
          </section>

          <section className="bl-card">
            <h3 className="bl-card-title">전성분 ({rows.length})</h3>
            <ol className="bl-ings">
              {visibleRows.map((r) => (
                <li key={`${r.order}-${r.name}`} className={`bl-ing ${r.skinCaution ? 'bl-ing-warn' : ''}`}>
                  <span className="bl-ing-no">{r.order}</span>
                  <div className="bl-ing-body">
                    <div className="bl-ing-name">
                      <strong>{r.name}</strong>
                      {r.matched && r.nameKo !== r.name && <small>→ {r.nameKo}</small>}
                      {r.matched ? (
                        <span className={`bl-tag bl-tag-${categoryTone(r.category)}`}>{r.category}</span>
                      ) : (
                        <span className="bl-tag bl-tag-gray">사전에 없음{r.aiRole ? ` · AI 추정: ${r.aiRole}` : ''}</span>
                      )}
                      {r.functional && <span className="bl-tag bl-tag-violet">{r.functional} 기능성 고시</span>}
                      {r.allergen && <span className="bl-tag bl-tag-amber">알레르기 착향제</span>}
                    </div>
                    {r.description && <p>{r.description}</p>}
                    {r.caution && <p className="bl-ing-caution">⚠️ {r.caution}</p>}
                  </div>
                </li>
              ))}
            </ol>
            {rows.length > 20 && (
              <button type="button" className="bl-more" onClick={() => setShowAll((v) => !v)}>
                {showAll ? '접기' : `나머지 ${rows.length - 20}개 더 보기`}
              </button>
            )}
            <p className="bl-disclaimer">
              성분 설명은 SKLabs가 정리한 일반 정보예요. 같은 성분도 함량·제형·사람에 따라 반응이 달라요. 피부 이상이 있으면 사용을 멈추고 전문가와 상담하세요.
            </p>
          </section>
        </>
      )}
    </div>
  )
}

export default AnalyzeTab
