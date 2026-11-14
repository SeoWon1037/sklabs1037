#!/usr/bin/env node
// ============================================
// 🖥️ SKLabs PC 도우미 (윈도우용, Node.js만 있으면 됨 — 따로 설치하는 것 없음)
//
// 무엇을 하나
//   sklabs1037.com 의 AI 챗봇이 "배틀그라운드 실행해줘" 같은 말을 들었을 때, 이 PC에 설치된 프로그램을 대신 실행해 줌
//   (웹사이트는 보안상 내 PC의 프로그램을 직접 실행할 수 없어서, 이 PC에서 작은 도우미가 대신 실행)
//
// 실행
//   node tools\pc-helper\sklabs-pc-helper.js          ← 켜 두는 동안만 동작 (창을 닫으면 꺼짐)
//   node tools\pc-helper\sklabs-pc-helper.js --pair   ← 연결 화면을 다시 열기
//   node tools\pc-helper\sklabs-pc-helper.js --mouse  ← 🖱️ 말로 마우스 움직이기도 허용 (이 옵션 없이 켜면 마우스 기능은 꺼져 있음)
//
// 안전 장치 (웹사이트가 내 PC의 프로그램을 실행하게 여는 것이므로)
//   1. 이 PC 안에서만 들음 (127.0.0.1) — 인터넷이나 같은 와이파이의 다른 기기에서는 접속할 수 없음
//   2. 허용한 사이트(sklabs1037.com)에서 온 요청만 받음 (Origin · Host 확인)
//   3. 처음 켤 때 만든 연결 표(token)를 아는 브라우저만 — 표는 주소의 # 뒤에 실어 보내서 서버에는 전달되지 않음
//   4. "시작 메뉴에 있는 프로그램"과 "스팀 게임"만 실행함. 사이트가 보내는 것은 목록의 번호(id)뿐이고,
//      명령어 · 파일 경로를 받아서 실행하는 기능은 없음
//   5. 실행은 1초에 한 번까지
//   6. 🌐 사이트 열기는 정해진 사이트(유튜브 · 네이버 · 구글 등)의 https 주소만
//   7. 🖱️ 마우스는 --mouse 로 켰을 때만 (사이트에서는 켤 수 없음). 이동 · 클릭 · 스크롤만 하고 키보드 입력 기능은 없음.
//      사이트가 보내는 것은 숫자(거리 · 위치 · 횟수)뿐이고, 범위를 벗어난 값은 거절
// ============================================
'use strict'

const http = require('http')
const fs = require('fs')
const path = require('path')
const os = require('os')
const crypto = require('crypto')
const { execFile, spawn } = require('child_process')

const VERSION = '1.2.0'
const PORT = 47613
const ALLOWED_ORIGINS = ['https://sklabs1037.com', 'https://www.sklabs1037.com', 'http://localhost:5173']
const SITE = 'https://sklabs1037.com'
const CONFIG_DIR = path.join(process.env.APPDATA || os.homedir(), 'sklabs-pc-helper')
const CONFIG_FILE = path.join(CONFIG_DIR, 'config.json')

// ---------- 순수 함수 (테스트 가능) ----------

/** 허용한 사이트에서 온 요청인지 */
const allowedOrigin = (origin) => typeof origin === 'string' && ALLOWED_ORIGINS.includes(origin)

/** 이 PC 주소로 온 요청인지 (다른 이름으로 속여 들어오는 것을 막음) */
const allowedHost = (host) => host === `127.0.0.1:${PORT}` || host === `localhost:${PORT}`

/** 길이가 달라도 시간 차이로 표를 추측할 수 없게 비교 */
const safeEqual = (a, b) => {
  const x = crypto.createHash('sha256').update(String(a ?? '')).digest()
  const y = crypto.createHash('sha256').update(String(b ?? '')).digest()
  return crypto.timingSafeEqual(x, y)
}

