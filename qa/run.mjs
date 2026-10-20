// ============================================
// 🤖 AI QA 봇 (브라우저 쪽) - GitHub Actions에서 실행 (.github/workflows/qa-bot.yml)
//
// 운영 사이트 주요 화면을 휴대폰(390)·PC(1400) 크기의 진짜 Chromium으로 열고
//   - 로딩 시간, 콘솔 에러, 페이지 오류, 실패한 요청(4xx·5xx), 가로 넘침 측정
//   - 기능 흐름: 종목 검색, 처방 검토 예시 → 결과가 나오는지 눌러봄
//   - 화면 캡처 (JPEG)
// → POST {QA_BASE_URL}/api/qa/report (헤더 X-QA-Token) → 서버가 규칙 + Claude 화면 판정
//
// 환경변수: QA_BASE_URL (기본 https://sklabs1037.com), QA_TOKEN, QA_SOURCE(SCHEDULE|DEPLOY|MANUAL), GITHUB_SHA
// 로컬에서 보내지 않고 결과만 보기: QA_DRY_RUN=1 node run.mjs
// ============================================
import { chromium } from 'playwright'

const BASE = (process.env.QA_BASE_URL || 'https://sklabs1037.com').replace(/\/$/, '')
const TOKEN = process.env.QA_TOKEN || ''
const DRY = process.env.QA_DRY_RUN === '1'

const VIEWPORTS = [
  { name: 'mobile', width: 390, height: 844, scale: 2 },
  { name: 'pc', width: 1400, height: 900, scale: 1 },
]

// 점검할 화면 (key는 서버 보고서·캡처 이름)
const PAGES = [
  { key: 'home', name: '메인', path: '/' },
  { key: 'stock', name: '주식 종목', path: '/stock?symbol=005930' },
  { key: 'mock', name: '모의투자', path: '/mock' },
  { key: 'league', name: 'AI 리그', path: '/league' },
  { key: 'ai', name: 'AI 비서', path: '/ai' },
  { key: 'beauty', name: '뷰티 랩', path: '/beauty' },
  {
    key: 'formula',
    name: '처방 검토',
    path: '/beauty?tab=formula',
    // 기능 흐름: 예시 넣기 → 처방 검토하기 → 결과 판정이 보이는지
    flow: {
      name: '처방 검토 예시',
      run: async (page) => {
        await page.getByRole('button', { name: '예시 넣기' }).click()
        await page.getByRole('button', { name: '처방 검토하기' }).click()
        await page.locator('.fm-verdict').waitFor({ timeout: 15000 })
        return '결과 판정이 나왔어요'
      },
    },
  },
  {
    key: 'search',
    name: '종목 검색',
    path: '/stock',
    // 기능 흐름: 검색창에 "삼성" → 검색 결과에 삼성전자
    flow: {
      name: '종목 검색',
      run: async (page) => {
        await page.locator('.stock-search input').fill('삼성')
        await page.getByText('삼성전자').first().waitFor({ timeout: 10000 })
        return '"삼성" 검색 결과가 나왔어요'
      },
    },
  },
]

// 흔하고 사용자에게 안 보이는 콘솔 메시지는 빼기
const IGNORED_CONSOLE = [/favicon/i, /Download the React DevTools/i]

