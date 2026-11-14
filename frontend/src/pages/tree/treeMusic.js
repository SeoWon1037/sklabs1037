// ============================================
// 🎵 트리 화면의 캐럴 (음원 파일 없이 브라우저가 직접 소리를 만듦 — Web Audio)
// - 곡: 징글벨(1857) · 위 위시 유 어 메리 크리스마스(전래) — 둘 다 저작권이 끝난 곡이라 가락을 그대로 쓸 수 있음
// - 소리: 오르골 느낌 (맑은 음 + 한 옥타브 위 음을 살짝, 빨리 사라지는 울림) + 마디마다 낮은 음 하나
// - 브라우저는 사용자가 화면을 누르기 전에는 소리를 못 냄 → [🎵 노래 켜기]를 눌러야 시작
// ============================================

// 음 이름 → 주파수(Hz). 가온 다(C4) = 261.63
const NOTE = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 }
export const freq = (name) => {
  const m = /^([A-G])(#|b)?(\d)$/.exec(name)
  const semis = NOTE[m[1]] + (m[2] === '#' ? 1 : m[2] === 'b' ? -1 : 0) + (Number(m[3]) - 4) * 12
  return 261.63 * 2 ** (semis / 12)
}

// 가락: "음:박" 을 띄어쓰기로 (R = 쉼표). bass: 마디마다 낮은 음
const SONGS = [
  {
    name: '징글벨',
    bpm: 132,
    beatsPerBar: 4,
    melody: 'E5:1 E5:1 E5:2 E5:1 E5:1 E5:2 E5:1 G5:1 C5:1.5 D5:0.5 E5:4 '
      + 'F5:1 F5:1 F5:1.5 F5:0.5 F5:1 E5:1 E5:1 E5:0.5 E5:0.5 E5:1 D5:1 D5:1 E5:1 D5:2 G5:2 '
      + 'E5:1 E5:1 E5:2 E5:1 E5:1 E5:2 E5:1 G5:1 C5:1.5 D5:0.5 E5:4 '
      + 'F5:1 F5:1 F5:1.5 F5:0.5 F5:1 E5:1 E5:1 E5:0.5 E5:0.5 G5:1 G5:1 F5:1 D5:1 C5:3 R:1',
    bass: 'C3 C3 C3 C3 F3 C3 D3 G3 C3 C3 C3 C3 F3 C3 G3 C3',
  },
  {
    name: '위 위시 유 어 메리 크리스마스',
    bpm: 150,
    beatsPerBar: 3,
    melody: 'G4:1 C5:1 C5:0.5 D5:0.5 C5:0.5 B4:0.5 A4:1 A4:1 A4:1 D5:1 D5:0.5 E5:0.5 D5:0.5 C5:0.5 B4:1 G4:1 G4:1 '
      + 'E5:1 E5:0.5 F5:0.5 E5:0.5 D5:0.5 C5:1 A4:1 G4:0.5 G4:0.5 A4:1 D5:1 B4:1 C5:2 '
      + 'G4:1 C5:1 C5:1 C5:1 B4:2 B4:1 C5:1 B4:1 A4:1 G4:2 D5:1 E5:1 D5:1 C5:1 G5:1 G4:1 G4:0.5 G4:0.5 A4:1 D5:1 B4:1 C5:2 R:1',
    bass: 'R C3 F3 D3 G3 E3 A3 F3 C3 C3 G3 C3 G3 C3 C3 F3 C3',
    pickup: 1,   // 못갖춘마디: 첫 박 하나가 마디 앞에 나옴
  },
]

const parse = (melody) => melody.trim().split(/\s+/).map((token) => {
  const [name, beats] = token.split(':')
  return { name, beats: Number(beats) }
})

// 곡 길이(박) — 테스트용
export const songBeats = (song) => parse(song.melody).reduce((sum, n) => sum + n.beats, 0)
export const songs = () => SONGS

export function createTreeMusic() {

  let ctx = null
  let master = null
  let timer = 0
  let playing = false
  let index = 0

  // 한 음: 오르골처럼 톡 치고 사라짐
  const pluck = (f, at, seconds, volume) => {
    const gain = ctx.createGain()
    const length = Math.min(1.6, Math.max(0.35, seconds * 1.7))
    gain.gain.setValueAtTime(0.0001, at)
    gain.gain.exponentialRampToValueAtTime(volume, at + 0.008)
    gain.gain.exponentialRampToValueAtTime(0.0001, at + length)
    gain.connect(master)

    const body = ctx.createOscillator()
    body.type = 'triangle'
    body.frequency.value = f
    body.connect(gain)
    body.start(at)
    body.stop(at + length + 0.05)

    const shine = ctx.createOscillator()     // 한 옥타브 위 맑은 소리를 살짝
    const shineGain = ctx.createGain()
    shine.type = 'sine'
    shine.frequency.value = f * 2
    shineGain.gain.value = 0.35
    shine.connect(shineGain)
    shineGain.connect(gain)
    shine.start(at)
    shine.stop(at + length + 0.05)
  }

  // 한 곡을 통째로 예약하고, 끝날 즈음 다음 곡을 예약
  const playSong = () => {
    if (!playing) return
    const song = SONGS[index % SONGS.length]
    index += 1
    const beat = 60 / song.bpm
    const start = ctx.currentTime + 0.12

    let t = start
    for (const n of parse(song.melody)) {
      if (n.name !== 'R') pluck(freq(n.name), t, n.beats * beat, 0.5)
      t += n.beats * beat
    }

    const pickup = (song.pickup ?? 0) * beat
    song.bass.split(' ').forEach((name, bar) => {
      if (name === 'R') return
      // 첫 낮은 음 자리가 R이면 그 마디는 못갖춘마디(pickup)라서 길이가 짧음
      const at = start + (song.pickup ? pickup + (bar - 1) * song.beatsPerBar * beat : bar * song.beatsPerBar * beat)
      pluck(freq(name), at, song.beatsPerBar * beat, 0.32)
    })

    timer = setTimeout(playSong, Math.max(500, (t - ctx.currentTime + 0.9) * 1000))
  }

  return {
    // 꼭 사용자가 버튼을 누른 순간에 불러야 소리가 남 → 시작했으면 true
    start() {
      try {
        const AudioContextClass = window.AudioContext || window.webkitAudioContext
        if (!AudioContextClass) return false
        if (!ctx) {
          ctx = new AudioContextClass()
          master = ctx.createGain()
          master.gain.value = 0.16
          master.connect(ctx.destination)
        }
        ctx.resume?.()
        if (playing) return true
        playing = true
        playSong()
        return true
      } catch {
        return false
      }
    },
    stop() {
      playing = false
      clearTimeout(timer)
      // 이미 예약해 둔 음이 남아 있으면 다시 켰을 때 겹쳐 들림 → 소리를 줄이고 장치를 아예 닫음 (다시 켜면 새로 만듦)
      const old = ctx
      const oldMaster = master
      ctx = null
      master = null
      if (!old) return
      try {
        oldMaster.gain.cancelScheduledValues(old.currentTime)
        oldMaster.gain.setTargetAtTime(0.0001, old.currentTime, 0.05)
        setTimeout(() => { try { old.close() } catch { /* 무시 */ } }, 300)
      } catch { /* 무시 */ }
    },
    dispose() {
      this.stop()
    },
    get playing() { return playing },
  }
}
