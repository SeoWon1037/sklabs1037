// 🖥️ PC 도우미 테스트 (node tools/pc-helper/test.js) — 윈도우가 아니어도 돌아감 (실제 실행은 하지 않음)
'use strict'
const assert = require('assert')
const http = require('http')
const h = require('./sklabs-pc-helper.js')

const tests = []
const test = (name, fn) => tests.push([name, fn])

test('허용한 사이트만', () => {
  assert.ok(h.allowedOrigin('https://sklabs1037.com'))
  assert.ok(!h.allowedOrigin('https://evil.example'))
  assert.ok(!h.allowedOrigin('https://sklabs1037.com.evil.example'))
  assert.ok(!h.allowedOrigin(undefined))
  assert.ok(!h.allowedOrigin('null'))
})

test('이 PC 주소만', () => {
  assert.ok(h.allowedHost(`127.0.0.1:${h.PORT}`))
  assert.ok(h.allowedHost(`localhost:${h.PORT}`))
  assert.ok(!h.allowedHost('evil.example'))
  assert.ok(!h.allowedHost(`evil.example:${h.PORT}`))
  assert.ok(!h.allowedHost(undefined))
})

test('연결 표 비교', () => {
  assert.ok(h.safeEqual('abc', 'abc'))
  assert.ok(!h.safeEqual('abc', 'abd'))
  assert.ok(!h.safeEqual(undefined, 'abc'))
  assert.ok(!h.safeEqual('', 'abc'))
})

test('시작 메뉴 목록 읽기', () => {
  const json = JSON.stringify([
    { Name: 'Google Chrome', AppID: 'Chrome' },
    { Name: '메모장', AppID: 'Microsoft.WindowsNotepad_8wekyb3d8bbwe!App' },
    { Name: 'Uninstall Foo', AppID: 'foo.uninstall' },
    { Name: 'Foo Website', AppID: 'https://foo.example' },
    { Name: '', AppID: 'x' },
  ])
  const apps = h.startAppsFromJson(json)
  assert.deepStrictEqual(apps.map((a) => a.name), ['Google Chrome', '메모장'])
  assert.strictEqual(apps[0].kind, 'app')
  assert.strictEqual(apps[0].id.length, 12)
  assert.deepStrictEqual(h.startAppsFromJson('깨진 글'), [])
  assert.strictEqual(h.startAppsFromJson(JSON.stringify({ Name: '하나', AppID: 'one' })).length, 1)   // 한 개면 배열이 아님
  assert.strictEqual(h.startAppsFromJson('\uFEFF[{"Name":"A","AppID":"a"}]').length, 1)
})

test('스팀 폴더 · 게임 읽기', () => {
  const vdf = '"libraryfolders"\n{\n "0"\n {\n  "path"\t\t"C:\\\\Program Files (x86)\\\\Steam"\n }\n "1"\n {\n  "path"\t\t"D:\\\\SteamLibrary"\n }\n}'
  assert.deepStrictEqual(h.parseVdfPaths(vdf), ['C:\\Program Files (x86)\\Steam', 'D:\\SteamLibrary'])

  const acf = '"AppState"\n{\n\t"appid"\t\t"578080"\n\t"name"\t\t"PUBG: BATTLEGROUNDS"\n}'
  assert.deepStrictEqual(h.parseAcf(acf), { appid: '578080', name: 'PUBG: BATTLEGROUNDS' })
  assert.strictEqual(h.parseAcf('"appid" "228980"\n"name" "Steamworks Common Redistributables"'), null)
  assert.strictEqual(h.parseAcf('아무것도 없음'), null)
})

test('실행 명령은 정해진 두 가지뿐', () => {
  assert.deepStrictEqual(h.launchCommand({ kind: 'steam', key: '578080' }), { cmd: 'rundll32.exe', args: ['url.dll,FileProtocolHandler', 'steam://rungameid/578080'] })
  assert.deepStrictEqual(h.launchCommand({ kind: 'app', key: 'Chrome' }), { cmd: 'explorer.exe', args: ['shell:AppsFolder\\Chrome'] })
  assert.strictEqual(h.launchCommand({ kind: 'steam', key: '578080 & calc' }), null)     // 숫자가 아니면 거절
  assert.strictEqual(h.launchCommand({ kind: 'exe', key: 'C:\\x.exe' }), null)            // 모르는 종류는 거절
})

