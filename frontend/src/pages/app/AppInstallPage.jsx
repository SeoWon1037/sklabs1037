// ============================================
// 📲 앱 설치 안내 (주소: /app, 로그인 없이 누구나)
// - 이 링크 하나만 보내면 기기에 맞는 방법이 나옴
//   · 카카오톡·인스타 등 앱 안 브라우저 → 바깥 브라우저로 열기 (여기서는 설치가 안 됨)
//   · 안드로이드 · PC 크롬/엣지 → [설치하기] 버튼 한 번
//   · 아이폰 → 공유 → 홈 화면에 추가 3단계 그림 안내 (애플이 버튼 설치를 막아 둠)
//   · 이미 앱으로 열었으면 → 알림 켜기 안내
// - PC에서는 QR(public/app-qr.svg = https://sklabs1037.com/app)로 휴대폰에 바로
// ============================================
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { inAppBrowser, installMode, isAndroid, isIos, isMobile, onPwaChange, openOutsideUrl, promptInstall } from '../../pwa'
import './AppInstallPage.css'

const APP_URL = 'https://sklabs1037.com/app'

const PERKS = [
  ['🏠', '아이콘 한 번에', '홈 화면·바탕화면에서 바로 열려요'],
  ['🔔', '푸시 알림', '하트·대결 결과·가격 알림이 앱을 닫아도 와요'],
  ['🖥️', '꽉 찬 화면', '주소창 없이 앱처럼 넓게'],
  ['⚡', '설치 파일 없음', '용량 거의 0, 업데이트도 자동이에요'],
]

function IosSteps() {
  return (
    <ol className="ins-steps">
      <li>
        <span className="ins-num">1</span>
        <div>
          <b>아래쪽 공유 버튼</b>을 눌러요
          <div className="ins-mock ins-mock-bar" aria-hidden="true">
            <span>‹</span><span>›</span><span className="ins-hit">⬆︎</span><span>📖</span><span>⧉</span>
          </div>
          <small>Safari 주소창 옆이나 화면 아래에 있는 □↑ 모양이에요. 안 보이면 화면을 살짝 위로 밀어보세요.</small>
        </div>
      </li>
      <li>
        <span className="ins-num">2</span>
        <div>
          <b>홈 화면에 추가</b>를 골라요
          <div className="ins-mock ins-mock-sheet" aria-hidden="true">
            <span>복사</span><span>읽기 목록에 추가</span><span className="ins-hit">홈 화면에 추가 ⊞</span><span>책갈피 추가</span>
          </div>
          <small>목록을 아래로 내려야 보일 수 있어요.</small>
        </div>
      </li>
      <li>
        <span className="ins-num">3</span>
        <div>
          <b>웹 앱으로 열기</b>가 켜진 채로 <b>추가</b>
          <div className="ins-mock ins-mock-add" aria-hidden="true">
            <img src="/icon-192.png" alt="" /><span>SKLabs</span><span className="ins-switch">웹 앱으로 열기 <i /></span><span className="ins-hit">추가</span>
          </div>
          <small>홈 화면의 SKLabs 아이콘으로 열고 로그인하면 끝! 🔔 알림 창에서 푸시 알림도 켤 수 있어요.</small>
        </div>
      </li>
    </ol>
  )
}

function AppInstallPage() {

  const [mode, setMode] = useState(installMode)
  const [copied, setCopied] = useState(false)
  const [done, setDone] = useState(false)

  useEffect(() => onPwaChange(() => setMode(installMode())), [])

  const inApp = inAppBrowser()
  const outside = inApp ? openOutsideUrl(APP_URL) : null

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(APP_URL)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setCopied(false)
    }
  }

  const install = async () => {
    if (await promptInstall()) setDone(true)
  }

  let body
  if (mode === 'installed' || done) {
    body = (
      <section className="ins-card ins-ok">
        <h2>✅ 앱으로 쓰고 있어요</h2>
        <p>상단 🔔 알림 창 맨 아래 <b>📲 휴대폰·PC 알림 [켜기]</b>를 누르면 앱을 닫아도 알림이 와요.</p>
        <Link to="/" className="ins-btn">홈으로</Link>
      </section>
    )
  } else if (inApp) {
    body = (
      <section className="ins-card ins-warn">
        <h2>{inApp} 안에서는 설치가 안 돼요</h2>
        <p>{isIos() ? 'Safari' : '크롬'}로 열어서 설치해 주세요.</p>
        {outside
          ? <a href={outside} className="ins-btn">{isIos() ? 'Safari' : '크롬'}로 열기</a>
          : <p className="ins-small">오른쪽 위 ⋯ 메뉴에서 <b>Safari로 열기</b>를 누르거나, 링크를 복사해 Safari 주소창에 붙여넣어 주세요.</p>}
        <button type="button" className="ins-btn ins-btn-ghost" onClick={copy}>{copied ? '복사했어요' : '🔗 링크 복사'}</button>
      </section>
    )
  } else if (mode === 'prompt') {
    body = (
      <section className="ins-card">
        <h2>버튼 한 번이면 설치돼요</h2>
        <button type="button" className="ins-btn ins-btn-big" onClick={install}>📲 SKLabs 설치하기</button>
        <p className="ins-small">{isMobile() ? '홈 화면에 아이콘이 생겨요.' : '바탕화면·시작 메뉴에 아이콘이 생기고 별도 창으로 열려요.'}</p>
      </section>
    )
  } else if (mode === 'ios') {
    body = (
      <section className="ins-card">
        <h2>아이폰은 3단계예요</h2>
        <IosSteps />
      </section>
    )
  } else {
    body = (
      <section className="ins-card">
        <h2>브라우저 메뉴에서 설치해요</h2>
        <p>
          {isAndroid()
            ? <>오른쪽 위 <b>⋮ 메뉴</b> → <b>앱 설치</b> 또는 <b>홈 화면에 추가</b>를 눌러요.</>
            : <>주소창 오른쪽의 <b>설치 아이콘(⊕)</b>이나 <b>⋮ 메뉴 → 앱 설치</b>를 눌러요. 크롬·엣지에서 돼요.</>}
        </p>
        <p className="ins-small">버튼이 안 보이면 이미 설치했거나, 이 브라우저가 설치를 지원하지 않는 경우예요.</p>
      </section>
    )
  }

  return (
    <main className="ins-page">
      <header className="ins-hero">
        <img src="/icon-192.png" alt="" className="ins-icon" />
        <div>
          <h1>SKLabs 앱 설치</h1>
          <p>앱스토어 없이, 지금 이 페이지에서 바로</p>
        </div>
      </header>

      <div className="ins-grid">
        <div className="ins-main">
          {body}

          <ul className="ins-perks">
            {PERKS.map(([icon, title, desc]) => (
              <li key={title}><span aria-hidden="true">{icon}</span><b>{title}</b><small>{desc}</small></li>
            ))}
          </ul>
        </div>

        {!isMobile() && (
          <aside className="ins-card ins-qr">
            <h2>📱 휴대폰에도 설치</h2>
            <img src="/app-qr.svg" alt="sklabs1037.com/app QR 코드" />
            <p>휴대폰 카메라로 찍으면 이 페이지가 열려요.</p>
            <button type="button" className="ins-link" onClick={copy}>{copied ? '복사했어요' : '🔗 링크 복사 (카톡으로 보내기)'}</button>
          </aside>
        )}
      </div>
    </main>
  )
}

export default AppInstallPage
