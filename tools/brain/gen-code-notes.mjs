#!/usr/bin/env node
// ============================================
// 🧠 세컨드 브레인용 "지금 스프링 코드" 노트 만들기
// - backend/src/main/java/.../<패키지>/**/*.java 를 읽어서 패키지마다 노트 하나
//   → backend/src/main/resources/brain/notes/code-<패키지>.md (+ code-overview.md)
// - 클래스마다: 종류(Controller · Service · Rules …) · 맨 위 설명 주석 · API 주소 · 공개 메서드
//   패키지가 쓰는 DB 표: 그 패키지 DAO의 mapper XML 에서 T_○○ 이름
// - 서버가 켜질 때 BrainImportService가 이 파일들을 관리자 브레인에 넣음 (안 고친 노트만 새 내용으로)
// - 실행: node tools/brain/gen-code-notes.mjs   (번들을 만들 때마다)
// ============================================
import { readdirSync, readFileSync, writeFileSync, mkdirSync, statSync, existsSync, rmSync } from 'node:fs'
import { join, relative } from 'node:path'

const ROOT = new URL('../../', import.meta.url).pathname
const SRC = join(ROOT, 'backend/src/main/java/com/sklabs1037/sklabs1037')
const MAPPERS = join(ROOT, 'backend/src/main/resources/mapper')
const OUT = join(ROOT, 'backend/src/main/resources/brain/notes')
const MAX_BODY = 19000

const walk = (dir) => readdirSync(dir).flatMap((f) => {
  const p = join(dir, f)
  return statSync(p).isDirectory() ? walk(p) : p.endsWith('.java') ? [p] : []
})

// 맨 위 설명 주석 (클래스 선언 바로 앞의 /** … */)
function headerDoc(src, at) {
  const before = src.slice(0, at)
  const end = before.lastIndexOf('*/')
  const start = before.lastIndexOf('/**')
  if (start < 0 || end < start) return []
  // 주석과 클래스 사이에 어노테이션만 있어야 그 클래스 설명
  const gap = before.slice(end + 2).replace(/@\w+(\([^)]*\))?/g, '').trim()
  if (gap) return []
  return before.slice(start + 3, end).split('\n')
    .map((l) => l.replace(/^\s*\*\s?/, '').trimEnd())
    .filter((l, i, a) => l.trim() || (i > 0 && a[i - 1].trim()))
}

function kindOf(src, decl) {
  if (/@RestController|@Controller\b/.test(src)) return 'Controller'
  if (/@Mapper\b/.test(src)) return 'DAO (MyBatis)'
  if (/@Service\b/.test(src)) return 'Service'
  if (/@Configuration\b/.test(src)) return 'Config'
  if (/@Component\b/.test(src)) return 'Component'
  if (/\brecord\b/.test(decl)) return 'record'
  if (/\benum\b/.test(decl)) return 'enum'
  if (/\binterface\b/.test(decl)) return 'interface'
  if (/final class \w+Rules\b|class \w+Rules\b/.test(decl)) return 'Rules (순수 계산)'
  return 'class'
}

const MAP = { GetMapping: 'GET', PostMapping: 'POST', PutMapping: 'PUT', DeleteMapping: 'DELETE', PatchMapping: 'PATCH' }
const firstPath = (args) => (args?.match(/"([^"]*)"/) ?? [])[1] ?? ''

function endpoints(src) {
  const cls = src.match(/@RequestMapping\(\s*(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"/)
  const base = cls ? cls[1] : ''
  const out = []
  const re = /@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)(\(([^)]*)\))?\s+(?:@\w+\s+)*public\s+[\w<>,?\s[\]]+?\s+(\w+)\s*\(/g
  let m
  while ((m = re.exec(src))) out.push(`${MAP[m[1]]} ${base}${firstPath(m[3])} → ${m[4]}()`)
  return out
}

function publicMethods(src, name) {
  const re = /\n\s{4}public\s+(?:static\s+)?(?:final\s+)?(?:synchronized\s+)?([\w<>,?\s[\].]+?)\s+(\w+)\s*\(([^)]*)\)/g
  const out = []
  let m
  while ((m = re.exec(src))) {
    if (m[2] === name) continue
    const params = m[3].split(',').map((p) => p.trim().replace(/@\w+(\([^)]*\))?\s*/g, '').split(/\s+/).pop()).filter(Boolean).join(', ')
    const line = `${m[2]}(${params})`
    if (!out.includes(line)) out.push(line)
  }
  return out
}

