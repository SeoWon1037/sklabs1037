// ============================================
// 뷰티 랩 > 📝 기획 브리프
// - 브랜드 담당자가 자유롭게 쓴 요청 → AI가 정해진 개발 브리프 양식으로 정리 + 되물을 질문
// - 서버가 덧붙임: 규제 확인 포인트, 성분 사전 기반 추천 성분, 꼭 넣을 성분 확인, 뺄 성분 펼치기
// ============================================
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { postJson } from './beautyUtils'

const SAMPLE = '20대 후반 여성 타깃으로 민감한 피부도 쓸 수 있는 수분 진정 크림 만들고 싶어요. 끈적이지 않고 가볍게 흡수됐으면 좋겠고, 병풀이랑 나이아신아마이드는 꼭 넣어주세요. 향료랑 파라벤은 빼고 비건 인증도 받고 싶어요. 올리브영 입점 목표예요.'

// 브리프 → 복사용 글
const toText = (b) => {
  const line = (k, v) => (v && (Array.isArray(v) ? v.length : String(v).trim()) ? `- ${k}: ${Array.isArray(v) ? v.join(', ') : v}\n` : '')
  return `[개발 브리프] ${b.productName || ''}\n\n`
    + line('카테고리', b.category) + line('타깃', b.target) + line('피부 타입', b.skinTypes) + line('제형·사용감', b.texture)
    + line('핵심 효능', b.keyBenefits) + line('꼭 넣을 성분', b.mustHave) + line('뺄 성분', b.avoid) + line('향', b.fragrance)
    + line('용량', b.volume) + line('목표 단가', b.targetPrice) + line('용기', b.packaging) + line('클레임', b.claims)
    + line('채널', b.channel) + line('일정', b.timeline)
    + (b.summary ? `\n요약: ${b.summary}\n` : '')
    + (b.regulatory?.length ? `\n[규제·표시 확인]\n${b.regulatory.map((r) => `- ${r}`).join('\n')}\n` : '')
    + (b.missingQuestions?.length ? `\n[브랜드에 확인할 것]\n${b.missingQuestions.map((q, i) => `${i + 1}. ${q}`).join('\n')}\n` : '')
}

function Row({ label, value, empty = '미정 (확인 필요)' }) {
  const has = Array.isArray(value) ? value.length > 0 : Boolean(value && String(value).trim())
  return (
    <div className="bl-brief-row">
      <span>{label}</span>
      <strong className={has ? '' : 'bl-missing'}>{has ? (Array.isArray(value) ? value.join(', ') : value) : empty}</strong>
    </div>
  )
}