/** 목록의 번호: 종류 + 열쇠로 만든 12글자 (같은 프로그램은 다시 찾아도 같은 번호) */
const makeId = (kind, key) => crypto.createHash('sha1').update(`${kind}:${key}`).digest('hex').slice(0, 12)

/** 시작 메뉴 목록(Get-StartApps의 JSON) → [{ id, kind: 'app', name, key }] */
const startAppsFromJson = (json) => {
  let rows
  try { rows = JSON.parse(String(json).replace(/^\uFEFF/, '')) } catch { return [] }
  if (!Array.isArray(rows)) rows = rows ? [rows] : []
  const out = []
  for (const r of rows) {
    const name = String(r?.Name ?? '').trim()
    const key = String(r?.AppID ?? '').trim()
    if (!name || !key || key.length > 400) continue
    // 설치 제거 · 도움말 · 웹 문서 바로가기는 "프로그램"이 아님
    if (/uninstall|제거|도움말|readme|license|라이선스|website|documentation|release notes/i.test(name)) continue
    if (/^https?:\/\//i.test(key)) continue
    out.push({ id: makeId('app', key), kind: 'app', name, key })
  }
  return out
}

/** 스팀 libraryfolders.vdf → 라이브러리 폴더들 */
const parseVdfPaths = (text) => {
  const out = []
  const re = /"path"\s+"([^"]+)"/g
  let m
  while ((m = re.exec(String(text)))) out.push(m[1].replace(/\\\\/g, '\\'))
  return out
}

/** 스팀 appmanifest_*.acf → { appid, name } (게임이 아닌 도구는 null) */
const parseAcf = (text) => {
  const id = /"appid"\s+"(\d+)"/.exec(String(text))
  const name = /"name"\s+"([^"]+)"/.exec(String(text))
  if (!id || !name) return null
  if (/redistributable|proton|steam linux runtime|steamworks common/i.test(name[1])) return null
  return { appid: id[1], name: name[1].trim() }
}

/** 실행할 명령 (셸을 거치지 않음 → 이름에 이상한 글자가 있어도 명령으로 해석되지 않음) */
const launchCommand = (item) => {
  if (item.kind === 'steam' && /^\d{1,10}$/.test(item.key)) {
    return { cmd: 'rundll32.exe', args: ['url.dll,FileProtocolHandler', `steam://rungameid/${item.key}`] }
  }
  if (item.kind === 'app') return { cmd: 'explorer.exe', args: [`shell:AppsFolder\\${item.key}`] }
  return null
}

/** 같은 이름이 여러 번 나오면 하나만 (스팀 게임이 시작 메뉴에도 있으면 스팀 것을 남김) */
const dedupe = (items) => {
  const seen = new Map()
  for (const it of items) {
    const k = it.name.toLowerCase()
    if (!seen.has(k) || it.kind === 'steam') seen.set(k, it)
  }
  return [...seen.values()].sort((a, b) => a.name.localeCompare(b.name, 'ko'))
}

// ---------- 🌐 사이트 열기 (순수 함수) ----------

// 기본 브라우저로 열어 줄 수 있는 사이트 (사이트의 AiChatRules.SITES와 같은 곳들). 여기 없는 주소는 열지 않음
const OPEN_HOSTS = ['www.youtube.com', 'music.youtube.com', 'www.naver.com', 'search.naver.com', 'www.google.com', 'www.daum.net', 'search.daum.net',
  'map.naver.com', 'map.kakao.com', 'mail.google.com', 'www.netflix.com', 'chzzk.naver.com', 'www.twitch.tv', 'www.instagram.com', 'github.com', 'www.coupang.com']

/** 열어도 되는 주소면 다듬은 주소, 아니면 null (https · 표에 있는 사이트 · 아이디/비밀번호 없음 · 500자까지) */
const safeUrl = (raw) => {
  if (typeof raw !== 'string' || raw.length > 500) return null
  let u
  try { u = new URL(raw) } catch { return null }
  if (u.protocol !== 'https:' || u.username || u.password || u.port) return null
  if (!OPEN_HOSTS.includes(u.hostname)) return null
  return u.href
}

