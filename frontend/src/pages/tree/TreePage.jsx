// ============================================
// 🎄 글자 트리 (주소: /tree, 로그인 없이 누구나)
// - 글자로 그린 3차원 크리스마스 트리가 아래에서 위로 자라고, 돌면서 금빛 줄 · 방울 · 전구 · 선물 · 별이 차례로 붙음
// - 그리는 법: 트리의 점들(treeModel.js)을 돌려서 글자 칸(가로 84 × 세로 64)에 떨어뜨리고,
//   칸마다 가장 앞에 있는 점만 남겨(깊이 비교) 밝기에 맞는 글자와 색으로 찍음
// - /tree?to=이름 → "To. 이름"이 같이 보임 (링크로 보내는 카드). 이름은 20자까지, 글자로만 표시
// - 움직임 줄이기를 켠 기기에서는 다 자란 트리를 멈춘 채로 시작
// ============================================
import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { saveCanvas } from '../../components/me/shareImage'
import { STAGES, TIMES, buildSnow, buildTree } from './treeModel'
import './TreePage.css'

const COLS = 84
const ROWS = 64
const RAMP = '.,:;~=+*xoaXA#%@'          // 어두움 → 밝음
const SNOW = 150
const BG = '#070b14'

// 한 장 그리기 (time: 시작한 뒤 지난 초)
function draw(ctx, W, H, points, flakes, time) {

  const cw = W / COLS
  const ch = H / ROWS
  const scale = W * 0.74                      // 트리 길이 1 = 이만큼의 픽셀
  const baseY = H * 0.9
  const angle = time * 0.42
  const ca = Math.cos(angle)
  const sa = Math.sin(angle)
  const TILT = 0.22                           // 살짝 위에서 내려다봄

  const depth = new Float32Array(COLS * ROWS).fill(-1e9)
  const cell = new Int32Array(COLS * ROWS).fill(-1)
  const light = new Float32Array(COLS * ROWS)

  let shown = 0
  for (let i = 0; i < points.length; i++) {
    const p = points[i]
    if (time < p.t) continue
    shown++
    const star = p.glow === 3                 // 별은 돌지 않고 늘 정면
    const x = star ? p.x : p.x * ca + p.z * sa
    const z = star ? 0.2 : -p.x * sa + p.z * ca
    const col = Math.floor((W / 2 + x * scale) / cw)
    const row = Math.floor((baseY - p.y * scale - (star ? 0 : z) * TILT * scale) / ch)
    if (col < 0 || col >= COLS || row < 0 || row >= ROWS) continue
    const k = row * COLS + col
    const d = z + p.front
    if (d <= depth[k]) continue
    depth[k] = d
    cell[k] = i
    // 빛: 왼쪽 위 앞에서 비춤 (면의 방향도 같이 돌림)
    const nx = p.nx * ca + p.nz * sa
    const nz = -p.nx * sa + p.nz * ca
    light[k] = Math.max(0, nx * -0.45 + p.ny * 0.6 + nz * 0.66)
  }

  ctx.fillStyle = BG
  ctx.fillRect(0, 0, W, H)
  ctx.font = `700 ${Math.round(ch * 0.98)}px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'

  // 눈: 빈칸에만
  for (let i = 0; i < flakes.length; i++) {
    const f = flakes[i]
    const fy = (f.y + time * f.v) % 1
    const fx = (f.x + Math.sin(time * 0.6 + f.p) * 0.012 + 1) % 1
    const col = Math.floor(fx * COLS)
    const row = Math.floor(fy * ROWS)
    if (cell[row * COLS + col] >= 0) continue
    ctx.fillStyle = `rgba(226,236,255,${f.a})`
    ctx.fillText(f.c, (col + 0.5) * cw, (row + 0.5) * ch)
  }

  for (let k = 0; k < cell.length; k++) {
    const i = cell[k]
    if (i < 0) continue
    const p = points[i]
    const age = time - p.t
    let r = p.r
    let g = p.g
    let b = p.b
    let level
    if (p.glow >= 2) {                                    // 전구 · 별: 스스로 반짝임 (별은 가운데에서 바깥으로 물결)
      level = p.glow === 3 ? 0.75 + 0.25 * Math.sin(time * 3 - p.phase) : 0.55 + 0.45 * Math.sin(time * 4 + p.phase)
    } else {
      level = 0.46 + 0.54 * light[k]
      if (p.glow === 1) level = Math.min(1, level + 0.35 * (0.5 + 0.5 * Math.sin(time * 5 + p.phase)))
    }
    if (age < 0.3) {                                      // 막 생긴 점은 하얗게 (자라는 자리가 보이게)
      const w = 1 - age / 0.3
      r += (1 - r) * w
      g += (1 - g) * w
      b += (1 - b) * w
      level = Math.max(level, 0.55 + 0.45 * w)
    }
    const m = 0.5 + 0.5 * level
    ctx.fillStyle = `rgb(${Math.round(255 * Math.min(1, r * m * 1.4))},${Math.round(255 * Math.min(1, g * m * 1.4))},${Math.round(255 * Math.min(1, b * m * 1.4))})`
    ctx.fillText(RAMP[Math.min(RAMP.length - 1, Math.floor(level * RAMP.length))], ((k % COLS) + 0.5) * cw, (Math.floor(k / COLS) + 0.5) * ch)
  }

  return { shown, degrees: ((angle * 180) / Math.PI) % 360 }
}

function TreePage() {

  const [params, setParams] = useSearchParams()
  const to = (params.get('to') ?? '').slice(0, 20)
  const [name, setName] = useState(to)
  // 움직임 줄이기를 켠 기기: 다 자란 모습으로 멈춘 채 시작
  const [reduced] = useState(() => Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches))
  const [paused, setPaused] = useState(reduced)
  const [stage, setStage] = useState(0)
  const [info, setInfo] = useState({ shown: 0, degrees: 0 })
  const [toast, setToast] = useState('')

  const canvasRef = useRef(null)
  const clock = useRef({ time: reduced ? TIMES.end : 0, last: 0 })
  const pausedRef = useRef(reduced)

  const points = useMemo(() => buildTree(7), [])
  const flakes = useMemo(() => buildSnow(SNOW), [])

  useEffect(() => {
    const canvas = canvasRef.current
    const ctx = canvas.getContext('2d')
    let raf = 0
    let lastInfo = 0

    const fit = () => {
      const dpr = Math.min(2, window.devicePixelRatio || 1)
      const width = canvas.clientWidth
      canvas.width = Math.round(width * dpr)
      canvas.height = Math.round(((width * dpr) / COLS) * 1.72 * ROWS)
    }
    fit()
    window.addEventListener('resize', fit)

    const frame = (now) => {
      const c = clock.current
      const dt = c.last ? Math.min(0.1, (now - c.last) / 1000) : 0
      c.last = now
      if (!pausedRef.current) c.time += dt
      const result = draw(ctx, canvas.width, canvas.height, points, flakes, c.time)
      if (now - lastInfo > 200) {   // 아래 숫자는 1초에 5번만 고침
        lastInfo = now
        setInfo(result)
        let s = 0
        for (let i = 0; i < STAGES.length; i++) if (c.time >= STAGES[i].at) s = i
        setStage(c.time >= TIMES.end ? STAGES.length : s)
      }
      raf = requestAnimationFrame(frame)
    }
    raf = requestAnimationFrame(frame)

    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('resize', fit)
    }
  }, [points, flakes])

  const togglePause = () => {
    pausedRef.current = !pausedRef.current
    setPaused(pausedRef.current)
  }
  const replay = () => {
    clock.current.time = 0
    pausedRef.current = false
    setPaused(false)
  }

  const flash = (msg) => {
    setToast(msg)
    setTimeout(() => setToast(''), 1800)
  }
  const link = () => `${window.location.origin}/tree${name.trim() ? `?to=${encodeURIComponent(name.trim())}` : ''}`
  const applyName = (e) => {
    e.preventDefault()
    setParams(name.trim() ? { to: name.trim() } : {}, { replace: true })
  }
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link())
      flash('링크를 복사했어요')
    } catch {
      flash('복사하지 못했어요. 주소창의 주소를 복사해주세요.')
    }
  }
  const save = () => saveCanvas(canvasRef.current, 'sklabs-tree.png', `🎄 ${to ? `To. ${to} ` : ''}Merry Christmas ${link()}`)

  return (
    <main className="tree-page">
      <header className="tree-head">
        <h1>🎄 글자 트리</h1>
        {to ? <p className="tree-to">To. <b>{to}</b> — Merry Christmas</p> : <p>글자로 그린 트리가 아래에서부터 자라나요. 이름을 넣어 링크로 보내보세요.</p>}
      </header>

      <div className="tree-stage">
        <canvas ref={canvasRef} className="tree-canvas" role="img" aria-label="글자로 그린 크리스마스 트리가 돌면서 자라는 모습" />
        <p className="tree-info" aria-hidden="true">rotation {info.degrees.toFixed(1).padStart(5, '0')}° · letters {info.shown.toLocaleString('ko-KR')}</p>
      </div>

      <div className="tree-actions">
        <button type="button" className="tree-btn" onClick={replay}>↻ 처음부터</button>
        <button type="button" className="tree-btn" onClick={togglePause} aria-pressed={paused}>{paused ? '▶ 다시 움직이기' : '⏸ 멈추기'}</button>
        <button type="button" className="tree-btn tree-btn-primary" onClick={save}>🖼️ 이미지 저장</button>
      </div>

      <ol className="tree-code" aria-label="트리가 만들어지는 순서">
        <li className="tree-code-title"><span>tree.js</span><span>{stage >= STAGES.length ? '● done' : '● running'}</span></li>
        {STAGES.map((s, i) => (
          <li key={s.code} className={i === stage ? 'tree-code-on' : i < stage ? 'tree-code-done' : ''}>
            <span>{String(i + 1).padStart(2, '0')}</span><code>{s.code}</code><em>{'// '}{s.note}</em>
          </li>
        ))}
      </ol>

      <form className="tree-send" onSubmit={applyName}>
        <label htmlFor="tree-name">받는 사람 이름 (선택)</label>
        <div>
          <input id="tree-name" value={name} onChange={(e) => setName(e.target.value.slice(0, 20))} placeholder="예: 민지" maxLength={20} />
          <button type="submit" className="tree-btn">넣기</button>
          <button type="button" className="tree-btn" onClick={copy}>🔗 링크 복사</button>
        </div>
        {toast && <p className="tree-toast" role="status">{toast}</p>}
      </form>
    </main>
  )
}

export default TreePage
