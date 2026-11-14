// ============================================
// 관리자: 📊 사용 통계 (주소: /admin/stats)
// - 오늘 · 7일 방문자 / 페이지뷰 / 가입, 최근 30일 방문자 막대
// - 인기 화면 (7일), 기능별 사용 (오늘 · 7일), 🧪 체험 → 가입
// - 방문자 = 브라우저 무작위 번호를 서버에서 해시해서 하루 단위로만 셈 (IP·회원 번호 저장 안 함, 봇·QA 봇 제외)
// - 백엔드 API (관리자만): GET /api/admin/stats
// ============================================
import { useEffect, useMemo, useState } from 'react'
import AdminTabs from '../../components/admin/AdminTabs'
import './AdminFeedbackPage.css'
import './AdminApiPage.css'
import './AdminStatsPage.css'

const num = (n) => Number(n ?? 0).toLocaleString('ko-KR')
const PAGE_NAMES = {
  '/': '투자 홈', '/market': '중고거래', '/whatif': '그때 샀더라면', '/tree': '글자 트리', '/translate': '번역', '/invest': '투자 홈(예전 주소)', '/stock': '주식 종목', '/mock': '모의투자', '/ai': 'AI 챗봇', '/beauty': '뷰티 랩', '/league': 'AI 리그',
  '/predict': '예측 게임', '/quiz': '뉴스 퀴즈', '/calendar': '증시 일정', '/messages': '메시지', '/mypage': '마이페이지',
  '/activity': '내 활동', '/login': '로그인', '/signup': '회원가입', '/about': '만든 사람', '/status': '서비스 상태',
  '/support': '문의하기', '/ideas': '기능 제안', '/duel': '친구 대결', '/couple': '커플', '/lotto': '오늘의 숫자', '/persona': '투자 성향', '/app': '앱 설치',
}

function Bars({ days }) {

  const max = Math.max(1, ...days.map((d) => d.visitors))
  const W = 480
  const H = 120
  const bw = W / days.length

  return (
    <figure className="ap-chart">
      <div className="ap-plot">
        <span className="ap-max">{num(max)}명</span>
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img" aria-label="최근 30일 하루 방문자 수">
          {days.map((d, i) => {
            const h = d.visitors > 0 ? Math.max((d.visitors / max) * H, 2) : 0
            return (
              <g key={d.day}>
                <title>{`${d.day.slice(5).replace('-', '/')} · 방문자 ${num(d.visitors)}명 · 화면 ${num(d.pv)}번 · 가입 ${num(d.signups)}명`}</title>
                <rect x={i * bw + 1.5} y={H - h} width={bw - 3} height={h} className={i === days.length - 1 ? 'stt-bar-today' : 'ap-bar'} />
                {d.signups > 0 && <rect x={i * bw + 3} y={Math.max(H - h - 7, 1)} width={bw - 6} height="4" rx="1" className="stt-dot" />}
              </g>
            )
          })}
        </svg>
      </div>
      <div className="ap-axis">
        <span>{days[0]?.day.slice(5).replace('-', '/')}</span>
        <span>{days[15]?.day.slice(5).replace('-', '/')}</span>
        <span>오늘</span>
      </div>
      <p className="stt-legend"><i className="stt-key-bar" />하루 방문자 <i className="stt-key-dot" />가입이 있던 날</p>
    </figure>
  )
}

function AdminStatsPage() {

  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let ignore = false
    fetch('/api/admin/stats')
      .then((r) => r.json())
      .then((j) => {
        if (ignore) return
        if (!j.success) throw new Error(j.message)
        setData(j)
      })
      .catch((e) => { if (!ignore) setError(e.message || '불러오지 못했어요.') })
    return () => { ignore = true }
  }, [])

  const days = useMemo(() => {
    if (!data) return []
    const su = Object.fromEntries((data.signups ?? []).map((s) => [s.day, s.signups]))
    return (data.daily ?? []).map((d) => ({ ...d, signups: su[d.day] ?? 0 }))
  }, [data])

  const today = days[days.length - 1] ?? { visitors: 0, pv: 0, signups: 0 }
  const week = days.slice(-7)
  const sum = (arr, k) => arr.reduce((s, d) => s + Number(d[k] ?? 0), 0)
  const pvMax = Math.max(1, ...(data?.pages ?? []).map((p) => p.pv))
  const demo = data?.events?.demo_login ?? 0
  const demoSignup = data?.events?.demo_signup ?? 0

  return (
    <main className="admin-page">
      <AdminTabs />
      <div className="admin-head">
        <h1 className="admin-title">사용 통계</h1>
        <p className="admin-desc">방문자는 브라우저 단위로 하루에 한 번만 세요 (IP·회원 번호는 저장하지 않음, 검색 로봇·QA 봇 제외). 1분마다 모아서 저장해요.</p>
      </div>

      {error && <p className="admin-empty admin-error">{error}</p>}
      {!error && !data && <p className="admin-empty">불러오는 중...</p>}

      {data && (
        <>
          <div className="ap-summary">
            <div><span>오늘 방문자</span><b>{num(today.visitors)}</b></div>
            <div><span>오늘 화면 열기</span><b>{num(today.pv)}</b></div>
            <div><span>7일 방문자 (일 합계)</span><b>{num(sum(week, 'visitors'))}</b></div>
            <div><span>7일 가입</span><b>{num(sum(week, 'signups'))}</b></div>
          </div>

          <h2 className="ap-h2">최근 30일</h2>
          {days.length > 0 && <Bars days={days} />}

          <div className="stt-grid">
            <section className="ap-table">
              <h2 className="ap-h2">인기 화면 <small>최근 7일</small></h2>
              {(data.pages ?? []).length === 0
                ? <p className="admin-empty">아직 기록이 없어요.</p>
                : (
                  <ul className="stt-pages">
                    {data.pages.map((p) => (
                      <li key={p.key}>
                        <span className="stt-name">{PAGE_NAMES[p.key] ?? p.key}</span>
                        <span className="stt-track"><i style={{ width: `${(p.pv / pvMax) * 100}%` }} /></span>
                        <b>{num(p.pv)}</b>
                      </li>
                    ))}
                  </ul>
                )}
            </section>

            <section className="ap-table">
              <h2 className="ap-h2">기능별 사용</h2>
              <div className="ap-scroll">
                <table>
                  <thead><tr><th>기능</th><th className="ap-n">오늘</th><th className="ap-n">7일</th></tr></thead>
                  <tbody>
                    {(data.features ?? []).map((f) => (
                      <tr key={f.feature}><td>{f.feature}</td><td className="ap-n">{num(f.today)}</td><td className="ap-n">{num(f.week)}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <h2 className="ap-h2">🧪 체험 → 가입 <small>최근 7일</small></h2>
              <div className="stt-funnel">
                <div><span>체험 시작</span><b>{num(demo)}</b></div>
                <span aria-hidden="true">→</span>
                <div><span>체험 중 가입</span><b>{num(demoSignup)}</b></div>
                <div><span>전환율</span><b>{demo > 0 ? `${Math.round((demoSignup / demo) * 100)}%` : '-'}</b></div>
              </div>
            </section>
          </div>
        </>
      )}
    </main>
  )
}

export default AdminStatsPage
