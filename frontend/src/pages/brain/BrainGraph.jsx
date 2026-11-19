// ============================================
// 🧠 세컨드 브레인 그래프 (canvas)
// - 내 노트가 점(RAW 빨강 · WIKI 노랑 · OUTPUT 파랑), 주제 태그가 허브(보라), 연결이 선
// - 뇌 모양 안에 놓음: 허브는 뇌 안에 고르게, 노트는 첫 번째 주제 허브 근처에 (주제가 없으면 아무 데나)
//   위치는 노트 번호로 정해져서 새로고침해도 같은 자리
// - 노트가 적어도 뇌 모양이 보이게, 뒤에 흐린 장식 점과 선을 깔아 둠 (클릭 안 됨)
// - 마우스를 올리면 제목, 누르면 onSelect(노트 id) / onHub(주제)
// - 움직임 줄이기를 켠 기기에서는 멈춘 그림
// ============================================
import { useEffect, useRef } from 'react'
import { KIND_COLOR } from './brainColors'

const HUB = '#7F77DD'
const BG = '#0d1220'

// 뇌 모양 (가로 · 세로 -1~1): 좌우 반구 두 개 + 가운데 위쪽 틈
function inside(u, v) {
  for (const cx of [-0.42, 0.42]) {
    const dx = (u - cx) / 0.5
    const dy = v / 0.82
    const a = Math.atan2(dy, dx)
    const r = 1 + 0.06 * Math.sin(7 * a + cx * 5) + 0.035 * Math.sin(13 * a)
    if (dx * dx + dy * dy < r * r * 0.92) return !(Math.abs(u) < 0.035 && v < -0.15)
  }
  return false
}

// 번호로 정해지는 난수 (같은 노트는 언제나 같은 자리)
function rng(seed) {
  let s = (Math.abs(Math.floor(seed)) % 2147483646) + 1
  return () => {
    s = (s * 16807) % 2147483647
    return (s - 1) / 2147483646
  }
}

function layout(graph) {
  const R = rng(1037)
  const hubs = new Map()
  let minGap = 0.34
  for (const h of graph.hubs) {
    let placed = null
    for (let tries = 0; tries < 400 && !placed; tries++) {
      const u = R() * 1.7 - 0.85
      const v = R() * 1.4 - 0.7
      if (!inside(u, v)) continue
      if ([...hubs.values()].every((p) => Math.hypot(p.u - u, p.v - v) > minGap)) placed = { u, v }
      if (tries === 399) minGap *= 0.8
    }
    hubs.set(h.id, { ...h, ...(placed ?? { u: R() * 0.6 - 0.3, v: R() * 0.6 - 0.3 }) })
  }

  const nodes = new Map()
  for (const n of graph.nodes) {
    const r = rng(n.id * 7919 + 13)
    const hub = n.topics.map((t) => hubs.get(`t:${t}`)).find(Boolean)
    let p = null
    for (let tries = 0; tries < 60 && !p; tries++) {
      const u = hub ? hub.u + (r() - 0.5) * 0.5 : r() * 1.8 - 0.9
      const v = hub ? hub.v + (r() - 0.5) * 0.5 : r() * 1.6 - 0.8
      if (inside(u, v)) p = { u, v }
    }
    nodes.set(n.id, { ...n, ...(p ?? { u: 0, v: 0 }), ph: r() * 6.28 })
  }

  // 장식: 흐린 점 + 가까운 점끼리 선
  const D = rng(42)
  const decor = []
  while (decor.length < 420) {
    const u = D() * 2 - 1
    const v = D() * 1.8 - 0.9
    if (inside(u, v)) decor.push({ u, v })
  }
  const decorEdges = []
  decor.forEach((a, i) => {
    const near = decor.map((b, j) => [(a.u - b.u) ** 2 + (a.v - b.v) ** 2, j]).filter(([, j]) => j !== i).sort((x, y) => x[0] - y[0]).slice(0, 3)
    for (const [, j] of near) if (i < j) decorEdges.push([i, j])
  })

  return { hubs, nodes, decor, decorEdges }
}

