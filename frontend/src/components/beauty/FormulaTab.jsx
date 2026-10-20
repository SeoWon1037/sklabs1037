// ============================================
// 🧪 처방 검토 (뷰티 랩 탭)
// - 원료 + 함량(%)을 넣으면 규정으로 바로 검사 (AI 없음, 로그인 없이 누구나)
//   합계 100% · 사용 한도(보존제·자외선 차단) · 기능성 고시 함량 · 알레르기 착향제 표시 · 전성분 표시 순서
// - 정제수는 보통 "적량"(나머지 채우기)
// - 표로 하나씩 넣거나, 엑셀에서 복사한 "원료명 함량" 줄을 붙여넣기
// - 작성 중인 처방은 이 브라우저에만 임시 저장 (새로고침해도 유지)
// - 백엔드: POST /api/beauty/formula/check
// ============================================
import { useEffect, useMemo, useState } from 'react'

const STORAGE_KEY = 'sklabs.formula.v1'
const MAX_ROWS = 80

const CLAIMS = ['미백', '주름', '여드름']

const LEVELS = {
  ERROR: { label: '고칠 것', cls: 'bl-badge-red' },
  WARN: { label: '확인', cls: 'bl-badge-amber' },
  INFO: { label: '참고', cls: 'bl-tag-blue' },
}

// 예시: 미백·주름 2중 기능성 크림 (파라벤 합산 한도를 일부러 넘김)
const SAMPLE = {
  rinseOff: false,
  claims: ['미백', '주름'],
  rows: [
    { name: '정제수', pct: '', qs: true },
    { name: '글리세린', pct: '6' },
    { name: '부틸렌글라이콜', pct: '5' },
    { name: '나이아신아마이드', pct: '4' },
    { name: '카프릴릭/카프릭트라이글리세라이드', pct: '3' },
    { name: '세틸알코올', pct: '1.5' },
    { name: '1,2-헥산다이올', pct: '1' },
    { name: '아데노신', pct: '0.04' },
    { name: '메칠파라벤', pct: '0.4' },
    { name: '프로필파라벤', pct: '0.4' },
    { name: '부틸파라벤', pct: '0.3' },
    { name: '리날룰', pct: '0.005' },
  ],
}

const emptyRow = () => ({ name: '', pct: '', qs: false })

// 빈 처방 (정제수 적량 + 빈 줄 2개)
const loadDraftEmpty = () => ({ rinseOff: false, claims: [], kids: false, rows: [{ name: '정제수', pct: '', qs: true }, emptyRow(), emptyRow()] })

// 이 브라우저에 저장된 작성 중 처방 (없거나 못 읽으면 빈 처방)
const loadDraft = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
    if (saved?.rows?.length) return saved
  } catch { /* 저장소를 못 쓰는 환경 */ }
  return loadDraftEmpty()
}

// 붙여넣기 한 줄: "나이아신아마이드 4", "나이아신아마이드\t4%", "정제수 적량", "Water QS"
const parseLine = (line) => {
  const text = line.trim()
  if (!text) return null
  const qs = text.match(/^(.+?)[\s,]+(적량|qs|q\.s\.|to\s*100|ad\s*100)\s*$/i)
  if (qs) return { name: qs[1].trim(), pct: '', qs: true }
  const m = text.match(/^(.+?)[\s,]+([\d.,]+)\s*%?\s*$/)
  if (m) return { name: m[1].trim(), pct: m[2].replace(/,/g, ''), qs: false }
  return { name: text, pct: '', qs: false }
}

const fmt = (n) => (n == null ? '-' : Number(n).toLocaleString('ko-KR', { maximumFractionDigits: 4 }))