async function checkPage(browser, vp, target) {

  const context = await browser.newContext({
    viewport: { width: vp.width, height: vp.height },
    deviceScaleFactor: vp.scale,
    colorScheme: 'dark',
    locale: 'ko-KR',
    userAgent: `SKLabs-QA-Bot (+${BASE}) Playwright Chromium`,
  })
  const page = await context.newPage()

  const consoleErrors = []
  const pageErrors = []
  const failedRequests = []

  page.on('console', (msg) => {
    if (msg.type() === 'error' && !IGNORED_CONSOLE.some((r) => r.test(msg.text()))) consoleErrors.push(msg.text().slice(0, 300))
  })
  page.on('pageerror', (err) => pageErrors.push(String(err.message || err).slice(0, 300)))
  page.on('response', (res) => {
    const url = res.url()
    // 같은 사이트 요청만, 로그인 안 한 상태라 401은 정상
    if (url.startsWith(BASE) && res.status() >= 400 && res.status() !== 401) failedRequests.push({ url: url.replace(BASE, ''), status: res.status() })
  })
  page.on('requestfailed', (req) => {
    if (req.url().startsWith(BASE) && !req.url().includes('/stream')) failedRequests.push({ url: req.url().replace(BASE, ''), status: 0 })
  })

  const result = { key: `${target.key}-${vp.name}`, page: target.path, name: target.name, viewport: vp.name, width: vp.width }
  const started = Date.now()

  try {

    const res = await page.goto(BASE + target.path, { waitUntil: 'domcontentloaded', timeout: 30000 })
    if (!res || res.status() >= 400) pageErrors.push(`화면 주소 응답 ${res ? res.status() : '없음'}`)

    // 실시간 연결(SSE)이 있어서 networkidle은 오지 않을 수 있음 → "불러오는 중" 문구가 사라질 때까지 최대 15초
    await page.waitForLoadState('load', { timeout: 30000 }).catch(() => {})
    await page.waitForFunction(() => !document.body.innerText.includes('불러오는 중'), null, { timeout: 15000 }).catch(() => {})
    result.loadMs = Date.now() - started

    result.overflowPx = await page.evaluate(() => Math.max(0, document.documentElement.scrollWidth - window.innerWidth))

    if (target.flow) {
      try {
        result.flow = { name: target.flow.name, ok: true, message: await target.flow.run(page) }
      } catch (e) {
        const msg = String(e.message || e).split('\n')[0]
        result.flow = { name: target.flow.name, ok: false, message: (/Timeout/i.test(msg) ? '기다려도 결과가 나오지 않았어요 · ' : '') + msg.slice(0, 200) }
      }
      await page.waitForTimeout(500)
    }

    const shot = await page.screenshot({ type: 'jpeg', quality: 55, fullPage: false })
    result.screenshot = shot.toString('base64')

  } catch (e) {

    pageErrors.push(`화면을 열지 못했어요: ${String(e.message || e).split('\n')[0].slice(0, 200)}`)
    result.loadMs = Date.now() - started

  }

  result.consoleErrors = consoleErrors.slice(0, 10)
  result.pageErrors = pageErrors.slice(0, 10)
  result.failedRequests = failedRequests.slice(0, 15)

  await context.close()

  return result
}

const browser = await chromium.launch()
const checks = []

for (const target of PAGES) {
  for (const vp of VIEWPORTS) {
    const r = await checkPage(browser, vp, target)
    checks.push(r)
    const problems = r.pageErrors.length + r.failedRequests.length + r.consoleErrors.length + (r.flow && !r.flow.ok ? 1 : 0)
    console.log(`${problems ? '⚠️' : '✅'} ${r.key.padEnd(16)} ${String(r.loadMs).padStart(5)}ms  넘침 ${r.overflowPx ?? '-'}px  문제 ${problems}${r.flow ? `  흐름 ${r.flow.ok ? '성공' : '실패'}` : ''}`)
  }
}

await browser.close()

const payload = { source: process.env.QA_SOURCE || 'MANUAL', commit: process.env.GITHUB_SHA || null, checks }

if (DRY) {
  console.log(JSON.stringify(checks.map(({ screenshot, ...rest }) => ({ ...rest, screenshotKB: screenshot ? Math.round(screenshot.length / 1024) : 0 })), null, 2))
  process.exit(0)
}

// 아직 QA_TOKEN을 안 넣었으면 점검만 하고 끝 (배포 화면이 빨간 X가 되지 않게)
if (!TOKEN) {
  console.warn('⚠️ QA_TOKEN이 없어서 결과를 서버로 보내지 않았어요. (GitHub Secrets와 서버 .env에 QA_TOKEN 추가)')
  process.exit(0)
}

const res = await fetch(`${BASE}/api/qa/report`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'X-QA-Token': TOKEN },
  body: JSON.stringify(payload),
})
const body = await res.json().catch(() => ({}))

if (!res.ok || !body.success) {
  console.error(`결과 보내기 실패: ${res.status} ${body.message || ''}`)
  process.exit(1)
}

console.log(`보냈어요 (점검 #${body.runId}). 서버가 Claude로 화면을 분석해서 관리자 화면 /admin/qa 에 올려요.`)
