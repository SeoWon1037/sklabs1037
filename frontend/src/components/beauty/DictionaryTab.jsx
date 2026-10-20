// ============================================
// 뷰티 랩 > 📖 성분 사전 (누구나)
// - 성분 분석·기획 브리프가 판단할 때 쓰는 바로 그 사전
// - 검색 + 빠른 보기(기능성 / 알레르기 착향제 / 주의 성분)
// ============================================
import { useEffect, useMemo, useState } from 'react'
import { categoryTone } from './beautyUtils'

const FILTERS = [
  { value: 'ALL', label: '전체' },
  { value: 'FUNC', label: '기능성 고시 성분' },
  { value: 'ALLERGEN', label: '알레르기 착향제 25종' },
  { value: 'CAUTION', label: '주의 성분' },
]

function DictionaryTab() {

  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('ALL')
  const [data, setData] = useState({ key: null, items: [], total: 0 })

  const trimmed = query.trim()
  useEffect(() => {
    let ignore = false
    const timer = setTimeout(() => {
      fetch(`/api/beauty/ingredients?q=${encodeURIComponent(trimmed)}`)
        .then((res) => res.json())
        .then((json) => { if (!ignore && json.success) setData({ key: trimmed, items: json.ingredients, total: json.total }) })
        .catch(() => { if (!ignore) setData({ key: trimmed, items: [], total: 0 }) })
    }, trimmed ? 250 : 0)
    return () => { ignore = true; clearTimeout(timer) }
  }, [trimmed])

  const items = useMemo(() => data.items.filter((i) => {
    if (filter === 'FUNC') return Boolean(i.functional)
    if (filter === 'ALLERGEN') return i.allergenYn === 'Y'
    if (filter === 'CAUTION') return Boolean(i.caution)
    return true
  }), [data.items, filter])

  return (
    <div className="bl-tab">
      <section className="bl-card">
        <p className="bl-muted bl-intro">
          SKLabs가 직접 정리한 화장품 성분 사전이에요 ({data.total}개). 성분 분석과 기획 브리프는 AI의 짐작 대신 이 사전을 기준으로 판단해요.
        </p>
        <input
          className="bl-search"
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="🔍 성분명 · 영문명 · 역할 검색"
          aria-label="성분 검색"
          maxLength={50}
        />
        <div className="bl-chips">
          {FILTERS.map((f) => (
            <button key={f.value} type="button" className={`bl-chip ${filter === f.value ? 'bl-chip-active' : ''}`} onClick={() => setFilter(f.value)}>{f.label}</button>
          ))}
        </div>
      </section>

      <section className="bl-card">
        {data.key !== trimmed && <p className="bl-muted">찾는 중...</p>}
        {data.key === trimmed && items.length === 0 && <p className="bl-muted">해당하는 성분이 없어요.</p>}
        <ul className="bl-dict">
          {items.map((i) => (
            <li key={i.ingredientId} className="bl-dict-item">
              <div className="bl-ing-name">
                <strong>{i.nameKo}</strong>
                <small>{i.nameEn}</small>
              </div>
              <div className="bl-dict-tags">
                <span className={`bl-tag bl-tag-${categoryTone(i.category)}`}>{i.category}</span>
                {i.functional && <span className="bl-tag bl-tag-violet">{i.functional} 기능성 고시</span>}
                {i.allergenYn === 'Y' && <span className="bl-tag bl-tag-amber">알레르기 착향제</span>}
              </div>
              <p>{i.description}</p>
              {i.caution && <p className="bl-ing-caution">⚠️ {i.caution}</p>}
              {i.aliases && <p className="bl-muted bl-alias">다른 표기: {i.aliases.split(',').join(', ')}</p>}
            </li>
          ))}
        </ul>
        <p className="bl-disclaimer">
          기능성 고시 성분·알레르기 유발 착향제는 식약처 고시를 기준으로 정리했어요. 성분 설명은 일반 정보이며 최신 고시는 식약처에서 확인하세요.
        </p>
      </section>
    </div>
  )
}

export default DictionaryTab
