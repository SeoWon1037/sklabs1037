// ============================================
// 뷰티 랩 (주소: /beauty, /beauty?tab=brief, /beauty?tab=dict)
// - 🔬 성분 분석: 화장품 전성분 사진/글 → 역할·기능성·알레르기·내 피부 주의 성분
// - ⚖️ 성분 비교: 두 제품 전성분 → 공통 / 한쪽에만, 겹침 %, 기능성·향료·착향제·내 피부 주의 수
// - 📝 기획 브리프: 브랜드 요청 → 개발 브리프 + 규제 확인 + 추천 성분 + 되물을 질문
// - 📚 규제 Q&A: 규제 문서에서 뜻이 가까운 문단(pgvector) → 그 문단만 근거로 답 + 출처 번호
// - 📖 성분 사전: 판단 기준이 되는 사전 (누구나)
// - 설계 원칙: "AI는 읽고 정리만, 판단은 직접 정리한 사전과 규칙으로" → AI가 성분 효능을 지어내지 않게
// - 백엔드 API: /api/beauty/analyze, /api/beauty/brief, /api/beauty/ingredients, /api/beauty/history
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import AnalyzeTab from '../../components/beauty/AnalyzeTab'
import BriefTab from '../../components/beauty/BriefTab'
import CompareTab from '../../components/beauty/CompareTab'
import DictionaryTab from '../../components/beauty/DictionaryTab'
import FormulaTab from '../../components/beauty/FormulaTab'
import ShelfTab from '../../components/beauty/ShelfTab'
import RegulationTab from '../../components/beauty/RegulationTab'
import { formatDay } from '../../components/beauty/beautyUtils'
import './BeautyLabPage.css'

const TABS = [
  { value: 'analyze', label: '🔬 성분 분석' },
  { value: 'compare', label: '⚖️ 성분 비교' },
  { value: 'shelf', label: '🧴 내 화장대' },
  { value: 'formula', label: '🧪 처방 검토' },
  { value: 'brief', label: '📝 기획 브리프' },
  { value: 'law', label: '📚 규제 Q&A' },
  { value: 'dict', label: '📖 성분 사전' },
]

function BeautyLabPage() {

  const [searchParams, setSearchParams] = useSearchParams()
  const tab = TABS.some((t) => t.value === searchParams.get('tab')) ? searchParams.get('tab') : 'analyze'
  const setTab = (value) => setSearchParams(value === 'analyze' ? {} : { tab: value }, { replace: true })

  const [history, setHistory] = useState(null)       // { loggedIn, logs, remainingAnalyze, remainingBrief }
  const [analysis, setAnalysis] = useState(null)
  const [brief, setBrief] = useState(null)
  const [compare, setCompare] = useState(null)
  const [qa, setQa] = useState(null)
  const [historyMsg, setHistoryMsg] = useState('')

  const loadHistory = useCallback(() => {
    return fetch('/api/beauty/history')
      .then((res) => res.json())
      .then((json) => setHistory(json.success ? json : { loggedIn: false }))
      .catch(() => setHistory({ loggedIn: false }))
  }, [])

  useEffect(() => { loadHistory() }, [loadHistory])

  const openLog = async (log) => {
    setHistoryMsg('')
    try {
      const res = await fetch(`/api/beauty/history/${log.logId}`)
      const json = await res.json()
      if (!json.success) {
        setHistoryMsg(json.message)
        return
      }
      if (json.kind === 'BRIEF') {
        setBrief(json)
        setTab('brief')
      } else if (json.kind === 'ASK') {
        setQa(json)
        setTab('law')
      } else if (json.kind === 'COMPARE') {
        setCompare(json)
        setTab('compare')
      } else {
        setAnalysis(json)
        setTab('analyze')
      }
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch {
      setHistoryMsg('기록을 불러오지 못했어요.')
    }
  }

  const loggedIn = Boolean(history?.loggedIn)

  return (
    <main className="bl-page">
      <div className="bl-head">
        <div>
          <h1>💄 뷰티 랩</h1>
          <p>화장품 성분을 읽고, 제품 기획 요청을 개발 브리프로 정리해요.</p>
        </div>
        <span className="bl-principle" title="AI가 성분 효능을 지어내지 않도록 판단은 직접 정리한 성분 사전과 규칙으로 해요">
          🤖 AI는 읽고 정리만 · ✅ 판단은 성분 사전으로
        </span>
      </div>

      <nav className="bl-tabs" role="tablist" aria-label="뷰티 랩 메뉴">
        {TABS.map((t) => (
          <button key={t.value} type="button" role="tab" aria-selected={tab === t.value} className={`bl-tabbtn ${tab === t.value ? 'bl-tabbtn-active' : ''}`} onClick={() => setTab(t.value)}>
            {t.label}
          </button>
        ))}
      </nav>

      <div className="bl-layout">
        <div className="bl-main">
          {tab === 'analyze' && (
            <AnalyzeTab
              loggedIn={loggedIn}
              remaining={history?.remainingAnalyze}
              result={analysis}
              onResult={(r) => { setAnalysis(r); loadHistory() }}
            />
          )}
          {tab === 'compare' && (
            <CompareTab
              loggedIn={loggedIn}
              remaining={history?.remainingCompare}
              result={compare}
              onResult={(r) => { setCompare(r); loadHistory() }}
            />
          )}
          {tab === 'brief' && (
            <BriefTab
              loggedIn={loggedIn}
              remaining={history?.remainingBrief}
              result={brief}
              onResult={(r) => { setBrief(r); loadHistory() }}
            />
          )}
          {tab === 'law' && (
            <RegulationTab loggedIn={loggedIn} result={qa} onResult={(r) => { setQa(r); loadHistory() }} />
          )}
          {tab === 'shelf' && <ShelfTab />}
          {tab === 'formula' && <FormulaTab />}
          {tab === 'dict' && <DictionaryTab />}
        </div>

        {/* ===== 최근 기록 ===== */}
        {loggedIn && (
          <aside className="bl-side">
            <section className="bl-card">
              <h3 className="bl-card-title">🕘 최근 기록</h3>
              {historyMsg && <p className="bl-error">{historyMsg}</p>}
              {history.logs?.length === 0 && <p className="bl-muted">아직 기록이 없어요.</p>}
              <ul className="bl-history">
                {history.logs?.map((l) => (
                  <li key={l.logId}>
                    <button type="button" onClick={() => openLog(l)}>
                      <span className={`bl-tag ${l.kind === 'BRIEF' ? 'bl-tag-blue' : l.kind === 'COMPARE' ? 'bl-tag-violet' : l.kind === 'ASK' ? 'bl-tag-amber' : 'bl-tag-green'}`}>
                        {l.kind === 'BRIEF' ? '브리프' : l.kind === 'COMPARE' ? '비교' : l.kind === 'ASK' ? '규제 Q&A' : l.inputType === 'IMAGE' ? '사진 분석' : '분석'}
                      </span>
                      <strong>{l.title}</strong>
                      <small>{formatDay(l.insDay)}{l.itemCount ? ` · 성분 ${l.itemCount}개` : ''}</small>
                    </button>
                  </li>
                ))}
              </ul>
              <p className="bl-muted bl-small">분석·비교·브리프는 각각 하루 {history.limit}번까지 쓸 수 있어요.</p>
            </section>
          </aside>
        )}
      </div>
    </main>
  )
}

export default BeautyLabPage
