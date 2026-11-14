// ============================================
// 🎄 글자 트리의 "모양" 만들기 (순수 계산 — 화면 그리기는 TreePage.jsx)
// - 트리를 3차원 점 수천 개로 만듦: 점마다 위치 · 면이 향한 방향(밝기 계산용) · 색 · 나타나는 시각
// - 순서: 바늘잎(아래 → 위로 자람) · 줄기 → 금빛 줄 → 방울 → 꼬마전구 → 선물 → 별
// - 같은 씨앗(seed)이면 언제나 같은 트리가 나옴
// - 단위: 땅 = 0, 꼭대기 ≈ 1.25, 가장 넓은 곳의 반지름 ≈ 0.55
// ============================================
const TAU = Math.PI * 2

export const TIMES = { grow: [0.8, 6.0], garland: [6.2, 8.1], baubles: [8.0, 9.2], lights: [9.0, 10.0], gifts: [9.6, 10.4], star: 10.5, end: 11.2 }

// 화면 아래 "진행" 줄에 보여줄 단계 (시작 시각순)
export const STAGES = [
  { at: 0, code: 'needles()', note: '가지 26층 · 아래에서 위로' },
  { at: TIMES.garland[0], code: 'garland()', note: '금빛 줄 감기' },
  { at: TIMES.baubles[0], code: 'baubles()', note: '방울 달기' },
  { at: TIMES.lights[0], code: 'lights()', note: '꼬마전구 켜기' },
  { at: TIMES.gifts[0], code: 'gifts()', note: '선물 놓기' },
  { at: TIMES.star, code: 'star()', note: '별 올리기' },
]

const Y0 = 0.22
const Y1 = 1.17
const TOP = 1.25

// 씨앗으로 만드는 난수 (0~1)
function mulberry32(seed) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

// 높이 y에서 트리의 반지름
export const envelope = (y) => {
  const u = Math.min(1, Math.max(0, (y - Y0) / (Y1 - Y0)))
  return 0.52 * (1 - u) ** 0.9 + 0.03
}

// 높이 y의 잎이 나타나는 시각 (아래가 먼저)
const growTime = (y) => TIMES.grow[0] + (TIMES.grow[1] - TIMES.grow[0]) * Math.min(1, Math.max(0, (y - 0.08) / (TOP - 0.08)))

const unit = (x, y, z) => {
  const n = Math.hypot(x, y, z) || 1
  return [x / n, y / n, z / n]
}

// 구 위에 고르게 놓인 n개의 방향
const fibSphere = (n) => {
  const out = []
  for (let i = 0; i < n; i++) {
    const y = 1 - (2 * (i + 0.5)) / n
    const r = Math.sqrt(1 - y * y)
    const a = i * 2.39996323
    out.push([Math.cos(a) * r, y, Math.sin(a) * r])
  }
  return out
}

/**
 * @returns {Array<{x,y,z,nx,ny,nz,r,g,b,t,glow,phase}>}
 *   glow: 0 보통 · 1 가끔 반짝(금빛 줄) · 2 스스로 빛남(전구) · 3 별(늘 정면), phase: 반짝이는 박자
 */
