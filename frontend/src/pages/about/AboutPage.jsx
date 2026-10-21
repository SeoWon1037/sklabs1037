// ============================================
// 👤 만든 사람 (주소: /about, 로그인 없이 누구나)
// - 개발자·관리자: 이름 · 회사 경력(회사명 · 기간) · GitHub · 문의
// - 이 사이트의 기술 스택 + 어떻게 돌아가는지 (배포 흐름 · 매일 도는 작업)
// - 내용은 아래 상수만 고치면 됨 (서버 호출 없음)
// ============================================
import { Link } from 'react-router-dom'
import { CONTACT_EMAIL, GITHUB_URL } from '../../site'
import './AboutPage.css'

const NAME = '서원'

// 회사 경력 (최신순)
const CAREER = [
  { company: '인동에프엔', from: '2024-04', to: '2026-10' },
  { company: 'BYC', from: '2022-05', to: '2024-03' },
]

// 이 사이트에 실제로 쓴 기술
const STACK = [
  { icon: '🖥️', title: '화면', items: ['React 19', 'Vite', 'React Router 7', 'CSS (모바일 우선)'] },
  { icon: '⚙️', title: '서버', items: ['Java 17', 'Spring Boot 4', 'MyBatis', 'SSE (실시간 토론)'] },
  { icon: '🗄️', title: 'DB', items: ['PostgreSQL (AWS RDS)', 'pgvector (문서 검색)'] },
  { icon: '🤖', title: 'AI', items: ['Claude API (비서 · 요약 · 코치 · QA 봇)', 'Voyage 임베딩 (규제 Q&A)', '도구 호출 + 서버 규칙 검증'] },
  { icon: '☁️', title: '인프라', items: ['AWS EC2', 'Docker Compose', 'nginx', "Let's Encrypt (HTTPS)"] },
  { icon: '🔁', title: '자동화', items: ['GitHub Actions', 'JUnit 테스트 + JaCoCo', '자동 배포', 'Playwright AI QA 봇'] },
  { icon: '📡', title: '데이터', items: ['토스증권 Open API (시세)', 'Google News (뉴스)', 'Open-Meteo (날씨)', 'Frankfurter (환율)'] },
]

// 코드가 올라가서 사이트에 반영되기까지
const PIPELINE = [
  { icon: '💻', title: '코드 push', desc: 'GitHub main' },
  { icon: '🧪', title: '테스트', desc: '단위 테스트 · 커버리지' },
  { icon: '🚀', title: '자동 배포', desc: 'EC2 Docker 재시작' },
  { icon: '🤖', title: 'QA 봇 점검', desc: '화면 캡처 → AI 판정 → 메일' },
]

// 매일 정해진 시각에 서버가 혼자 하는 일
const JOBS = [
  ['07:30 · 16:30', 'AI 뉴스 요약'],
  ['07:40', '오늘의 뉴스 퀴즈 출제'],
  ['08:00', '아침 브리핑 메일'],
  ['09:10', 'AI 투자 리그 매매'],
  ['장중 1분마다', '지정가 체결 · 목표가 알림'],
  ['15:40', '모의투자 자산 기록'],
  ['20:30', '전 종목 시세 수집'],
  ['21:10', '예측 게임 채점'],
  ['30분마다', '뉴스 수집'],
  ['감시', '안 돈 작업은 메일 + 다시 실행'],
]

// "2024-04" ~ "2026-10" → 31 (두 달 모두 포함)
const months = (from, to) => {
  const [fy, fm] = from.split('-').map(Number)
  const [ty, tm] = to.split('-').map(Number)
  return (ty - fy) * 12 + (tm - fm) + 1
}
const span = (m) => [Math.floor(m / 12) && `${Math.floor(m / 12)}년`, m % 12 && `${m % 12}개월`].filter(Boolean).join(' ')
const dot = (ym) => ym.replace('-', '.')

function AboutPage() {

  const total = CAREER.reduce((sum, c) => sum + months(c.from, c.to), 0)

  return (
    <div className="ab-page">

      {/* 프로필 */}
      <section className="ab-profile">
        <div className="ab-avatar" aria-hidden="true">{NAME.slice(0, 1)}</div>
        <div className="ab-who">
          <p className="ab-eyebrow">개발자 · 관리자</p>
          <h1>{NAME}</h1>
          <p className="ab-total">경력 {span(total)}</p>
        </div>
        <div className="ab-links">
          <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="btn btn-ghost">GitHub</a>
          <a href={`mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent('[SKLabs] 문의')}`} className="btn btn-primary">✉️ 문의하기</a>
        </div>
      </section>

      {/* 경력 */}
      <section className="ab-section">
        <h2>💼 경력</h2>
        <ol className="ab-timeline">
          {CAREER.map((c) => (
            <li key={c.company}>
              <span className="ab-dot" aria-hidden="true" />
              <div className="ab-job">
                <strong>{c.company}</strong>
                <span>{dot(c.from)} ~ {dot(c.to)} · {span(months(c.from, c.to))}</span>
              </div>
            </li>
          ))}
        </ol>
      </section>

      {/* 기술 스택 */}
      <section className="ab-section">
        <h2>🛠️ 이 사이트의 기술 스택</h2>
        <div className="ab-stack">
          {STACK.map((s) => (
            <div key={s.title} className="ab-stack-card">
              <p className="ab-stack-title"><span aria-hidden="true">{s.icon}</span> {s.title}</p>
              <div className="ab-chips">
                {s.items.map((i) => <span key={i} className="ab-chip">{i}</span>)}
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* 어떻게 돌아가나요 */}
      <section className="ab-section">
        <h2>🔁 이 사이트는 이렇게 돌아가요</h2>
        <p className="ab-sub">코드를 올리면 사람 손 없이 사이트까지 반영돼요</p>
        <ol className="ab-flow">
          {PIPELINE.map((p, i) => (
            <li key={p.title}>
              <span className="ab-flow-icon" aria-hidden="true">{p.icon}</span>
              <b>{p.title}</b>
              <small>{p.desc}</small>
              {i < PIPELINE.length - 1 && <i className="ab-flow-arrow" aria-hidden="true">→</i>}
            </li>
          ))}
        </ol>

        <p className="ab-sub ab-sub-gap">서버가 매일 혼자 하는 일</p>
        <ul className="ab-jobs">
          {JOBS.map(([time, what]) => (
            <li key={what}><span>{time}</span>{what}</li>
          ))}
        </ul>
      </section>

      <p className="ab-foot">
        사이트 기능은 <Link to="/">메인</Link>의 ☰ 전체 메뉴에서 모두 볼 수 있어요 · 원하는 기능은 <Link to="/ideas">💡 기능 제안</Link>으로
      </p>
    </div>
  )
}

export default AboutPage