test('같은 이름은 하나만, 스팀 것을 남김', () => {
  const list = h.dedupe([{ id: '1', kind: 'app', name: 'PUBG: BATTLEGROUNDS', key: 'a' }, { id: '2', kind: 'steam', name: 'pubg: battlegrounds', key: '578080' }, { id: '3', kind: 'app', name: '가나다', key: 'b' }])
  assert.strictEqual(list.length, 2)
  assert.ok(list.some((p) => p.kind === 'steam'))
})

// ----- 서버: 다른 사이트 · 표 없는 요청 · 목록에 없는 번호는 거절 -----
const call = (port, path, { method = 'GET', origin, token, host, body } = {}) => new Promise((resolve) => {
  const headers = { Host: host ?? `127.0.0.1:${h.PORT}` }
  if (origin) headers.Origin = origin
  if (token) headers['X-SKLabs-Token'] = token
  if (body) headers['Content-Type'] = 'application/json'
  const req = http.request({ host: '127.0.0.1', port, path, method, headers }, (res) => {
    let data = ''
    res.on('data', (c) => { data += c })
    res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: data ? JSON.parse(data) : null }))
  })
  if (body) req.write(JSON.stringify(body))
  req.end()
})

test('서버 안전 장치', async () => {
  const TOKEN = 'T'.repeat(32)
  h._setPrograms([{ id: 'aaaaaaaaaaaa', kind: 'exe', name: '실행 안 되는 것', key: 'x' }, { id: 'bbbbbbbbbbbb', kind: 'app', name: 'Google Chrome', key: 'Chrome' }])
  const server = h.createServer(TOKEN)
  await new Promise((r) => server.listen(0, '127.0.0.1', r))
  const port = server.address().port
  const ok = 'https://sklabs1037.com'

  try {
    assert.strictEqual((await call(port, '/health', { origin: 'https://evil.example' })).status, 403)           // 다른 사이트
    assert.strictEqual((await call(port, '/health')).status, 403)                                                // Origin 없음
    assert.strictEqual((await call(port, '/health', { origin: ok, host: 'evil.example' })).status, 403)          // 다른 이름으로 접속
    const health = await call(port, '/health', { origin: ok })
    assert.strictEqual(health.status, 200)
    assert.strictEqual(health.headers['access-control-allow-origin'], ok)
    assert.strictEqual(health.headers['access-control-allow-private-network'], 'true')
    assert.strictEqual(health.body.token, undefined)                                                             // 표는 절대 알려 주지 않음

    assert.strictEqual((await call(port, '/programs', { origin: ok })).status, 401)                              // 표 없음
    assert.strictEqual((await call(port, '/programs', { origin: ok, token: 'wrong' })).status, 401)
    const list = await call(port, '/programs', { origin: ok, token: TOKEN })
    assert.strictEqual(list.status, 200)
    assert.deepStrictEqual(Object.keys(list.body.programs[0]).sort(), ['id', 'kind', 'name'])                    // 실행 열쇠(key)는 내보내지 않음

    assert.strictEqual((await call(port, '/launch', { method: 'POST', origin: ok, body: { id: 'bbbbbbbbbbbb' } })).status, 401)
    assert.strictEqual((await call(port, '/launch', { method: 'POST', origin: ok, token: TOKEN, body: { id: 'zzzzzzzzzzzz' } })).status, 404)   // 목록에 없음
    assert.strictEqual((await call(port, '/launch', { method: 'POST', origin: ok, token: TOKEN, body: { id: 'aaaaaaaaaaaa' } })).status, 404)   // 모르는 종류
    assert.strictEqual((await call(port, '/launch', { method: 'POST', origin: ok, token: TOKEN, body: { cmd: 'calc.exe' } })).status, 404)      // 명령어는 받지 않음
    assert.strictEqual((await call(port, '/launch', { method: 'OPTIONS', origin: ok })).status, 204)
    assert.strictEqual((await call(port, '/nope', { origin: ok, token: TOKEN })).status, 404)
  } finally {
    server.close()
  }
})

;(async () => {
  let failed = 0
  for (const [name, fn] of tests) {
    try { await fn(); console.log('  ✓', name) } catch (e) { failed++; console.log('  ✗', name, '\n   ', e.message) }
  }
  console.log(`passed ${tests.length - failed}, failed ${failed}`)
  process.exit(failed ? 1 : 0)
})()