// ---------- 🖱️ 마우스 (순수 함수: 요청 → 마우스 일꾼에게 보낼 한 줄) ----------

const int = (v, min, max) => (Number.isInteger(v) && v >= min && v <= max ? v : null)

/**
 * 요청 → 한 줄 명령 (숫자와 정해진 글자만 들어감). 모르는 동작 · 범위를 벗어난 값이면 null
 *   move   { dx, dy }        → "M dx dy"      지금 자리에서 dx · dy 만큼 (픽셀, ±2000까지)
 *   to     { x, y }          → "A x y"        화면의 위치로 (0~1000, 가로 · 세로를 1000으로 나눈 눈금)
 *   click  { button, count } → "C L|R 1|2"    왼쪽 · 오른쪽, 한 번 · 두 번
 *   scroll { amount }        → "S n"          위(+) · 아래(−) 로 n칸 (±20까지)
 */
const mouseLine = (body) => {
  const b = body && typeof body === 'object' ? body : {}
  if (b.action === 'move') {
    const dx = int(b.dx, -2000, 2000), dy = int(b.dy, -2000, 2000)
    return dx === null || dy === null ? null : `M ${dx} ${dy}`
  }
  if (b.action === 'to') {
    const x = int(b.x, 0, 1000), y = int(b.y, 0, 1000)
    return x === null || y === null ? null : `A ${x} ${y}`
  }
  if (b.action === 'click') {
    const button = b.button === 'right' ? 'R' : b.button === 'left' || b.button === undefined ? 'L' : null
    const count = b.count === undefined ? 1 : int(b.count, 1, 2)
    return button === null || count === null ? null : `C ${button} ${count}`
  }
  if (b.action === 'scroll') {
    const n = int(b.amount, -20, 20)
    return n === null || n === 0 ? null : `S ${n}`
  }
  return null
}

// 마우스 일꾼: 파워셸 하나를 켜 두고 한 줄씩 받아서 움직임 (명령마다 새로 켜면 1초씩 걸려서)
// - 받는 줄은 위 mouseLine이 만든 것뿐. 일꾼은 숫자로 바꿀 수 없는 글자를 무시함
const MOUSE_SCRIPT = `
$ErrorActionPreference = 'SilentlyContinue'
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class SkMouse {
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X; public int Y; }
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern bool GetCursorPos(out POINT p);
  [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, int data, UIntPtr extra);
  [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
}
"@
$w = [SkMouse]::GetSystemMetrics(0)
$h = [SkMouse]::GetSystemMetrics(1)
function Clamp([int]$v, [int]$max) { if ($v -lt 0) { return 0 }; if ($v -gt ($max - 1)) { return ($max - 1) }; return $v }
[Console]::Out.WriteLine('READY')
while ($true) {
  $line = [Console]::In.ReadLine()
  if ($null -eq $line) { break }
  $p = $line.Trim().Split(' ')
  $a = 0; $b = 0
  if ($p.Length -ge 2) { [void][int]::TryParse($p[1], [ref]$a) }
  if ($p.Length -ge 3) { [void][int]::TryParse($p[2], [ref]$b) }
  if ($p[0] -eq 'M') {
    $pt = New-Object SkMouse+POINT
    [void][SkMouse]::GetCursorPos([ref]$pt)
    [void][SkMouse]::SetCursorPos((Clamp ($pt.X + $a) $w), (Clamp ($pt.Y + $b) $h))
  } elseif ($p[0] -eq 'A') {
    [void][SkMouse]::SetCursorPos((Clamp ([int]($w * $a / 1000)) $w), (Clamp ([int]($h * $b / 1000)) $h))
  } elseif ($p[0] -eq 'C') {
    $down = 0x0002; $up = 0x0004
    if ($p[1] -eq 'R') { $down = 0x0008; $up = 0x0010 }
    $n = 1; if ($b -eq 2) { $n = 2 }
    for ($i = 0; $i -lt $n; $i++) {
      [SkMouse]::mouse_event($down, 0, 0, 0, [UIntPtr]::Zero)
      [SkMouse]::mouse_event($up, 0, 0, 0, [UIntPtr]::Zero)
      Start-Sleep -Milliseconds 60
    }
  } elseif ($p[0] -eq 'S') {
    [SkMouse]::mouse_event(0x0800, 0, 0, ($a * 120), [UIntPtr]::Zero)
  }
}
`

