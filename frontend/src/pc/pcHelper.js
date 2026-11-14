// ============================================
// 🖥️ 내 PC 프로그램 실행 (PC 도우미와 이야기하는 곳)
// - 웹사이트는 내 PC의 프로그램을 직접 실행할 수 없음 → 그 PC에서 켜 둔 작은 도우미(tools/pc-helper)가 대신 실행
// - 도우미는 이 PC 안(127.0.0.1)에서만 듣고, 이 사이트에서 온 요청 + 연결 표(token)가 맞는 요청만 받음
// - 연결 표는 이 브라우저에만 저장(localStorage). 서버로 보내지 않음
// - 사이트가 도우미에게 보내는 것은 "목록의 번호(id)"뿐 — 명령어나 파일 경로를 보내는 기능은 없음
// - 크롬은 처음 한 번 "이 기기의 앱에 접근" 허용을 물어봄 (Local Network Access)
// ============================================
const PORT = 47613
const BASE = `http://127.0.0.1:${PORT}`
const TOKEN_KEY = 'sklabs.pc.token'
const CACHE_MS = 5 * 60 * 1000

export const pcToken = () => {
  try { return localStorage.getItem(TOKEN_KEY) || '' } catch { return '' }
}
export const setPcToken = (token) => {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch { /* 저장 못 해도 이번 화면에서는 동작 */ }
  cache = null
}
export const pcPaired = () => pcToken() !== ''

// 주소의 # 뒤에 실려 온 연결 표 (도우미가 처음 켜질 때 연 화면) — 모양이 맞을 때만
export const tokenFromHash = (hash) => {
  const m = /[#&]pair=([A-Za-z0-9_-]{32,64})(?:&|$)/.exec(String(hash ?? ''))
  return m ? m[1] : ''
}

const call = async (path, { method = 'GET', body } = {}) => {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'X-SKLabs-Token': pcToken(), ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
    targetAddressSpace: 'loopback',   // 크롬: 이 PC로 가는 요청임을 알림 (허용 창이 뜨고, https → http도 통과)
  })
  const json = await res.json().catch(() => ({}))
  if (!res.ok || !json.ok) throw new Error(json.message || (res.status === 401 ? '연결 표가 맞지 않아요.' : 'PC 도우미가 응답하지 않아요.'))
  return json
}

// 도우미가 켜져 있는지 (표 없이도 확인됨)
export const pcHealth = () => call('/health').then(() => true).catch(() => false)

let cache = null   // { at, programs }
export const pcPrograms = async (fresh = false) => {
  if (!fresh && cache && Date.now() - cache.at < CACHE_MS) return cache.programs
  const json = await call(fresh ? '/rescan' : '/programs', fresh ? { method: 'POST' } : {})
  const list = fresh ? (await call('/programs')).programs : json.programs
  cache = { at: Date.now(), programs: list }
  return list
}

export const pcLaunch = (id) => call('/launch', { method: 'POST', body: { id } })

