// ============================================
// 🖥️ 내 PC 프로그램 실행 (주소: /pc)
// - "배틀그라운드 실행해줘"처럼 말하면 내 PC에 설치된 프로그램을 실행 (윈도우 + PC 도우미가 켜져 있을 때)
// - 이 화면: 연결 상태 · 연결하는 법 · 찾은 프로그램 목록(검색 · 눌러서 실행) · 다시 찾기 · 연결 끊기
// - 도우미가 처음 켜질 때 이 화면을 /pc#pair=연결표 로 엶 → 표를 이 브라우저에 저장하고 주소에서 지움
//   (표는 # 뒤에 있어서 서버로 전송되지 않음)
// ============================================
import { useCallback, useEffect, useState } from 'react'
import { pcHealth, pcLaunch, pcPaired, pcPrograms, setPcToken, tokenFromHash } from '../../pc/pcHelper'
import './PcPage.css'

function PcPage() {

  const [state, setState] = useState('checking')     // checking · off(도우미 꺼짐) · unpaired(연결 전) · on
  const [programs, setPrograms] = useState([])
  const [q, setQ] = useState('')
  const [msg, setMsg] = useState('')
  const [busy, setBusy] = useState(false)

  const check = useCallback(async (fresh = false) => {
    setBusy(true)
    try {
      if (!(await pcHealth())) return setState('off')
      if (!pcPaired()) return setState('unpaired')
      setPrograms(await pcPrograms(fresh))
      setState('on')
      if (fresh) setMsg('프로그램을 다시 찾았어요.')
    } catch (e) {
      setState('unpaired')
      setMsg(e.message)
    } finally {
      setBusy(false)
    }
  }, [])

  useEffect(() => {
    // 도우미가 연 주소의 연결 표 받기 → 저장하고 주소에서 지움
    const token = tokenFromHash(window.location.hash)
    if (token) {
      setPcToken(token)
      window.history.replaceState(null, '', window.location.pathname)
    }
    const timer = setTimeout(() => check(), 0)
    return () => clearTimeout(timer)
  }, [check])

  const launch = async (p) => {
    setMsg('')
    try {
      await pcLaunch(p.id)
      setMsg(`▶ ${p.name}을(를) 실행했어요.`)
    } catch (e) {
      setMsg(e.message)
    }
  }

  const disconnect = () => {
    if (!window.confirm('이 브라우저와 PC 도우미의 연결을 끊을까요?')) return
    setPcToken('')
    setPrograms([])
    setState('unpaired')
    setMsg('연결을 끊었어요.')
  }

  const key = q.trim().toLowerCase()
  const shown = key ? programs.filter((p) => p.name.toLowerCase().includes(key)) : programs

  return (
    <main className="pc-page">
      <header>
        <h1>🖥️ 내 PC 프로그램 실행</h1>
        <p className="pc-muted">🎤에 “배틀그라운드 실행해줘”, “크롬 켜줘”라고 말하면 이 PC에 설치된 프로그램을 실행해요. 윈도우 PC에서 도우미를 켜 둔 동안만 돼요.</p>
      </header>

      <section className={`pc-status pc-${state}`}>
        <b>
          {state === 'checking' && '확인하는 중...'}
          {state === 'off' && '⚪ PC 도우미가 꺼져 있어요'}
          {state === 'unpaired' && '🟡 도우미는 켜져 있는데 아직 연결되지 않았어요'}
          {state === 'on' && `🟢 연결됨 · 프로그램 ${programs.length}개`}
        </b>
        <div>
          <button type="button" className="btn btn-ghost" onClick={() => check(state === 'on')} disabled={busy}>{state === 'on' ? '다시 찾기' : '다시 확인'}</button>
          {pcPaired() && <button type="button" className="btn btn-ghost" onClick={disconnect}>연결 끊기</button>}
        </div>
      </section>

      {state !== 'on' && (
        <section className="pc-card">
          <h2>연결하는 법 (처음 한 번)</h2>
          <ol>
            <li>윈도우 PC에 <b>Node.js</b>가 있어야 해요. (프로젝트 폴더에서 화면을 빌드할 때 쓰는 그 Node예요)</li>
            <li>프로젝트 폴더에서 아래 한 줄을 실행해요. (또는 폴더의 <b>pc-helper.cmd</b>를 더블클릭)<code>.\pc-helper.cmd</code></li>
            <li>처음 켜면 이 화면이 자동으로 열리면서 연결돼요. 브라우저가 “이 기기의 앱에 접근”을 물으면 <b>허용</b>을 눌러요.</li>
            <li>도우미 창을 켜 둔 동안만 동작해요. 창을 닫으면 꺼져요.</li>
          </ol>
          {state === 'unpaired' && <p className="pc-muted">연결이 안 되면 도우미 창을 닫고 <code>.\pc-helper.cmd --pair</code> 로 다시 켜 주세요.</p>}
          <p className="pc-safe">
            🔒 도우미는 이 PC 안에서만 듣고, 이 사이트에서 온 요청과 연결 표가 맞는 브라우저의 요청만 받아요.
            실행할 수 있는 것은 시작 메뉴에 있는 프로그램과 스팀 게임뿐이고, 명령어나 파일을 받아서 실행하는 기능은 없어요.
          </p>
        </section>
      )}

      {state === 'on' && (
        <section className="pc-card">
          <h2>찾은 프로그램</h2>
          <input className="pc-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="이름으로 찾기" aria-label="프로그램 이름으로 찾기" />
          {shown.length === 0 ? <p className="pc-muted">맞는 프로그램이 없어요.</p> : (
            <ul className="pc-list">
              {shown.slice(0, 200).map((p) => (
                <li key={p.id}>
                  <span>{p.kind === 'steam' ? '🎮' : '▫️'} {p.name}</span>
                  <button type="button" className="btn btn-ghost" onClick={() => launch(p)}>실행</button>
                </li>
              ))}
            </ul>
          )}
          {shown.length > 200 && <p className="pc-muted">200개까지만 보여요. 이름으로 찾아 보세요.</p>}
        </section>
      )}

      {msg && <p className="pc-msg" role="status">{msg}</p>}
    </main>
  )
}

export default PcPage