let mouseWorker = null
const startMouseWorker = () => {
  // -EncodedCommand: 스크립트를 파일로 쓰지 않고 넘김 (윈도우 파워셸은 UTF-16LE를 base64로 받음)
  const encoded = Buffer.from(MOUSE_SCRIPT, 'utf16le').toString('base64')
  const child = spawn('powershell.exe', ['-NoProfile', '-NoLogo', '-EncodedCommand', encoded], { stdio: ['pipe', 'pipe', 'ignore'], windowsHide: true })
  child.stdout.once('data', () => console.log('  🖱️ 마우스 기능이 준비됐어요.'))
  child.on('exit', () => { if (mouseWorker === child) mouseWorker = null })
  child.on('error', () => { if (mouseWorker === child) mouseWorker = null })
  mouseWorker = child
}

const sendMouse = (line) => {
  if (!mouseWorker || mouseWorker.killed) startMouseWorker()
  mouseWorker.stdin.write(line + '\n')
}

// ---------- 이 PC에서 프로그램 찾기 ----------

const run = (cmd, args) => new Promise((resolve) => {
  execFile(cmd, args, { windowsHide: true, maxBuffer: 16 * 1024 * 1024, encoding: 'utf8' }, (err, stdout) => resolve(err ? '' : stdout))
})

const scanStartApps = async () => {
  const ps = '[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; Get-StartApps | Select-Object Name, AppID | ConvertTo-Json -Compress'
  return startAppsFromJson(await run('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', ps]))
}