function daoMethods(src) {
  const re = /\n\s{4}(?:[\w<>,?\s[\].]+?)\s+(\w+)\s*\([^;{]*\);/g
  const out = []
  let m
  while ((m = re.exec(src))) if (!out.includes(m[1])) out.push(m[1])
  return out
}

function tablesFor(daoFqns) {
  if (!existsSync(MAPPERS)) return []
  const set = new Set()
  for (const f of readdirSync(MAPPERS)) {
    const x = readFileSync(join(MAPPERS, f), 'utf8')
    const ns = (x.match(/namespace="([^"]+)"/) ?? [])[1]
    if (!ns || !daoFqns.includes(ns)) continue
    for (const t of x.matchAll(/\bT_[A-Z0-9_]+\b/g)) set.add(t[0])
  }
  return [...set].sort()
}

const clip = (s) => (s.length > MAX_BODY ? `${s.slice(0, MAX_BODY - 40).replace(/\n[^\n]*$/, '')}\n\n(길어서 여기까지)` : s)

const pkgs = readdirSync(SRC).filter((d) => statSync(join(SRC, d)).isDirectory()).sort()
mkdirSync(OUT, { recursive: true })
for (const f of readdirSync(OUT)) if (f.startsWith('code-')) rmSync(join(OUT, f))

const overview = []
for (const pkg of pkgs) {
  const files = walk(join(SRC, pkg)).sort()
  const classes = []
  const daoFqns = []
  for (const file of files) {
    const src = readFileSync(file, 'utf8')
    const decl = src.match(/\n(public\s+)?(?:abstract\s+|final\s+)*(class|interface|record|enum)\s+(\w+)/)
    if (!decl) continue
    const name = decl[3]
    const kind = kindOf(src, decl[0])
    if (kind === 'DAO (MyBatis)') daoFqns.push(`${(src.match(/package\s+([\w.]+);/) ?? [])[1]}.${name}`)
    classes.push({
      name, kind, path: relative(ROOT, file),
      doc: headerDoc(src, decl.index + 1).slice(0, 8),
      api: kind === 'Controller' ? endpoints(src) : [],
      methods: kind === 'DAO (MyBatis)' ? daoMethods(src) : (kind === 'Service' || kind.startsWith('Rules')) ? publicMethods(src, name).slice(0, 14) : [],
    })
  }
  if (classes.length === 0) continue

  const tables = tablesFor(daoFqns)
  const main = classes.find((c) => c.kind === 'Controller') ?? classes.find((c) => c.kind === 'Service') ?? classes[0]
  const summary = main.doc.find((l) => l.trim()) ?? `${pkg} 패키지`
  const api = classes.flatMap((c) => c.api)

  const lines = [`## 요약`, `${summary.trim()}`, '', `클래스 ${classes.length}개${api.length ? ` · API ${api.length}개` : ''}${tables.length ? ` · DB 표 ${tables.length}개` : ''} · 위치 \`backend/src/main/java/com/sklabs1037/sklabs1037/${pkg}\``]
  if (api.length) lines.push('', '## API', ...api.map((a) => `- ${a}`))
  if (tables.length) lines.push('', '## DB 표', `- ${tables.join(', ')}`)
  lines.push('', '## 클래스')
  for (const c of classes) {
    lines.push('', `### ${c.name} · ${c.kind}`, `- 파일: ${c.path}`)
    const doc = c.doc.filter((l) => l.trim()).filter((l) => !/^\s*(GET|POST|PUT|DELETE)\s+\//.test(l) && !/^API 목록/.test(l.trim()))
    for (const l of doc.slice(0, 5)) lines.push(`- ${l.trim().replace(/^[-·]\s+/, '')}`)
    if (c.methods.length) lines.push(`- 메서드: ${c.methods.join(' · ')}`)
  }

  const title = `Spring 코드 · ${pkg} 패키지`
  const text = `key: code-${pkg}\ntitle: ${title}\ntopics: spring, ${pkg}\n---\n${clip(lines.join('\n'))}\n`
  writeFileSync(join(OUT, `code-${pkg}.md`), text)
  overview.push(`- **${pkg}** — ${summary.trim().slice(0, 80)} (클래스 ${classes.length}${api.length ? ` · API ${api.length}` : ''})`)
}

const ov = [
  '## 요약',
  'SKLabs 백엔드(Spring Boot 4 · Java 17 · MyBatis · PostgreSQL)의 패키지 지도. 패키지마다 "Spring 코드 · ○○ 패키지" 노트가 따로 있어요.',
  '',
  '## 구조 규칙',
  '- controller → service → dao(MyBatis XML, 결과는 LinkedHashMap · AS "camelCase")',
  '- ○○Rules: 순수 계산(스프링 없이 테스트) / ○○Service: DB · 외부 API를 묶음',
  '- 설계 원칙: AI는 정리, 판단은 서버 규칙 · 되돌릴 수 없는 일은 사람 승인',
  '- DB 바꾸기: resources/db/auto/날짜-순번-설명.sql (IF NOT EXISTS, 서버가 켜질 때 한 번 실행) + ops/service/SchemaExpect 등록',
  '',
  '## 패키지',
  ...overview,
].join('\n')
writeFileSync(join(OUT, 'code-overview.md'), `key: code-overview\ntitle: Spring 코드 · 전체 구조\ntopics: spring, 구조\nlinks: ${pkgs.filter((p) => existsSync(join(OUT, `code-${p}.md`))).map((p) => `code-${p}`).join(', ')}\n---\n${clip(ov)}\n`)

console.log(`노트 ${overview.length + 1}개 → ${relative(ROOT, OUT)}`)
