// ============================================
// 🌐 번역 (주소: /translate, 로그인 없이 누구나)
// - 말하거나 써서 → 고른 언어로 번역 → 글자 + 한글 발음 + 🔊 소리로 듣기
// - 마주 보고 대화하기: [내가 말하기]는 내 언어로 듣고 상대 언어로, [상대가 말하기]는 그 반대로 번역해서 바로 읽어줌
// - 주소로 넘겨받기: /translate?text=화장실 어디예요&to=en (🎤 말로 시키기에서 "… 영어로"라고 하면 이렇게 들어옴)
// - 음성 인식이 안 되는 기기에서는 글자 입력만 (키보드의 🎤 받아쓰기는 쓸 수 있음)
// - 백엔드: POST /api/ai/translate { text, target } (하루 60번)
// ============================================
import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { canListen, canSpeak, listen, speak, stopSpeaking } from '../../voice/speech'
import './TranslatePage.css'

const LANGS = [['ko', '한국어'], ['en', '영어'], ['ja', '일본어'], ['zh', '중국어'], ['es', '스페인어'], ['vi', '베트남어']]
const NAME = Object.fromEntries(LANGS)

function TranslatePage() {

  const [params] = useSearchParams()
  const [initial] = useState(() => ({ text: (params.get('text') ?? '').slice(0, 500), to: NAME[params.get('to')] ? params.get('to') : 'en' }))
  const [mine, setMine] = useState('ko')            // 내 언어
  const [theirs, setTheirs] = useState(initial.to === 'ko' ? 'en' : initial.to)   // 상대 언어
  const [text, setText] = useState(initial.text)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(Boolean(initial.text))
  const [listening, setListening] = useState('')    // '' · 'mine' · 'theirs'
  const [copied, setCopied] = useState(false)
  const session = useRef(null)
  const supported = canListen()

  // 서버에 번역 요청 → 결과가 오면 바로 읽어줌
  const translate = useCallback((source, target) => {
    return fetch('/api/ai/translate', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text: source, target }) })
      .then((r) => (r.status === 429 ? { success: false, message: '잠시 후 다시 시도해주세요. (요청이 너무 많아요)' } : r.json()))
      .then((j) => {
        if (!j.success) {
          setResult(null)
          return setError(j.message || '번역하지 못했어요.')
        }
        setError('')
        setResult(j)
        speak(j.translated, j.target)
      })
      .catch(() => setError('서버에 연결하지 못했어요.'))
      .finally(() => setBusy(false))
  }, [])

  // 주소로 넘겨받은 글이 있으면 바로 번역 (처음 한 번)
  useEffect(() => {
    if (initial.text) translate(initial.text, initial.to)
    return () => {
      session.current?.stop()
      stopSpeaking()
    }
  }, [initial, translate])

  const run = (source, target) => {
    if (!source.trim()) return
    setBusy(true)
    setError('')
    translate(source.trim(), target)
  }

  // who: 'mine'(내 언어로 듣고 → 상대 언어로) / 'theirs'(상대 언어로 듣고 → 내 언어로)
  const hear = (who) => {
    if (listening) return session.current?.stop()
    stopSpeaking()
    setError('')
    setListening(who)
    session.current = listen({
      lang: who === 'mine' ? mine : theirs,
      onInterim: setText,
      onFinal: (heard) => {
        setText(heard)
        run(heard, who === 'mine' ? theirs : mine)
      },
      onError: setError,
      onEnd: () => setListening(''),
    })
  }

  const swap = () => {
    setMine(theirs)
    setTheirs(mine)
    setResult(null)
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(result.translated)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch { /* 복사를 못 하면 글자를 길게 눌러 복사 */ }
  }

  return (
    <main className="tr-page">
      <header className="tr-head">
        <h1>🌐 번역</h1>
        <p>말하거나 써보세요. 번역해서 글자와 소리로 알려드려요.</p>
      </header>

      <div className="tr-langs">
        <label>
          <span>내 언어</span>
          <select value={mine} onChange={(e) => { setMine(e.target.value); if (e.target.value === theirs) setTheirs(mine) }}>
            {LANGS.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
          </select>
        </label>
        <button type="button" className="tr-swap" onClick={swap} aria-label="두 언어 바꾸기">⇄</button>
        <label>
          <span>상대 언어</span>
          <select value={theirs} onChange={(e) => { setTheirs(e.target.value); if (e.target.value === mine) setMine(theirs) }}>
            {LANGS.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
          </select>
        </label>
      </div>

      {supported ? (
        <div className="tr-mics">
          <button type="button" className={`tr-mic ${listening === 'mine' ? 'tr-mic-on' : ''}`} onClick={() => hear('mine')} disabled={busy || listening === 'theirs'}>
            <span aria-hidden="true">{listening === 'mine' ? '⏺' : '🎤'}</span>
            <b>내가 말하기</b>
            <small>{NAME[mine]} → {NAME[theirs]}</small>
          </button>
          <button type="button" className={`tr-mic tr-mic-other ${listening === 'theirs' ? 'tr-mic-on' : ''}`} onClick={() => hear('theirs')} disabled={busy || listening === 'mine'}>
            <span aria-hidden="true">{listening === 'theirs' ? '⏺' : '🎤'}</span>
            <b>상대가 말하기</b>
            <small>{NAME[theirs]} → {NAME[mine]}</small>
          </button>
        </div>
      ) : (
        <p className="tr-note">이 기기에서는 음성 인식이 안 돼요. 아래 칸을 누르고 키보드의 🎤(받아쓰기)로 말하면 돼요.</p>
      )}

      <form className="tr-form" onSubmit={(e) => { e.preventDefault(); run(text, theirs) }}>
        <textarea value={text} onChange={(e) => setText(e.target.value.slice(0, 500))} rows={3} maxLength={500}
          placeholder={listening ? '듣고 있어요...' : '번역할 말을 쓰거나, 위 버튼을 누르고 말해보세요'} aria-label="번역할 말" />
        <div className="tr-form-row">
          <small>{text.length}/500</small>
          <button type="button" className="tr-btn" onClick={() => run(text, mine)} disabled={busy || !text.trim()}>{NAME[mine]}로</button>
          <button type="submit" className="tr-btn tr-btn-primary" disabled={busy || !text.trim()}>{busy ? '번역하는 중...' : `${NAME[theirs]}로 번역`}</button>
        </div>
      </form>

      {error && <p className="tr-error" role="alert">{error}</p>}

      {result && (
        <section className="tr-result" aria-live="polite">
          <small>{result.sourceName} → {result.targetName}</small>
          <p className="tr-out" lang={result.target}>{result.translated}</p>
          {result.reading && <p className="tr-reading">발음: {result.reading}</p>}
          <div className="tr-actions">
            {canSpeak() && <button type="button" className="tr-btn" onClick={() => speak(result.translated, result.target)}>🔊 다시 듣기</button>}
            <button type="button" className="tr-btn" onClick={copy}>{copied ? '복사했어요' : '복사'}</button>
          </div>
        </section>
      )}

      <p className="tr-foot">AI가 번역해요. 계약서 · 진료처럼 중요한 내용은 꼭 다시 확인해주세요. 하루 60번까지 쓸 수 있어요.</p>
    </main>
  )
}

export default TranslatePage
