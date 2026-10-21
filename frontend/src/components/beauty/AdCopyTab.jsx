// ============================================
// 📣 광고 문구 검수 (뷰티 랩 탭)
// - 상세페이지·SNS 광고 문구를 붙여넣으면 규칙으로 바로 검사 (AI 없음, 로그인 없이 누구나)
//   의약품 오인 · 기능성 오인 · 의사·기관 추천 · 절대 표현 · 실증 필요 · 천연·유기농
// - 걸린 곳을 문구 안에 색으로 표시 + 이유 · 근거 · 바꿔 쓸 방향
// - ✨ AI로 고쳐 쓰기 (로그인, 하루 20번): AI가 다시 쓰고 → 서버가 같은 규칙으로 다시 검사한 결과를 같이 보여줌
// - 백엔드: POST /api/beauty/adcopy/check, /api/beauty/adcopy/rewrite
// ============================================
import { useState } from 'react'
import { postJson } from './beautyUtils'

const CLAIMS = ['미백', '주름', '자외선', '여드름', '탈모', '아토피']

const LEVELS = {
  ERROR: { label: '고칠 것', cls: 'bl-badge-red', mark: 'ad-mark-error' },
  WARN: { label: '근거 확인', cls: 'bl-badge-amber', mark: 'ad-mark-warn' },
  INFO: { label: '참고', cls: 'bl-tag-blue', mark: 'ad-mark-info' },
}

const SAMPLE = '피부과 의사가 추천한 최고의 진정 크림 ✨\n여드름 치료부터 피부 재생까지 단 7일 만에! 미백·주름 개선 효과 100%\n천연 유래 성분으로 부작용 없이, 판매 1위의 비결을 지금 경험하세요.'

function AdCopyTab({ loggedIn, remaining, result, onResult }) {

  const [text, setText] = useState(result?.text ?? '')
  const [claims, setClaims] = useState(result?.claims ?? [])
  const [loading, setLoading] = useState('')
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)

  const changed = result && (result.text !== text.trim() || result.claims.join() !== claims.join())

  const check = async () => {
    setLoading('check')
    setError('')
    const json = await postJson('/api/beauty/adcopy/check', { text, claims }).catch(() => ({ success: false, message: '서버에 연결하지 못했어요.' }))
    setLoading('')
    if (!json.success) return setError(json.message)
    onResult(json, false)
  }

  const rewrite = async () => {
    setLoading('rewrite')
    setError('')
    const json = await postJson('/api/beauty/adcopy/rewrite', { text: result.text, claims: result.claims }).catch(() => ({ success: false, message: '서버에 연결하지 못했어요.' }))
    setLoading('')
    if (!json.success) return setError(json.message)
    onResult(json, true)
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(result.rewrite.text)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      setCopied(false)
    }
  }

  const toggle = (c) => setClaims((prev) => (prev.includes(c) ? prev.filter((x) => x !== c) : CLAIMS.filter((x) => x === c || prev.includes(x))))

  return (
    <div className="bl-tab">
      <section className="bl-card">
        <p className="bl-intro">
          상세페이지·SNS 광고 문구를 붙여넣으면 <strong>화장품법 표시·광고 기준</strong>으로 바로 검사해요.
          의약품으로 오인되는 표현, 기능성 오인, 의사 추천·최고 같은 금지 표현, 근거가 필요한 표현을 찾아 바꿀 방향을 알려줘요.
        </p>

        <div className="bl-field">
          <span className="bl-label">심사·보고 받은 기능성 (있을 때만)</span>
          <div className="bl-chips">
            {CLAIMS.map((c) => {
              const on = claims.includes(c)
              return (
                <button key={c} type="button" className={`bl-chip ${on ? 'bl-chip-active' : ''}`} aria-pressed={on} onClick={() => toggle(c)}>
                  {c}
                </button>
              )
            })}
          </div>
        </div>

        <div className="bl-field">
          <div className="fm-head">
            <span className="bl-label">광고 문구 ({text.trim().length}/2000자)</span>
            <div className="fm-head-actions">
              <button type="button" className="bl-link" onClick={() => { setText(SAMPLE); setClaims([]) }}>예시 넣기</button>
              <button type="button" className="bl-link" onClick={() => setText('')}>비우기</button>
            </div>
          </div>
          <div className="bl-text">
            <textarea
              rows={6}
              value={text}
              maxLength={2000}
              onChange={(e) => setText(e.target.value)}
              placeholder={'예) 피부 속까지 촉촉하게 채워주는 수분 크림\n바르는 순간 산뜻하게 스며들어요'}
              aria-label="광고 문구"
            />
          </div>
        </div>

        {error && <p className="bl-error" role="alert">{error}</p>}

        <div className="bl-submit">
          <button type="button" className="bl-primary" onClick={check} disabled={!!loading || text.trim().length < 5}>
            {loading === 'check' ? '검사 중...' : '문구 검사하기'}
          </button>
          <span className="bl-remaining">AI 없이 규칙으로 바로 검사해요</span>
        </div>
      </section>

      {result && (
        <AdCopyResult
          r={result}
          stale={changed}
          loggedIn={loggedIn}
          remaining={remaining}
          loading={loading}
          onRewrite={rewrite}
          copied={copied}
          onCopy={copy}
        />
      )}
    </div>
  )
}