function BrainGraph({ graph, selectedId, onSelect, onHub }) {
  const canvas = useRef(null)
  const state = useRef({ hover: null, pulses: [] })

  useEffect(() => {
    const cv = canvas.current
    if (!cv || !graph) return undefined
    const ctx = cv.getContext('2d')
    const L = layout(graph)
    const still = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    let W = 0
    let H = 0
    let t = 0
    let frame = 0

    const size = () => {
      const dpr = window.devicePixelRatio || 1
      W = cv.clientWidth
      H = Math.max(280, Math.min(520, Math.round(W * 0.72)))
      cv.style.height = `${H}px`
      cv.width = W * dpr
      cv.height = H * dpr
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    }
    const sx = (u) => W / 2 + u * W * 0.44
    const sy = (v) => H / 2 + v * H * 0.52
    const drift = (p, k = 1.2) => (still ? [sx(p.u), sy(p.v)] : [sx(p.u) + Math.sin(t * 0.7 + (p.ph ?? 0)) * k, sy(p.v) + Math.cos(t * 0.6 + (p.ph ?? 0)) * k])
    const posOf = (id) => {
      const n = L.nodes.get(id)
      if (n) return drift(n)
      const h = L.hubs.get(id)
      return h ? [sx(h.u), sy(h.v)] : null
    }
    const linkEdges = graph.edges.filter((e) => e.type === 'link')

    const draw = () => {
      ctx.fillStyle = BG
      ctx.fillRect(0, 0, W, H)

      // 장식
      ctx.globalAlpha = 0.09
      ctx.strokeStyle = '#8fa3d6'
      ctx.lineWidth = 0.6
      ctx.beginPath()
      for (const [i, j] of L.decorEdges) {
        ctx.moveTo(sx(L.decor[i].u), sy(L.decor[i].v))
        ctx.lineTo(sx(L.decor[j].u), sy(L.decor[j].v))
      }
      ctx.stroke()
      ctx.globalAlpha = 0.22
      ctx.fillStyle = '#8fa3d6'
      for (const d of L.decor) ctx.fillRect(sx(d.u) - 0.7, sy(d.v) - 0.7, 1.4, 1.4)

      const hover = state.current.hover
      const focus = hover?.type === 'node' ? hover.id : selectedId

      // 주제 선 (노트 → 허브)
      ctx.lineWidth = 0.8
      for (const e of graph.edges) {
        if (e.type !== 'topic') continue
        const a = posOf(e.a)
        const b = posOf(e.b)
        if (!a || !b) continue
        const n = L.nodes.get(e.a)
        ctx.globalAlpha = focus === e.a ? 0.9 : 0.32
        ctx.strokeStyle = KIND_COLOR[n.kind] ?? HUB
        ctx.beginPath()
        ctx.moveTo(a[0], a[1])
        ctx.lineTo(b[0], b[1])
        ctx.stroke()
      }

      // 노트끼리 연결 (정리 문서 → 출처 메모)
      ctx.lineWidth = 1.1
      ctx.strokeStyle = '#ffffff'
      for (const e of linkEdges) {
        const a = posOf(e.a)
        const b = posOf(e.b)
        if (!a || !b) continue
        ctx.globalAlpha = focus === e.a || focus === e.b ? 0.95 : 0.35
        ctx.beginPath()
        ctx.moveTo(a[0], a[1])
        ctx.lineTo(b[0], b[1])
        ctx.stroke()
      }

      // 연결선을 따라 흐르는 빛
      if (!still) {
        const ps = state.current.pulses
        if (linkEdges.length && Math.random() < 0.12 && ps.length < 24) ps.push({ e: linkEdges[Math.floor(Math.random() * linkEdges.length)], k: 0 })
        for (let i = ps.length - 1; i >= 0; i--) {
          const q = ps[i]
          q.k += 0.015
          const a = posOf(q.e.a)
          const b = posOf(q.e.b)
          if (q.k >= 1 || !a || !b) {
            ps.splice(i, 1)
            continue
          }
          ctx.globalAlpha = 1 - q.k * 0.6
          ctx.fillStyle = '#ffffff'
          ctx.beginPath()
          ctx.arc(b[0] + (a[0] - b[0]) * q.k, b[1] + (a[1] - b[1]) * q.k, 1.8, 0, 6.283)
          ctx.fill()
        }
      }

      // 노트
      for (const n of L.nodes.values()) {
        const [x, y] = drift(n)
        const r = n.kind === 'WIKI' ? 5 : 3.6
        ctx.globalAlpha = 1
        ctx.fillStyle = KIND_COLOR[n.kind] ?? '#ffffff'
        ctx.beginPath()
        ctx.arc(x, y, focus === n.id ? r + 2 : r, 0, 6.283)
        ctx.fill()
        if (n.id === selectedId) {
          ctx.lineWidth = 2
          ctx.strokeStyle = '#ffffff'
          ctx.stroke()
        }
      }

      // 허브 + 이름
      ctx.font = '12px system-ui, -apple-system, "Malgun Gothic", sans-serif'
      ctx.textAlign = 'center'
      for (const h of L.hubs.values()) {
        const x = sx(h.u)
        const y = sy(h.v)
        const r = 6 + Math.min(h.count, 12) * 0.7
        ctx.globalAlpha = 1
        ctx.fillStyle = HUB
        ctx.beginPath()
        ctx.arc(x, y, r, 0, 6.283)
        ctx.fill()
        ctx.lineWidth = hover?.type === 'hub' && hover.id === h.id ? 3 : 1.6
        ctx.strokeStyle = '#ffffff'
        ctx.stroke()
        ctx.fillStyle = '#d9d6fb'
        ctx.fillText(`#${h.label}`, x, y + r + 14)
      }

      // 올린 것의 제목
      if (hover) {
        const label = hover.type === 'hub' ? `#${hover.label} · 노트 ${hover.count}개` : hover.title
        const text = label.length > 32 ? `${label.slice(0, 32)}…` : label
        ctx.font = '13px system-ui, -apple-system, "Malgun Gothic", sans-serif'
        const w = ctx.measureText(text).width + 16
        const x = Math.min(W - w - 6, Math.max(6, hover.x + 12))
        const y = Math.max(6, hover.y - 34)
        ctx.globalAlpha = 0.95
        ctx.fillStyle = '#1b2440'
        ctx.beginPath()
        ctx.roundRect?.(x, y, w, 26, 6)
        if (!ctx.roundRect) ctx.rect(x, y, w, 26)
        ctx.fill()
        ctx.globalAlpha = 1
        ctx.fillStyle = '#ffffff'
        ctx.textAlign = 'left'
        ctx.fillText(text, x + 8, y + 17)
      }
      ctx.globalAlpha = 1
    }

    const loop = () => {
      t += 0.016
      draw()
      if (!still) frame = requestAnimationFrame(loop)
    }

    // 마우스 · 손가락 위치에서 가장 가까운 노트 · 허브
    const pick = (ev) => {
      const r = cv.getBoundingClientRect()
      const mx = ev.clientX - r.left
      const my = ev.clientY - r.top
      let best = null
      let bd = 16 * 16
      for (const h of L.hubs.values()) {
        const d = (sx(h.u) - mx) ** 2 + (sy(h.v) - my) ** 2
        if (d < bd) { bd = d; best = { type: 'hub', id: h.id, label: h.label, count: h.count, x: mx, y: my } }
      }
      for (const n of L.nodes.values()) {
        const [x, y] = drift(n)
        const d = (x - mx) ** 2 + (y - my) ** 2
        if (d < bd) { bd = d; best = { type: 'node', id: n.id, title: n.title, x: mx, y: my } }
      }
      return best
    }
    const onMove = (ev) => {
      state.current.hover = pick(ev)
      cv.style.cursor = state.current.hover ? 'pointer' : 'default'
      if (still) draw()
    }
    const onLeave = () => {
      state.current.hover = null
      if (still) draw()
    }
    const onClick = (ev) => {
      const p = pick(ev)
      if (p?.type === 'node') onSelect?.(p.id)
      if (p?.type === 'hub') onHub?.(p.label)
    }

    cv.addEventListener('mousemove', onMove)
    cv.addEventListener('mouseleave', onLeave)
    cv.addEventListener('click', onClick)
    const ro = new ResizeObserver(() => { size(); draw() })
    ro.observe(cv)
    size()
    loop()

    return () => {
      cancelAnimationFrame(frame)
      ro.disconnect()
      cv.removeEventListener('mousemove', onMove)
      cv.removeEventListener('mouseleave', onLeave)
      cv.removeEventListener('click', onClick)
    }
  }, [graph, selectedId, onSelect, onHub])

  return <canvas ref={canvas} className="brain-canvas" role="img" aria-label={`내 노트 ${graph?.nodes.length ?? 0}개와 주제 ${graph?.hubs.length ?? 0}개를 이은 그래프`} />
}

export default BrainGraph
