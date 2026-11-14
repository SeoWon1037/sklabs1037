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