// 문구 안에 걸린 곳 표시 (findings는 서버가 겹치지 않게 정리해서 줌)
function Highlighted({ text, findings }) {

  const parts = []
  let at = 0
  findings.forEach((f, i) => {
    if (f.start > at) parts.push(<span key={`t${i}`}>{text.slice(at, f.start)}</span>)
    parts.push(
      <mark key={`m${i}`} className={`ad-mark ${LEVELS[f.level].mark}`} title={`${LEVELS[f.level].label} · ${f.category}`}>
        {text.slice(f.start, f.end)}<sup>{i + 1}</sup>
      </mark>,
    )
    at = f.end
  })
  if (at < text.length) parts.push(<span key="end">{text.slice(at)}</span>)

  return <p className="ad-text">{parts}</p>
}

function Verdict({ r, small }) {

  const v = {
    PASS: { icon: '✅', text: r.findings.length ? '걸리는 표현이 없어요 (참고만 있어요)' : '걸리는 표현이 없어요', cls: 'fm-verdict-ok' },
    CHECK: { icon: '⚠️', text: `근거 확인 ${r.warnCount}개`, cls: 'fm-verdict-warn' },
    FAIL: { icon: '❌', text: `고칠 것 ${r.errorCount}개${r.warnCount ? ` · 근거 확인 ${r.warnCount}개` : ''}`, cls: 'fm-verdict-bad' },
  }[r.verdict]

  if (small) return <span className={`ad-recheck ${v.cls}`}>{v.icon} 서버 재검사: {v.text}</span>

  return (
    <section className={`bl-card fm-verdict ${v.cls}`} aria-live="polite">
      <span className="fm-verdict-icon" aria-hidden="true">{v.icon}</span>
      <div>
        <strong>{v.text}</strong>
        <p className="bl-small bl-muted">{r.claims.length ? `기능성 ${r.claims.join('·')} 심사·보고 받은 제품 기준` : '기능성 화장품이 아닌 제품 기준'}</p>
      </div>
    </section>
  )
}

function AdCopyResult({ r, stale, loggedIn, remaining, loading, onRewrite, copied, onCopy }) {

  const fixable = r.findings.some((f) => f.level !== 'INFO')

  return (
    <>
      {stale && <p className="bl-muted bl-small ad-stale">문구나 기능성을 바꿨어요. 다시 검사하면 결과가 새로 나와요.</p>}

      <Verdict r={r} />

      {r.findings.length > 0 && (
        <section className="bl-card">
          <h3 className="bl-card-title">걸린 곳</h3>
          <Highlighted text={r.text} findings={r.findings} />
          <ol className="fm-issues ad-issues">
            {r.findings.map((f, i) => (
              <li key={i} className="fm-issue">
                <span className="ad-num">{i + 1}</span>
                <div className="fm-issue-body">
                  <p>
                    <span className={`bl-badge ${LEVELS[f.level].cls}`}>{LEVELS[f.level].label}</span>{' '}
                    <strong>“{f.phrase}”</strong> <span className="bl-muted">{f.category}</span>
                  </p>
                  <p>{f.reason}</p>
                  <p className="ad-suggest">→ {f.suggest}</p>
                  <p className="fm-basis">근거: {f.basis}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>
      )}

      {fixable && !r.rewrite && (
        <section className="bl-card ad-ai">
          <div>
            <h3 className="bl-card-title">✨ AI로 고쳐 쓰기</h3>
            <p className="bl-small bl-muted">걸린 표현만 바꾸고 광고 느낌은 살려요. 고친 문구는 서버가 같은 규칙으로 다시 검사해요.</p>
          </div>
          {loggedIn ? (
            <div className="bl-submit">
              <button type="button" className="bl-primary" onClick={onRewrite} disabled={!!loading || stale || remaining === 0}>
                {loading === 'rewrite' ? '고치는 중...' : '고쳐 쓰기'}
              </button>
              {remaining != null && <span className="bl-remaining">오늘 {remaining}번 남음</span>}
            </div>
          ) : (
            <p className="bl-muted bl-small">로그인하면 쓸 수 있어요.</p>
          )}
        </section>
      )}

      {r.rewrite && (
        <section className="bl-card">
          <div className="fm-head">
            <h3 className="bl-card-title">✨ 고친 문구</h3>
            <button type="button" className="bl-link" onClick={onCopy}>{copied ? '복사했어요' : '복사'}</button>
          </div>
          <Verdict r={{ ...r.rewrite, claims: r.claims }} small />
          {r.rewrite.findings.length > 0
            ? <Highlighted text={r.rewrite.text} findings={r.rewrite.findings} />
            : <p className="ad-text">{r.rewrite.text}</p>}
          {r.rewrite.changes.length > 0 && (
            <ul className="ad-changes">
              {r.rewrite.changes.map((c, i) => (
                <li key={i}>
                  <del>{c.before}</del> → <ins>{c.after}</ins>
                  {c.why && <span className="bl-muted"> · {c.why}</span>}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      <p className="bl-disclaimer">
        {r.ruleVersion}. 자주 문제 되는 표현을 정리한 1차 검수예요. 실제 광고 전에는 식약처 「화장품 표시·광고 관리 가이드라인」 원문과 담당 부서 확인을 거치세요.
      </p>
    </>
  )
}

export default AdCopyTab