function BriefTab({ loggedIn, remaining, result, onResult }) {

  const [text, setText] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)

  const submit = async () => {
    if (loading || text.trim().length < 10) return
    setLoading(true)
    setError('')
    try {
      const json = await postJson('/api/beauty/brief', { request: text })
      if (json.success) onResult(json)
      else setError(json.message)
    } catch {
      setError('서버에 연결할 수 없습니다.')
    } finally {
      setLoading(false)
    }
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(toText(result))
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      setError('복사하지 못했어요.')
    }
  }

  const b = result

  return (
    <div className="bl-tab">
      <section className="bl-card">
        <p className="bl-muted bl-intro">
          브랜드에서 받은 요청을 그대로 붙여 넣으면, 연구소가 바로 검토할 수 있는 <b>개발 브리프</b>로 정리하고 빠진 정보를 질문으로 뽑아줘요.
        </p>
        <div className="bl-text">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="예) 30대 남성용 올인원 로션, 끈적임 없고 쿨링감 있게. 단가는 3천 원 이하, 200ml 펌프로..."
            maxLength={1500}
            rows={5}
          />
          <button type="button" className="bl-link" onClick={() => setText(SAMPLE)}>예시 넣기</button>
        </div>

        {error && <p className="bl-error" role="alert">{error}</p>}

        {loggedIn ? (
          <div className="bl-submit">
            <button type="button" className="bl-primary" onClick={submit} disabled={loading || text.trim().length < 10}>
              {loading ? 'AI가 브리프를 정리하는 중...' : '브리프 만들기'}
            </button>
            {remaining != null && <span className="bl-remaining">오늘 {remaining}번 남음</span>}
          </div>
        ) : (
          <p className="bl-login"><Link to="/login">로그인</Link>하면 브리프를 만들 수 있어요.</p>
        )}
      </section>

      {b && (
        <>
          <section className="bl-card bl-brief">
            <div className="bl-summary-head">
              <div>
                <span className="bl-eyebrow">개발 브리프</span>
                <h2>{b.productName || '이름 미정'}</h2>
                {b.summary && <p className="bl-muted">{b.summary}</p>}
              </div>
              <button type="button" className="bl-ghost" onClick={copy}>{copied ? '복사됨 ✓' : '📋 복사'}</button>
            </div>

            <div className="bl-brief-grid">
              <Row label="카테고리" value={b.category} />
              <Row label="타깃" value={b.target} />
              <Row label="피부 타입" value={b.skinTypes} />
              <Row label="제형·사용감" value={b.texture} />
              <Row label="핵심 효능" value={b.keyBenefits} />
              <Row label="향" value={b.fragrance} />
              <Row label="용량" value={b.volume} />
              <Row label="목표 단가" value={b.targetPrice} />
              <Row label="용기" value={b.packaging} />
              <Row label="판매 채널" value={b.channel} />
              <Row label="일정" value={b.timeline} />
              <Row label="클레임" value={b.claims} empty="없음" />
            </div>
          </section>

          <div className="bl-two">
            <section className="bl-card">
              <h3 className="bl-card-title">🧪 꼭 넣을 성분</h3>
              {b.mustHaveCheck?.length ? (
                <ul className="bl-list">
                  {b.mustHaveCheck.map((m) => (
                    <li key={m.name}>
                      <b>{m.name}</b>
                      {m.known ? <span> — {m.description}{m.functional ? ` (${m.functional} 기능성 고시 성분)` : ''}</span> : <span className="bl-muted"> — 사전에 없는 성분이에요. 원료 규격을 확인해요.</span>}
                    </li>
                  ))}
                </ul>
              ) : <p className="bl-muted">요청에 언급된 성분이 없어요.</p>}

              <h3 className="bl-card-title bl-gap">🚫 뺄 성분</h3>
              {b.avoid?.length ? (
                <>
                  <p>{b.avoid.join(', ')}</p>
                  {b.avoidIngredients?.length > 0 && (
                    <details className="bl-details">
                      <summary>사전 기준으로 펼치면 {b.avoidIngredients.length}개 성분</summary>
                      <p className="bl-muted">{b.avoidIngredients.join(', ')}</p>
                    </details>
                  )}
                </>
              ) : <p className="bl-muted">따로 빼달라는 성분은 없어요.</p>}
            </section>

            <section className="bl-card">
              <h3 className="bl-card-title">💡 효능별 추천 성분 <small className="bl-muted">(성분 사전 기준)</small></h3>
              {b.suggestions?.length ? b.suggestions.map((g) => (
                <div key={g.benefit} className="bl-suggest">
                  <span className="bl-tag bl-tag-blue">{g.benefit}</span>
                  <ul className="bl-list">
                    {g.ingredients.map((i) => (
                      <li key={i.name}><b>{i.name}</b> — {i.description}{i.functional ? ` (${i.functional} 기능성)` : ''}</li>
                    ))}
                  </ul>
                </div>
              )) : <p className="bl-muted">핵심 효능이 정해지면 추천해드려요.</p>}
            </section>
          </div>

          <div className="bl-two">
            <section className="bl-card">
              <h3 className="bl-card-title">⚖️ 규제·표시 확인 포인트</h3>
              <ul className="bl-list">
                {b.regulatory?.map((r) => <li key={r}>{r}</li>)}
              </ul>
              <p className="bl-disclaimer">확정 판단이 아니라 검토할 항목이에요. 실제 적용은 최신 고시와 담당 부서 확인이 필요해요.</p>
            </section>

            <section className="bl-card">
              <h3 className="bl-card-title">❓ 브랜드에 확인할 것</h3>
              <ol className="bl-list bl-ol">
                {b.missingQuestions?.map((q) => <li key={q}>{q}</li>)}
              </ol>
            </section>
          </div>
        </>
      )}
    </div>
  )
}

export default BriefTab
