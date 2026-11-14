// ============================================
// 🎤 말로 묻기 (AI 챗봇 입력칸 옆)
// - 브라우저 음성 인식(Web Speech API, 한국어)으로 말한 내용을 입력칸에 채움 → 확인하고 [보내기]
// - onFinal: 다 들은 뒤 한 번 불림 (🔊 음성 대화가 켜져 있으면 AI 챗봇이 바로 보냄)
// - keep: 🟢 항상 듣기 — 말이 없어도 계속 다시 들음 (직접 ⏺를 눌러 멈추면 다시 🎤를 누를 때까지 쉼)
// - ref.start(): 밖에서 마이크 켜기 (🔊 음성 대화의 "계속 듣기")
// - 지원하지 않는 브라우저(일부 PC 파이어폭스 등)면 버튼을 안 보여줌
// - 음성은 브라우저(구글·애플) 쪽에서 글자로 바뀌고, 우리 서버로는 글자만 감
// ============================================
import { useEffect, useImperativeHandle, useRef, useState } from 'react'

const Recognition = typeof window !== 'undefined' ? (window.SpeechRecognition || window.webkitSpeechRecognition) : null

function VoiceButton({ onText, onFinal, disabled, ref, keep = false }) {

  const [listening, setListening] = useState(false)
  const [error, setError] = useState('')
  const rec = useRef(null)

  // keep(항상 듣기): 말이 없이 끝나면 조금 뒤 다시 들음. 직접 멈췄거나, 권한이 막혔거나, 답을 기다리는 중이면 다시 켜지 않음
  const live = useRef({ keep, disabled, manual: false, denied: false, timer: 0 })
  useEffect(() => {
    live.current.keep = keep
    live.current.disabled = disabled
  })

  useEffect(() => () => {
    clearTimeout(live.current.timer)
    rec.current?.abort?.()
  }, [])

  const start = () => {
    clearTimeout(live.current.timer)
    live.current.manual = false
    setError('')
    const r = new Recognition()
    r.lang = 'ko-KR'
    r.interimResults = true
    r.continuous = false
    let heard = ''
    r.onresult = (e) => {
      heard = Array.from(e.results).map((x) => x[0].transcript).join('')
      onText(heard)
    }
    r.onerror = (e) => {
      if (e.error === 'not-allowed' || e.error === 'service-not-allowed') live.current.denied = true
      if (live.current.keep && e.error === 'no-speech') return   // 항상 듣기 중의 조용한 시간은 오류가 아님
      setError(e.error === 'not-allowed' ? '마이크 권한을 허용해주세요.' : e.error === 'no-speech' ? '말소리가 들리지 않았어요.' : '음성 인식을 하지 못했어요.')
    }
    r.onend = () => {
      setListening(false)
      if (heard.trim()) return onFinal?.(heard.trim())   // 다 들은 뒤 (음성 대화 중이면 바로 보내는 데 씀)
      const s = live.current
      if (s.keep && !s.manual && !s.denied) {
        s.timer = setTimeout(() => {
          if (live.current.keep && !live.current.disabled && document.visibilityState === 'visible' && !window.speechSynthesis?.speaking) start()
          else if (live.current.keep && !live.current.manual) live.current.timer = setTimeout(() => ref?.current?.start?.(), 1500)
        }, 500)
      }
    }
    rec.current = r
    setListening(true)
    r.start()
  }

  const stop = () => {
    live.current.manual = true          // 직접 멈춤 → 다시 누를 때까지 쉬기
    clearTimeout(live.current.timer)
    rec.current?.stop()
  }

  // 계속 듣기: AI 챗봇이 답을 다 읽은 뒤 ref.current.start()로 마이크를 다시 켬 (듣는 중 · 답 기다리는 중이면 아무 일도 안 함)
  useImperativeHandle(ref, () => ({
    start: () => { if (Recognition && !listening && !disabled && !live.current.denied) start() },
    stop,
  }))

  if (!Recognition) return null

  return (
    <>
      <button
        type="button"
        className={`ai-voice ${listening ? 'ai-voice-on' : ''}`}
        onClick={listening ? stop : start}
        disabled={disabled}
        aria-label={listening ? '듣는 중 · 눌러서 멈추기' : '말로 묻기'}
        title={listening ? '듣는 중… (눌러서 멈추기)' : '말로 묻기'}
      >
        {listening ? '⏺' : '🎤'}
      </button>
      {error && <span className="ai-voice-error" role="alert">{error}</span>}
    </>
  )
}

export default VoiceButton