export function buildTree(seed = 7) {

  const rnd = mulberry32(seed)
  const gauss = () => (rnd() + rnd() + rnd() + rnd() - 2) * 0.87   // 대략 표준정규
  const pts = []
  const tips = []
  // front: 깊이 비교에서 이만큼 앞에 있는 것으로 침 (방울 · 전구가 잎에 묻히지 않게)
  const add = (x, y, z, n, c, t, glow = 0, phase = 0, front = 0) => pts.push({ x, y, z, nx: n[0], ny: n[1], nz: n[2], r: c[0], g: c[1], b: c[2], t, glow, phase, front })

  // 바늘잎: 층마다 가지가 돌려나고, 끝으로 갈수록 살짝 처짐. 아래층이 더 길고 촘촘함
  const LEVELS = 26
  for (let i = 0; i < LEVELS; i++) {
    const u = i / (LEVELS - 1)
    const y = Y0 + 0.02 + u * (Y1 - Y0 - 0.06)
    const R = envelope(y) + 0.05
    const branches = Math.round(6 + 12 * (1 - u))
    for (let b = 0; b < branches; b++) {
      const a = (TAU * (b + 0.5 * rnd())) / branches + i * 0.77
      const len = R * (0.85 + 0.23 * rnd())
      const n = Math.round(22 + 60 * len)
      const ca = Math.cos(a)
      const sa = Math.sin(a)
      for (let k = 0; k < n; k++) {
        const s = rnd() ** 0.65                           // 0 줄기 쪽 … 1 가지 끝
        const side = (0.025 + 0.09 * s) * gauss() * 0.5   // 가지 옆으로 퍼짐
        const px = ca * len * s - sa * side + 0.012 * gauss()
        const pz = sa * len * s + ca * side + 0.012 * gauss()
        const py = y + 0.06 * s - 0.2 * s * s + 0.012 * gauss()
        const normal = unit(ca * 0.75 + 0.3 * gauss(), 0.55 + 0.3 * gauss(), sa * 0.75 + 0.3 * gauss())
        const snowy = s > 0.5 && rnd() < 0.05 && normal[1] > 0.5
        const shade = (0.3 + 0.7 * s ** 1.3) * (0.75 + 0.35 * rnd())   // 안쪽은 어둡게
        const v = rnd()
        const color = snowy ? [0.95, 0.97, 1] : [(0.1 + 0.12 * v) * shade, (0.48 + 0.38 * v) * shade, (0.18 + 0.14 * v) * shade]
        add(px, py, pz, normal, color, growTime(py) + 0.45 * s)
      }
      tips.push([ca * len * 0.95, y + 0.06 * 0.9 - 0.2 * 0.81, sa * len * 0.95])
    }
  }

  // 안쪽 어두운 원뿔: 가지 사이로 뒤가 비쳐 보이지 않게
  for (let i = 0; i < 2600; i++) {
    const y = Y0 + (Y1 - 0.1 - Y0) * rnd()
    const phi = rnd() * TAU
    const r = 0.6 * envelope(y)
    add(Math.cos(phi) * r, y, Math.sin(phi) * r, [Math.cos(phi), 0.3, Math.sin(phi)], [0.07, 0.3, 0.12], growTime(y))
  }

  // 줄기
  for (let i = 0; i < 500; i++) {
    const y = 0.02 + rnd() * (Y0 + 0.08)
    const phi = rnd() * TAU
    const bark = 0.75 + 0.25 * Math.sign(Math.sin(phi * 30 + y * 5))
    add(Math.cos(phi) * 0.045, y, Math.sin(phi) * 0.045, [Math.cos(phi), 0, Math.sin(phi)], [0.42 * bark, 0.26 * bark, 0.14 * bark], growTime(y))
  }

  // 금빛 줄: 트리를 감아 올라가며, 걸린 곳 사이는 살짝 늘어짐
  const TURNS = 3.6
  for (let i = 0; i < 1500; i++) {
    const u = i / 1499
    const y = Y0 + 0.12 + (Y1 - Y0 - 0.34) * u
    const a = TAU * TURNS * u + 0.9
    const swag = -0.045 * Math.abs(Math.sin(a * 2.5))
    const r = envelope(y) + 0.045
    const v = 0.8 + 0.2 * rnd()
    add(Math.cos(a) * r + 0.008 * gauss(), y + swag + 0.008 * gauss(), Math.sin(a) * r + 0.008 * gauss(),
      unit(Math.cos(a), 0.4, Math.sin(a)), [1 * v, 0.78 * v, 0.2 * v], TIMES.garland[0] + (TIMES.garland[1] - TIMES.garland[0]) * u, rnd() < 0.12 ? 1 : 0, rnd() * TAU)
  }

  // 방울: 빨강이 많고 금 · 은 조금
  const ball = fibSphere(70)
  const BALL_COLORS = [[0.9, 0.12, 0.14], [0.9, 0.12, 0.14], [0.98, 0.76, 0.2], [0.85, 0.88, 0.95]]
  for (let k = 0; k < 26; k++) {
    const u = (k + rnd()) / 26
    const y = Y0 + 0.1 + u * (Y1 - Y0 - 0.32)
    const a = k * 2.39996 + rnd() * 0.4
    const r = envelope(y) + 0.03
    const c = [Math.cos(a) * r, y - 0.03, Math.sin(a) * r]
    const rad = 0.036 + 0.026 * (1 - u)
    const color = BALL_COLORS[k % BALL_COLORS.length]
    const t = TIMES.baubles[0] + rnd() * (TIMES.baubles[1] - TIMES.baubles[0])
    for (const d of ball) add(c[0] + d[0] * rad, c[1] + d[1] * rad, c[2] + d[2] * rad, d, color, t, 0, 0, 0.06)
  }

  // 꼬마전구: 가지 끝에서 반짝임 (따뜻한 색이 많음)
  const LIGHT_COLORS = [[1, 0.86, 0.5], [1, 0.86, 0.5], [1, 0.86, 0.5], [0.6, 0.85, 1], [1, 0.5, 0.6]]
  for (let i = 0; i < 170; i++) {
    const tip = tips[Math.floor(rnd() * tips.length)]
    const t = TIMES.lights[0] + rnd() * (TIMES.lights[1] - TIMES.lights[0])
    add(tip[0] * 1.04, tip[1] + 0.01, tip[2] * 1.04, [0, 1, 0], LIGHT_COLORS[i % LIGHT_COLORS.length], t, 2, rnd() * TAU, 0.05)
  }

  // 선물: 상자 + 십자 리본
  const GIFTS = [
    { a: 0.3, d: 0.5, w: 0.2, h: 0.15, body: [0.85, 0.12, 0.14], ribbon: [1, 0.82, 0.25] },
    { a: 2.6, d: 0.48, w: 0.17, h: 0.19, body: [0.98, 0.78, 0.2], ribbon: [0.85, 0.12, 0.14] },
    { a: 4.7, d: 0.52, w: 0.22, h: 0.12, body: [0.16, 0.6, 0.3], ribbon: [0.92, 0.2, 0.2] },
  ]
  GIFTS.forEach((gift, gi) => {
    const cx = Math.cos(gift.a) * gift.d
    const cz = Math.sin(gift.a) * gift.d
    const half = gift.w / 2
    const t = TIMES.gifts[0] + gi * 0.25
    for (let i = 0; i < 520; i++) {
      const face = Math.floor(rnd() * 5)   // 옆 4면 + 윗면
      const p = rnd() - 0.5
      const q = rnd()
      let lx
      let ly
      let lz
      let normal
      if (face === 4) {
        lx = p * gift.w
        lz = (q - 0.5) * gift.w
        ly = gift.h
        normal = [0, 1, 0]
      } else {
        const sign = face < 2 ? 1 : -1
        ly = q * gift.h
        if (face % 2 === 0) {
          lx = sign * half
          lz = p * gift.w
          normal = [sign, 0, 0]
        } else {
          lz = sign * half
          lx = p * gift.w
          normal = [0, 0, sign]
        }
      }
      const onRibbon = Math.abs(lx) < gift.w * 0.09 || Math.abs(lz) < gift.w * 0.09
      // 상자를 줄기 쪽을 보게 돌림
      const ca = Math.cos(gift.a)
      const sa = Math.sin(gift.a)
      add(cx + lx * ca - lz * sa, 0.01 + ly, cz + lx * sa + lz * ca, [normal[0] * ca - normal[2] * sa, normal[1], normal[0] * sa + normal[2] * ca], onRibbon ? gift.ribbon : gift.body, t)
    }
  })

  // 별: 꼭대기의 다섯 꼭지 별 (glow 3 = 트리가 돌아도 늘 정면을 봄)
  const inStar = (x, y) => {
    const sector = TAU / 5
    const ang = Math.atan2(x, y)
    const local = Math.abs((((ang % sector) + sector) % sector) - sector / 2) / (sector / 2)   // 0 꼭지 … 1 골
    return Math.hypot(x, y) <= 0.105 - 0.062 * local ** 0.8
  }
  for (let gx = -12; gx <= 12; gx++) {
    for (let gy = -12; gy <= 12; gy++) {
      const x = gx * 0.009
      const y = gy * 0.009
      if (inStar(x, y)) add(x, Y1 + 0.075 + y, 0, [0, 0, 1], [1, 0.88, 0.3], TIMES.star, 3, Math.hypot(x, y) * 40)
    }
  }

  return pts
}

// 눈송이 n개: 위치(0~1) · 떨어지는 빠르기 · 흔들리는 박자 · 진하기 · 글자
export function buildSnow(n, seed = 99) {
  const rnd = mulberry32(seed)
  return Array.from({ length: n }, () => ({ x: rnd(), y: rnd(), v: 0.03 + rnd() * 0.05, p: rnd() * 6.28, a: (0.25 + rnd() * 0.6).toFixed(2), c: rnd() < 0.3 ? '*' : '.' }))
}