// 🌐 챗봇이 열기로 한 바깥 사이트 열기 → 열렸으면 true
// - PC 도우미가 연결되어 있으면 도우미가 기본 브라우저로 엶 (가장 확실)
// - 아니면 새 창으로. 브라우저가 "누르지 않았는데 뜨는 창"으로 보고 막으면 false → 화면이 누를 수 있는 링크를 보여 줌
export async function openSite(url) {
  if (!/^https:\/\//.test(String(url ?? ''))) return false
  if (pcPaired()) {
    try {
      await call('/open', { method: 'POST', body: { url } })
      return true
    } catch { /* 도우미가 꺼져 있거나 예전 것이면 새 창으로 */ }
  }
  try {
    const win = window.open(url, '_blank', 'noopener')
    return win !== null
  } catch {
    return false
  }
}

// ---------- 말에서 프로그램 찾기 (순수 계산) ----------

// 실행해 달라는 말인지
export const LAUNCH_RE = /(실행|켜\s?줘|켜\s?봐|켜\s?주세요|틀어|시작해|띄워|열어|플레이|\brun\b|\bplay\b|\blaunch\b|\bopen\b)/i

const norm = (s) => String(s ?? '').toLowerCase().replace(/[^0-9a-z가-힣]/g, '')

// "배틀그라운드 좀 실행해줘" → "배틀그라운드"
export const launchTarget = (text) => String(text ?? '')
  .replace(/(내\s?(pc|컴퓨터|컴)에서|pc에서|컴퓨터에서)/gi, ' ')
  .replace(/(프로그램|게임|앱|어플)\s*(을|를)?/g, ' ')
  .replace(/(을|를|좀|한번|한 번|빨리|지금|바로)(?=\s|$)/g, ' ')
  .replace(/(실행해\s?줘요?|실행해\s?주세요|실행시켜\s?줘|실행해|실행|켜\s?줘요?|켜\s?봐|켜\s?주세요|틀어\s?줘|시작해\s?줘|시작해|띄워\s?줘|열어\s?줘요?|열어\s?주세요|열어|플레이해\s?줘|플레이|run|play|launch|open)/gi, ' ')
  .replace(/\s+/g, ' ')
  .trim()

/**
 * 이름이 그대로 맞는 프로그램 찾기 (AI 없이 바로 실행할 수 있는 경우)
 * - 똑같거나, 프로그램 이름이 말한 이름을 품고 있을 때(가장 짧은 것). 여러 개가 비슷하게 맞으면 null → AI에게 물어봄
 */
export function matchProgram(text, programs) {
  const want = norm(launchTarget(text))
  if (want.length < 2) return null

  const exact = programs.filter((p) => norm(p.name) === want)
  if (exact.length >= 1) return exact[0]

  const contains = programs.filter((p) => norm(p.name).includes(want)).sort((a, b) => a.name.length - b.name.length)
  if (contains.length === 1) return contains[0]
  if (contains.length > 1 && norm(contains[0].name).length < norm(contains[1].name).length && norm(contains[0].name).startsWith(want)) return contains[0]
  return null
}

/**
 * 말(text)이 프로그램 실행 요청이면 처리
 * @returns {Promise<{ launched?: string, programs?: string[], error?: string }>}
 *   launched: 바로 실행한 프로그램 이름 / programs: 이름이 그대로 맞지 않아 AI에게 같이 보낼 목록 / 둘 다 없으면 상관없는 말
 */
export async function tryLaunch(text) {
  if (!pcPaired() || !LAUNCH_RE.test(text)) return {}
  let list
  try {
    list = await pcPrograms()
  } catch {
    return {}   // 도우미가 꺼져 있으면 평소처럼
  }
  const hit = matchProgram(text, list)
  if (!hit) return { programs: list.map((p) => p.name) }
  try {
    await pcLaunch(hit.id)
    return { launched: hit.name }
  } catch (e) {
    return { error: e.message }
  }
}

// AI가 고른 이름(launch)을 실행 → 실행한 이름 (못 찾으면 예외)
export async function launchByName(name) {
  const list = await pcPrograms()
  const hit = list.find((p) => p.name === name)
  if (!hit) throw new Error('목록에서 찾지 못했어요.')
  await pcLaunch(hit.id)
  return hit.name
}

// ============================================
// 🖱️ 말로 마우스 움직이기 (도우미를 --mouse 로 켰고, 이 브라우저에서도 켰을 때만)
// - 이동 · 클릭 · 스크롤만. 키보드 입력은 없음
// - AI를 거치지 않고 이 화면에서 바로 알아들음 (빨라야 해서) → 정해진 말만 됨
// ============================================
const MOUSE_KEY = 'sklabs.pc.mouse'

export const mouseOn = () => {
  try { return localStorage.getItem(MOUSE_KEY) === '1' } catch { return false }
}
export const setMouseOn = (on) => {
  try { localStorage.setItem(MOUSE_KEY, on ? '1' : '0') } catch { /* 이번 화면에서만 */ }
}

// 도우미가 마우스 기능을 켠 채로(--mouse) 떠 있는지
export const pcMouseReady = () => call('/health').then((j) => j.mouse === true).catch(() => false)

const DIRS = { 오른쪽: [1, 0], 우측: [1, 0], 왼쪽: [-1, 0], 좌측: [-1, 0], 위: [0, -1], 위쪽: [0, -1], 아래: [0, 1], 아래쪽: [0, 1], 밑: [0, 1] }
const PLACES = [
  [/(왼쪽|좌측)\s*(위|상단)/, [50, 50]], [/(오른쪽|우측)\s*(위|상단)/, [950, 50]],
  [/(왼쪽|좌측)\s*(아래|하단|밑)/, [50, 950]], [/(오른쪽|우측)\s*(아래|하단|밑)/, [950, 950]],
  [/(가운데|중앙|한가운데)/, [500, 500]],
]

/**
 * 말 → 마우스 동작 (순수 계산). 마우스 말이 아니면 null
 * @returns {{ action: 'move', dx, dy } | { action: 'to', x, y } | { action: 'click', button, count } | { action: 'scroll', amount } | null}
 */
export function mouseCommand(heard) {
  const text = String(heard ?? '').replace(/[.!?~]+$/g, '').replace(/\s+/g, ' ').trim()
  if (!text || text.length > 30) return null

  // 클릭: "클릭", "더블클릭", "우클릭", "마우스 오른쪽 버튼 클릭" (짧은 말일 때만 — 긴 문장 속의 "클릭"은 질문일 수 있음)
  if (/클릭|click/i.test(text) && text.length <= 16 && !/(어떻게|뭐야|왜|알려|설명|\?)/.test(text)) {
    const right = /(우\s?클릭|오른쪽\s*(버튼)?\s*클릭|right)/i.test(text)
    const twice = /(더블|두\s?번|double)/i.test(text)
    return { action: 'click', button: right ? 'right' : 'left', count: twice ? 2 : 1 }
  }

  // 스크롤: "스크롤 내려", "스크롤 많이 올려"
  if (/스크롤/.test(text)) {
    const up = /(올려|위)/.test(text)
    const down = /(내려|아래|밑)/.test(text)
    if (up === down) return null
    const n = /(많이|쭉|끝까지)/.test(text) ? 10 : /(조금|살짝)/.test(text) ? 1 : 3
    return { action: 'scroll', amount: up ? n : -n }
  }

  // 여기부터는 "마우스"라는 말이 있어야 함 ("오른쪽으로" 같은 말이 다른 뜻으로 쓰일 수 있어서)
  if (!/(마우스|커서|포인터)/.test(text)) return null

  for (const [re, [x, y]] of PLACES) if (re.test(text)) return { action: 'to', x, y }

  // "마우스 오른쪽으로 (조금 | 많이 | 300)"
  const dir = Object.keys(DIRS).sort((a, b) => b.length - a.length).find((k) => new RegExp(`${k}\\s*(으로|로|쪽)?`).test(text.replace(/(마우스|커서|포인터)/, '')))
  if (!dir) return null
  const num = /(\d{1,4})/.exec(text)
  const step = num ? Math.min(2000, Math.max(1, Number(num[1]))) : /(아주\s?조금|살짝)/.test(text) ? 20 : /조금/.test(text) ? 60 : /(아주\s?많이|엄청)/.test(text) ? 800 : /(많이|쭉)/.test(text) ? 400 : 150
  const [mx, my] = DIRS[dir]
  return { action: 'move', dx: mx * step, dy: my * step }
}

const MOUSE_SAY = { move: '마우스를 움직였어요.', to: '마우스를 옮겼어요.', click: '클릭했어요.', scroll: '스크롤했어요.' }

/**
 * 말이 마우스 동작이면 실행
 * @returns {Promise<{ done?: string, error?: string }>} 둘 다 없으면 마우스 말이 아님 (또는 기능이 꺼져 있음)
 */
export async function tryMouse(text) {
  if (!pcPaired() || !mouseOn()) return {}
  const command = mouseCommand(text)
  if (!command) return {}
  try {
    await call('/mouse', { method: 'POST', body: command })
    return { done: MOUSE_SAY[command.action] }
  } catch (e) {
    return { error: e.message }
  }
}