const scanSteam = async () => {
  const reg = await run('reg.exe', ['query', 'HKCU\\Software\\Valve\\Steam', '/v', 'SteamPath'])
  const m = /SteamPath\s+REG_SZ\s+(.+)/i.exec(reg)
  if (!m) return []
  const root = m[1].trim().replace(/\//g, '\\')

  const libs = new Set([root])
  try { parseVdfPaths(fs.readFileSync(path.join(root, 'steamapps', 'libraryfolders.vdf'), 'utf8')).forEach((p) => libs.add(p)) } catch { /* 없으면 기본 폴더만 */ }

  const out = []
  for (const lib of libs) {
    let files = []
    try { files = fs.readdirSync(path.join(lib, 'steamapps')) } catch { continue }
    for (const f of files) {
      if (!/^appmanifest_\d+\.acf$/.test(f)) continue
      try {
        const g = parseAcf(fs.readFileSync(path.join(lib, 'steamapps', f), 'utf8'))
        if (g) out.push({ id: makeId('steam', g.appid), kind: 'steam', name: g.name, key: g.appid })
      } catch { /* 읽지 못한 것은 건너뜀 */ }
    }
  }
  return out
}

let programs = []
let scannedAt = 0
const scan = async () => {
  const [apps, steam] = await Promise.all([scanStartApps(), scanSteam()])
  programs = dedupe([...apps, ...steam])
  scannedAt = Date.now()
  console.log(`  프로그램 ${programs.length}개를 찾았어요. (시작 메뉴 ${apps.length} · 스팀 게임 ${steam.length})`)
  return programs
}

// ---------- 연결 표 ----------

const loadToken = () => {
  try {
    const t = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf8')).token
    if (typeof t === 'string' && t.length >= 32) return { token: t, fresh: false }
  } catch { /* 처음 */ }
  const token = crypto.randomBytes(24).toString('base64url')
  fs.mkdirSync(CONFIG_DIR, { recursive: true })
  fs.writeFileSync(CONFIG_FILE, JSON.stringify({ token }), { mode: 0o600 })
  return { token, fresh: true }
}

// ---------- 서버 ----------

const send = (res, status, body, origin) => {
  const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', Vary: 'Origin' }
  if (origin) {
    headers['Access-Control-Allow-Origin'] = origin
    headers['Access-Control-Allow-Headers'] = 'Content-Type, X-SKLabs-Token'
    headers['Access-Control-Allow-Methods'] = 'GET, POST, OPTIONS'
    headers['Access-Control-Allow-Private-Network'] = 'true'
    headers['Access-Control-Max-Age'] = '600'
  }
  res.writeHead(status, headers)
  res.end(body === null ? '' : JSON.stringify(body))
}

const readBody = (req) => new Promise((resolve) => {
  let data = ''
  req.on('data', (c) => { data += c; if (data.length > 4096) req.destroy() })
  req.on('end', () => { try { resolve(JSON.parse(data || '{}')) } catch { resolve({}) } })
  req.on('error', () => resolve({}))
})

// mouse: 🖱️ 마우스 기능을 켰는지 (--mouse). onMouse: 한 줄 명령을 받을 곳 (테스트에서는 가짜)
const openInBrowser = (target) => spawn('rundll32.exe', ['url.dll,FileProtocolHandler', target], { detached: true, stdio: 'ignore' }).unref()

const createServer = (token, { mouse = false, onMouse = sendMouse, onOpen = openInBrowser } = {}) => {
  let lastLaunch = 0
  let mouseCount = 0
  let mouseWindow = 0

  return http.createServer(async (req, res) => {
    const origin = req.headers.origin

    // 허용한 사이트 · 이 PC 주소가 아니면 아무것도 알려 주지 않음
    if (!allowedHost(req.headers.host) || !allowedOrigin(origin)) return send(res, 403, { ok: false, message: 'forbidden' }, null)
    if (req.method === 'OPTIONS') return send(res, 204, null, origin)

    const url = (req.url || '').split('?')[0]

    // 도우미가 켜져 있는지만 (표 없이)
    if (req.method === 'GET' && url === '/health') return send(res, 200, { ok: true, name: 'sklabs-pc-helper', version: VERSION, mouse }, origin)

    if (!safeEqual(req.headers['x-sklabs-token'], token)) return send(res, 401, { ok: false, message: '연결 표가 맞지 않아요. 도우미를 --pair 로 다시 켜서 연결해 주세요.' }, origin)

    if (req.method === 'GET' && url === '/programs') {
      if (Date.now() - scannedAt > 10 * 60 * 1000) await scan()
      return send(res, 200, { ok: true, programs: programs.map(({ id, name, kind }) => ({ id, name, kind })) }, origin)
    }

    if (req.method === 'POST' && url === '/rescan') {
      await scan()
      return send(res, 200, { ok: true, count: programs.length }, origin)
    }

    if (req.method === 'POST' && url === '/launch') {
      const { id } = await readBody(req)
      const item = programs.find((p) => p.id === id)
      const command = item ? launchCommand(item) : null
      if (!command) return send(res, 404, { ok: false, message: '목록에 없는 프로그램이에요.' }, origin)
      if (Date.now() - lastLaunch < 1000) return send(res, 429, { ok: false, message: '너무 빨라요. 잠시 뒤에 다시 해 주세요.' }, origin)
      lastLaunch = Date.now()

      try {
        spawn(command.cmd, command.args, { detached: true, stdio: 'ignore', windowsHide: false }).unref()
        console.log(`  ▶ 실행: ${item.name}`)
        return send(res, 200, { ok: true, name: item.name }, origin)
      } catch (e) {
        return send(res, 500, { ok: false, message: `실행하지 못했어요: ${e.message}` }, origin)
      }
    }

    // 🌐 정해진 사이트를 기본 브라우저로 열기
    if (req.method === 'POST' && url === '/open') {
      const target = safeUrl((await readBody(req)).url)
      if (!target) return send(res, 400, { ok: false, message: '열 수 없는 주소예요.' }, origin)
      if (Date.now() - lastLaunch < 1000) return send(res, 429, { ok: false, message: '너무 빨라요. 잠시 뒤에 다시 해 주세요.' }, origin)
      lastLaunch = Date.now()

      try {
        onOpen(target)
        console.log(`  🌐 열기: ${new URL(target).hostname}`)
        return send(res, 200, { ok: true }, origin)
      } catch (e) {
        return send(res, 500, { ok: false, message: `열지 못했어요: ${e.message}` }, origin)
      }
    }

    // 🖱️ 마우스 (--mouse 로 켰을 때만)
    if (req.method === 'POST' && url === '/mouse') {
      if (!mouse) return send(res, 403, { ok: false, message: '마우스 기능이 꺼져 있어요. 도우미를 --mouse 로 켜 주세요.' }, origin)
      const line = mouseLine(await readBody(req))
      if (!line) return send(res, 400, { ok: false, message: '할 수 없는 마우스 동작이에요.' }, origin)

      // 1초에 20번까지
      const now = Date.now()
      if (now - mouseWindow > 1000) { mouseWindow = now; mouseCount = 0 }
      if (++mouseCount > 20) return send(res, 429, { ok: false, message: '너무 빨라요.' }, origin)

      try {
        onMouse(line)
        return send(res, 200, { ok: true }, origin)
      } catch (e) {
        return send(res, 500, { ok: false, message: `마우스를 움직이지 못했어요: ${e.message}` }, origin)
      }
    }

    return send(res, 404, { ok: false, message: 'not found' }, origin)
  })
}

const main = async () => {
  if (process.platform !== 'win32') {
    console.log('이 도우미는 윈도우에서만 동작해요.')
    process.exit(1)
  }

  const { token, fresh } = loadToken()
  console.log(`SKLabs PC 도우미 ${VERSION}`)
  console.log('  이 PC에 설치된 프로그램을 찾는 중...')
  await scan()

  const mouse = process.argv.includes('--mouse')
  if (mouse) startMouseWorker()
  else console.log('  🖱️ 마우스 기능은 꺼져 있어요. (말로 마우스를 움직이려면 --mouse 를 붙여서 켜세요)')

  const server = createServer(token, { mouse })
  server.on('error', (e) => {
    console.log(e.code === 'EADDRINUSE' ? '  이미 켜져 있어요. (다른 창에서 실행 중)' : `  시작하지 못했어요: ${e.message}`)
    process.exit(1)
  })
  server.listen(PORT, '127.0.0.1', () => {
    console.log(`  켜졌어요. 이 창을 닫으면 꺼져요. (127.0.0.1:${PORT}, 이 PC 안에서만 접속돼요)`)
    // 처음 켰거나 --pair 면 연결 화면을 엶. 표는 # 뒤에 있어서 서버로 전송되지 않음 — 이 주소를 다른 사람에게 보내거나 채팅에 붙여넣지 마세요
    if (fresh || process.argv.includes('--pair')) {
      const link = `${SITE}/pc#pair=${token}`
      console.log('  브라우저에서 연결 화면을 열게요. 안 열리면 도우미를 --pair 로 다시 켜 주세요.')
      spawn('rundll32.exe', ['url.dll,FileProtocolHandler', link], { detached: true, stdio: 'ignore' }).unref()
    }
  })
}

if (require.main === module) main()

module.exports = { safeUrl, mouseLine, MOUSE_SCRIPT, allowedOrigin, allowedHost, safeEqual, makeId, startAppsFromJson, parseVdfPaths, parseAcf, launchCommand, dedupe, createServer, PORT, _setPrograms: (list) => { programs = list; scannedAt = Date.now() } }
