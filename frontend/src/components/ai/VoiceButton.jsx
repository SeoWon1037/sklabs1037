// ============================================
// 🎤 말로 묻기 (AI 비서 입력칸 옆)
// - 브라우저 음성 인식(Web Speech API, 한국어)으로 말한 내용을 입력칸에 채움 → 확인하고 [보내기]
// - 지원하지 않는 브라우저(일부 PC 파이어폭스 등)면 버튼을 안 보여줌
// - 음성은 브라우저(구글·애플) 쪽에서 글자로 바뀌고, 우리 서버로는 글자만 감
// ============================================
import { useEffect, useRef, useState } from 'react'

const Recognition = typeof window !== 'undefined' ? (window.SpeechRecognition || window.webkitSpeechRecognition) : null

function VoiceButton({ onText, disabled }) {

  const [listening, setListening] = useState(false)
  const [error, setError] = useState('')
  const rec = useRef(null)

  useEffect(() => () => rec.current?.abort?.(), [])

  if (!Recognition) return null

  const start = () => {
    setError('')
    const r = new Recognition()
    r.lang = 'ko-KR'
    r.interimResults = true
    r.continuous = false
    r.onresult = (e) => {
      const text = Array.from(e.results).map((x) => x[0].transcript).join('')
      onText(text)
    }
    r.onerror = (e) => {
      setError(e.error === 'not-allowed' ? '마이크 권한을 허용해주세요.' : e.error === 'no-speech' ? '말소리가 들리지 않았어요.' : '음성 인식을 하지 못했어요.')
    }
    r.onend = () => setListening(false)
    rec.current = r
    setListening(true)
    r.start()
  }

  const stop = () => rec.current?.stop()

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