function FormulaTab() {

  const [draft, setDraft] = useState(loadDraft)
  const [names, setNames] = useState([])          // 자동완성용 사전 성분명
  const [paste, setPaste] = useState(null)        // null = 닫힘, 문자열 = 붙여넣기 칸
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)

  const { rows, rinseOff, claims, kids } = draft

  // 작성 중 처방 임시 저장
  useEffect(() => {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(draft)) } catch { /* 저장 못 해도 동작 */ }
  }, [draft])

  // 자동완성 목록 (성분 사전)
  useEffect(() => {
    let ignore = false
    fetch('/api/beauty/ingredients')
      .then((res) => res.json())
      .then((data) => { if (!ignore && data.success) setNames(data.ingredients.map((i) => i.nameKo)) })
      .catch(() => {})
    return () => { ignore = true }
  }, [])

  const update = (changes) => setDraft((d) => ({ ...d, ...changes }))

  const setRow = (i, changes) =>
    update({
      rows: rows.map((r, k) => {
        if (k === i) return { ...r, ...changes }
        // 적량은 한 줄만: 다른 줄을 적량으로 바꾸면 기존 적량은 해제
        return changes.qs ? { ...r, qs: false } : r
      }),
    })

  const addRow = () => rows.length < MAX_ROWS && update({ rows: [...rows, emptyRow()] })
  const removeRow = (i) => update({ rows: rows.length > 1 ? rows.filter((_, k) => k !== i) : [emptyRow()] })

  // 화면에서 바로 보는 합계 (서버 검토 전)
  const sum = useMemo(() => {
    const fixed = rows.reduce((acc, r) => acc + (r.qs || !r.name.trim() ? 0 : Number(r.pct) || 0), 0)
    const hasQs = rows.some((r) => r.qs && r.name.trim())
    return { fixed, qs: hasQs ? 100 - fixed : null }
  }, [rows])

  const applyPaste = () => {
    const parsed = (paste || '').split(/\r?\n/).map(parseLine).filter(Boolean).slice(0, MAX_ROWS)
    if (parsed.length === 0) return
    // 적량이 여러 줄이면 첫 줄만
    let qsSeen = false
    const fixed = parsed.map((r) => {
      if (!r.qs) return r
      if (qsSeen) return { ...r, qs: false }
      qsSeen = true
      return r
    })
    update({ rows: fixed })
    setPaste(null)
    setResult(null)
  }

  const submit = async () => {
    setLoading(true)
    setError('')
    setCopied(false)
    try {
      const res = await fetch('/api/beauty/formula/check', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          rinseOff,
          kids,
          claims,
          items: rows.filter((r) => r.name.trim()).map((r) => ({ name: r.name, pct: r.qs ? null : r.pct, qs: r.qs })),
        }),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.message || '검토하지 못했어요.')
      setResult(data)
    } catch (e) {
      setError(e.message === 'Failed to fetch' ? '서버에 연결할 수 없습니다.' : e.message)
    } finally {
      setLoading(false)
    }
  }

  const copyLabel = async () => {
    try {
      await navigator.clipboard.writeText(result.label)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  const filled = rows.some((r) => r.name.trim())

  return (
    <div className="bl-tab">

      <section className="bl-card">
        <p className="bl-intro">
          원료와 함량(%)을 넣으면 <strong>식약처 규정</strong>으로 바로 검사해요.
          합계 100%, 사용 한도(보존제·자외선 차단 성분), 기능성 고시 함량, 알레르기 착향제 표시, 전성분 표시 순서를 확인해요.
        </p>

        {/* ===== 조건 ===== */}
        <div className="bl-field">
          <span className="bl-label">제품 유형</span>
          <div className="bl-chips">
            {[[false, '바르고 두는 제품 (크림·세럼)'], [true, '씻어내는 제품 (클렌저·샴푸)']].map(([v, label]) => (
              <button key={label} type="button" className={`bl-chip ${rinseOff === v ? 'bl-chip-active' : ''}`} aria-pressed={rinseOff === v} onClick={() => update({ rinseOff: v })}>
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="bl-field">
          <span className="bl-label">내세울 기능성 (선택)</span>
          <div className="bl-chips">
            {CLAIMS.map((c) => {
              const on = claims.includes(c)
              return (
                <button key={c} type="button" className={`bl-chip ${on ? 'bl-chip-active' : ''}`} aria-pressed={on}
                  onClick={() => update({ claims: on ? claims.filter((x) => x !== c) : [...claims, c] })}>
                  {c}
                </button>
              )
            })}
            <label className="fm-check">
              <input type="checkbox" checked={!!kids} onChange={(e) => update({ kids: e.target.checked })} />
              영유아·어린이용
            </label>
          </div>
        </div>

        {/* ===== 처방 ===== */}
        <div className="fm-head">
          <span className="bl-label">처방 ({rows.filter((r) => r.name.trim()).length}개 원료)</span>
          <div className="fm-head-actions">
            <button type="button" className="bl-link" onClick={() => setPaste(paste == null ? '' : null)}>
              {paste == null ? '📋 붙여넣기' : '표로 입력'}
            </button>
            <button type="button" className="bl-link" onClick={() => { setDraft({ ...SAMPLE, kids: false }); setResult(null) }}>예시 넣기</button>
            <button type="button" className="bl-link" onClick={() => { setDraft(loadDraftEmpty()); setResult(null) }}>비우기</button>
          </div>
        </div>

        {paste != null ? (
          <div className="bl-text">
            <textarea
              rows={8}
              value={paste}
              onChange={(e) => setPaste(e.target.value)}
              placeholder={'한 줄에 원료 하나 (엑셀에서 두 칸 복사해도 돼요)\n정제수 적량\n글리세린 5\n나이아신아마이드 4%'}
              aria-label="처방 붙여넣기"
            />
            <button type="button" className="bl-ghost" onClick={applyPaste} disabled={!paste.trim()}>표로 옮기기</button>
          </div>
        ) : (
          <>
            <datalist id="fm-names">
              {names.map((n) => <option key={n} value={n} />)}
            </datalist>

            <div className="fm-table" role="table" aria-label="처방">
              <div className="fm-row fm-row-head" role="row">
                <span role="columnheader">원료</span>
                <span role="columnheader">함량 %</span>
                <span role="columnheader">적량</span>
                <span role="columnheader"><span className="fm-sr">삭제</span></span>
              </div>
              {rows.map((r, i) => (
                <div key={i} className="fm-row" role="row">
                  <input
                    className="fm-input"
                    value={r.name}
                    list="fm-names"
                    maxLength={100}
                    placeholder="원료명"
                    aria-label={`${i + 1}번 원료명`}
                    onChange={(e) => setRow(i, { name: e.target.value })}
                  />
                  <input
                    className="fm-input fm-pct"
                    value={r.qs ? '' : r.pct}
                    inputMode="decimal"
                    placeholder={r.qs ? fmt(sum.qs) : '0'}
                    disabled={r.qs}
                    aria-label={`${i + 1}번 함량`}
                    onChange={(e) => setRow(i, { pct: e.target.value.replace(/[^\d.]/g, '') })}
                  />
                  <label className="fm-qs" title="나머지를 채우는 원료 (보통 정제수)">
                    <input type="checkbox" checked={!!r.qs} onChange={(e) => setRow(i, { qs: e.target.checked })} aria-label={`${i + 1}번 적량`} />
                  </label>
                  <button type="button" className="fm-del" onClick={() => removeRow(i)} aria-label={`${i + 1}번 줄 삭제`}>×</button>
                </div>
              ))}
            </div>

            <div className="fm-foot">
              <button type="button" className="bl-link" onClick={addRow} disabled={rows.length >= MAX_ROWS}>+ 원료 추가</button>
              <span className={`fm-sum ${sum.qs != null ? (sum.qs < 0 ? 'fm-sum-bad' : '') : Math.abs(sum.fixed - 100) > 0.0005 ? 'fm-sum-bad' : 'fm-sum-ok'}`}>
                {sum.qs != null ? `적량 ${fmt(sum.qs)}% · 합계 ${sum.qs < 0 ? fmt(sum.fixed) : 100}%` : `합계 ${fmt(sum.fixed)}%`}
              </span>
            </div>
          </>
        )}

        {error && <p className="bl-error" role="alert">{error}</p>}

        <div className="bl-submit">
          <button type="button" className="bl-primary" onClick={submit} disabled={loading || !filled || paste != null}>
            {loading ? '검토 중...' : '처방 검토하기'}
          </button>
          <span className="bl-remaining">AI 없이 규정으로만 판단해요</span>
        </div>
      </section>

      {result && <FormulaResult r={result} copied={copied} onCopy={copyLabel} />}
    </div>
  )
}

// ============================================
// 결과
// ============================================
function FormulaResult({ r, copied, onCopy }) {

  const verdict = {
    PASS: { icon: '✅', text: '규정상 문제를 찾지 못했어요', cls: 'fm-verdict-ok' },
    CHECK: { icon: '⚠️', text: `확인할 것 ${r.warnCount}개`, cls: 'fm-verdict-warn' },
    FAIL: { icon: '❌', text: `고칠 것 ${r.errorCount}개${r.warnCount ? ` · 확인할 것 ${r.warnCount}개` : ''}`, cls: 'fm-verdict-bad' },
  }[r.verdict]

  return (
    <>
      <section className={`bl-card fm-verdict ${verdict.cls}`} aria-live="polite">
        <span className="fm-verdict-icon" aria-hidden="true">{verdict.icon}</span>
        <div>
          <strong>{verdict.text}</strong>
          <p className="bl-small bl-muted">
            {r.rinseOff ? '씻어내는 제품' : '바르고 두는 제품'} 기준 · 합계 {fmt(r.total)}%{r.qsPct != null ? ` (적량 ${fmt(r.qsPct)}%)` : ''}
          </p>
        </div>
      </section>

      {r.issues.length > 0 && (
        <section className="bl-card">
          <h3 className="bl-card-title">검토 결과</h3>
          <ul className="fm-issues">
            {r.issues.map((it, i) => (
              <li key={i} className="fm-issue">
                <span className={`bl-badge ${LEVELS[it.level].cls}`}>{LEVELS[it.level].label}</span>
                <div className="fm-issue-body">
                  <p><strong>{it.target}</strong> {it.message}</p>
                  {it.basis && <p className="fm-basis">규정: {it.basis}</p>}
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {r.functional.length > 0 && (
        <section className="bl-card">
          <h3 className="bl-card-title">기능성 고시 함량</h3>
          {r.functional.map((f) => (
            <div key={f.claim} className="fm-claim">
              <p className="fm-claim-head">{f.ok ? '✅' : '⚠️'} <strong>{f.claim}</strong> {f.message}</p>
              {f.items.length > 0 && (
                <ul className="fm-claim-items">
                  {f.items.map((x) => (
                    <li key={x.name}>
                      {x.name} {fmt(x.pct)}% <span className="bl-muted">(고시 {x.basis})</span> → {x.verdict}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))}
          <p className="fm-basis">「기능성화장품 심사에 관한 규정」 별표4 함량이면 유효성 자료 제출이 생략돼요. 다른 함량은 심사 자료가 필요해요.</p>
        </section>
      )}

      <section className="bl-card">
        <div className="fm-head">
          <h3 className="bl-card-title">전성분 표시안</h3>
          <button type="button" className="bl-link" onClick={onCopy}>{copied ? '복사했어요' : '복사'}</button>
        </div>
        <p className="fm-label">{r.label}</p>
        <p className="fm-basis">1% 넘는 원료는 함량 많은 순, 1% 이하는 입력 순서 그대로예요 (1% 이하는 순서를 바꿔도 돼요).</p>
      </section>

      <p className="bl-disclaimer">
        {r.ruleVersion}. 원료 순도·역가·제조 손실, 이 검토기에 없는 원료의 한도는 반영하지 않아요. 실제 허가·표시 전에는 원문 규정으로 다시 확인하세요.
      </p>
    </>
  )
}

export default FormulaTab
