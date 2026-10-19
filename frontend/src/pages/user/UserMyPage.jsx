// ============================================
// 마이페이지 (주소: /mypage)
// - 내 정보 / 내 AI 질문 기록 / 닉네임 변경 / 비밀번호 변경(이메일 가입 회원만) / 회원 탈퇴
// - 백엔드 API
//   GET  /api/user/mypage            내 정보
//   POST /api/user/mypage/nickname   { nickname }
//   POST /api/user/mypage/password   { currentPassword, newPassword }
//   POST /api/user/mypage/withdraw   { currentPassword, confirmText }
//   GET  /api/user/mypage/chats?page=1   내 AI 질문 기록 (10개씩)
//   /api/alerts/**                    내 가격 알림 (MyAlertsBox)
// - 닉네임 변경/탈퇴 후 "sklabs:user-updated" 이벤트 → 상단바가 로그인 정보를 다시 불러옴
// ============================================
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import MyAlertsBox from '../../components/alert/MyAlertsBox'
import './UserMyPage.css'

const WITHDRAW_TEXT = '탈퇴합니다'

const PROVIDER_LABEL = { LOCAL: '이메일', KAKAO: '카카오', NAVER: '네이버' }

// "2026-10-17T21:48:00" → "2026.10.17"
const formatDate = (text) => (text ? text.slice(0, 10).replaceAll('-', '.') : '-')

// AI 답변의 **굵게** 표시는 목록에서는 빼고 보여줌
const plainText = (text) => (text ?? '').replace(/\*\*/g, '')

// 접힌 상태에서 보여줄 답변 앞부분
const PREVIEW_LENGTH = 90

const RATING_LABEL = { UP: '👍 도움됨', DOWN: '👎 아쉬움' }

// 상단바에 "로그인 정보가 바뀌었다"고 알리기
const notifyUserUpdated = () => window.dispatchEvent(new Event('sklabs:user-updated'))

// POST 요청 공통 (JSON 보내고 JSON 받기)
const postJson = async (url, body) => {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return res.json()
}

function UserMyPage() {

  const navigate = useNavigate()

  // null = 불러오는 중, false = 로그인 안 됨
  const [user, setUser] = useState(null)
  const [loadError, setLoadError] = useState('')

  // 닉네임
  const [nickname, setNickname] = useState('')
  const [nicknameMsg, setNicknameMsg] = useState({ ok: false, text: '' })

  // 비밀번호
  const [pw, setPw] = useState({ current: '', next: '', confirm: '' })
  const [pwMsg, setPwMsg] = useState({ ok: false, text: '' })

  // 탈퇴
  const [withdrawOpen, setWithdrawOpen] = useState(false)
  const [withdrawPw, setWithdrawPw] = useState('')
  const [withdrawText, setWithdrawText] = useState('')
  const [withdrawMsg, setWithdrawMsg] = useState('')

  const [saving, setSaving] = useState(false)

  // 내 AI 질문 기록
  const [chats, setChats] = useState({ items: [], total: 0, page: 0, loading: false, error: '' })
  const [openChatId, setOpenChatId] = useState(null)   // 답변을 펼쳐 둔 질문

  // 처음 한 번: 내 정보
  useEffect(() => {
    fetch('/api/user/mypage')
      .then((res) => {
        if (res.status === 401) return { unauthorized: true }
        return res.json()
      })
      .then((data) => {
        if (data.unauthorized) { setUser(false); return }
        if (data.success) {
          setUser(data.user)
          setNickname(data.user.nickname ?? '')
        } else {
          setLoadError(data.message)
        }
      })
      .catch(() => setLoadError('서버에 연결할 수 없습니다.'))
  }, [])

  // 처음 한 번: AI 질문 기록 1페이지
  useEffect(() => {
    fetch('/api/user/mypage/chats?page=1')
      .then((res) => res.json())
      .then((data) => {
        if (data.success) setChats({ items: data.chats, total: data.total, page: 1, loading: false, error: '' })
      })
      .catch(() => {})
  }, [])

  // ===== AI 질문 기록 더보기 =====
  const loadMoreChats = async () => {
    if (chats.loading) return
    const next = chats.page + 1
    setChats((prev) => ({ ...prev, loading: true }))
    try {
      const res = await fetch(`/api/user/mypage/chats?page=${next}`)
      const data = await res.json()
      setChats((prev) => (data.success
        ? { items: [...prev.items, ...data.chats], total: data.total, page: next, loading: false, error: '' }
        : { ...prev, loading: false, error: data.message }))
    } catch {
      setChats((prev) => ({ ...prev, loading: false, error: '서버에 연결할 수 없습니다.' }))
    }
  }

  // ===== 닉네임 변경 =====
  const saveNickname = async (e) => {
    e.preventDefault()
    if (saving) return
    setSaving(true)
    try {
      const data = await postJson('/api/user/mypage/nickname', { nickname })
      if (data.success) {
        setUser((prev) => ({ ...prev, nickname: data.nickname }))
        setNickname(data.nickname)
        setNicknameMsg({ ok: true, text: '닉네임을 바꿨어요.' })
        notifyUserUpdated()
      } else {
        setNicknameMsg({ ok: false, text: data.message })
      }
    } catch {
      setNicknameMsg({ ok: false, text: '서버에 연결할 수 없습니다.' })
    } finally {
      setSaving(false)
    }
  }

  // ===== 비밀번호 변경 =====
  const savePassword = async (e) => {
    e.preventDefault()
    if (saving) return

    if (pw.next !== pw.confirm) {
      setPwMsg({ ok: false, text: '새 비밀번호가 서로 다릅니다.' })
      return
    }

    setSaving(true)
    try {
      const data = await postJson('/api/user/mypage/password', { currentPassword: pw.current, newPassword: pw.next })
      if (data.success) {
        setPw({ current: '', next: '', confirm: '' })
        setPwMsg({ ok: true, text: '비밀번호를 바꿨어요.' })
      } else {
        setPwMsg({ ok: false, text: data.message })
      }
    } catch {
      setPwMsg({ ok: false, text: '서버에 연결할 수 없습니다.' })
    } finally {
      setSaving(false)
    }
  }

  // ===== 회원 탈퇴 =====
  const withdraw = async (e) => {
    e.preventDefault()
    if (saving) return
    setSaving(true)
    try {
      const data = await postJson('/api/user/mypage/withdraw', { currentPassword: withdrawPw, confirmText: withdrawText })
      if (data.success) {
        notifyUserUpdated()
        navigate('/', { replace: true })
      } else {
        setWithdrawMsg(data.message)
      }
    } catch {
      setWithdrawMsg('서버에 연결할 수 없습니다.')
    } finally {
      setSaving(false)
    }
  }

  // ===== 화면 =====
  if (loadError) {
    return <main className="mypage"><p className="mypage-status mypage-error">{loadError}</p></main>
  }

  if (user === null) {
    return <main className="mypage"><p className="mypage-status">불러오는 중...</p></main>
  }

  if (user === false) {
    return (
      <main className="mypage">
        <p className="mypage-status">로그인 후 이용할 수 있어요.</p>
        <div className="mypage-center">
          <Link to="/login" className="btn btn-primary">로그인</Link>
        </div>
      </main>
    )
  }

  const isLocal = user.provider === 'LOCAL'

  return (
    <main className="mypage">

      <h1 className="mypage-title">마이페이지</h1>

      {/* 내 정보 */}
      <section className="mypage-box">
        <h2 className="mypage-box-title">내 정보</h2>
        <dl className="mypage-info">
          <div><dt>이메일</dt><dd>{user.email}</dd></div>
          <div><dt>닉네임</dt><dd>{user.nickname}</dd></div>
          <div><dt>가입 방식</dt><dd>{PROVIDER_LABEL[user.provider] ?? user.provider}</dd></div>
          <div><dt>가입일</dt><dd>{formatDate(user.insDay)}</dd></div>
        </dl>
      </section>

      {/* 내 가격 알림 */}
      <MyAlertsBox hasEmail={Boolean(user.email && user.email.includes('@'))} />

      {/* 내 AI 질문 기록 */}
      <section className="mypage-box">
        <div className="mypage-box-head">
          <h2 className="mypage-box-title">내 AI 질문 기록</h2>
          <span className="mypage-count">{chats.total}개</span>
        </div>

        {chats.total === 0 ? (
          <p className="mypage-desc">
            아직 AI 비서에게 물어본 질문이 없어요. <Link to="/ai" className="mypage-link">AI 비서에게 물어보기 →</Link>
          </p>
        ) : (
          <ul className="mypage-chats">
            {chats.items.map((c) => {
              const open = openChatId === c.chatId
              const answer = plainText(c.answer)
              return (
                <li key={c.chatId} className="mypage-chat">
                  <button
                    type="button"
                    className="mypage-chat-q"
                    onClick={() => setOpenChatId(open ? null : c.chatId)}
                    aria-expanded={open}
                  >
                    <span className="mypage-chat-mark">Q</span>
                    <span className="mypage-chat-text">{c.question}</span>
                    <span className="mypage-chat-toggle">{open ? '접기' : '펼치기'}</span>
                  </button>

                  <p className={`mypage-chat-a ${open ? 'open' : ''}`}>
                    {open || answer.length <= PREVIEW_LENGTH ? answer : `${answer.slice(0, PREVIEW_LENGTH)}…`}
                  </p>

                  <div className="mypage-chat-meta">
                    <span className={`mypage-chat-rating ${c.rating ? c.rating.toLowerCase() : ''}`}>
                      {RATING_LABEL[c.rating] ?? '평가 안 함'}
                    </span>
                    {open && c.toolsUsed && <span className="mypage-chat-tools">🔧 {c.toolsUsed}</span>}
                    <Link to={`/ai?q=${encodeURIComponent(c.question)}`} className="mypage-link">다시 묻기 →</Link>
                  </div>
                </li>
              )
            })}
          </ul>
        )}

        {chats.items.length < chats.total && (
          <button type="button" className="btn mypage-more" onClick={loadMoreChats} disabled={chats.loading}>
            {chats.loading ? '불러오는 중...' : `더보기 (${chats.items.length} / ${chats.total})`}
          </button>
        )}
        {chats.error && <p className="mypage-msg error">{chats.error}</p>}
      </section>

      {/* 닉네임 변경 */}
      <section className="mypage-box">
        <h2 className="mypage-box-title">닉네임 변경</h2>
        <form className="mypage-form" onSubmit={saveNickname}>
          <input
            className="mypage-input"
            value={nickname}
            onChange={(e) => { setNickname(e.target.value); setNicknameMsg({ ok: false, text: '' }) }}
            maxLength={20}
            placeholder="2~20자"
          />
          <button
            type="submit"
            className="btn btn-primary"
            disabled={saving || nickname.trim() === '' || nickname.trim() === user.nickname}
          >
            저장
          </button>
        </form>
        {nicknameMsg.text && <p className={`mypage-msg ${nicknameMsg.ok ? 'ok' : 'error'}`}>{nicknameMsg.text}</p>}
      </section>

      {/* 비밀번호 변경 (이메일 가입 회원만) */}
      <section className="mypage-box">
        <h2 className="mypage-box-title">비밀번호 변경</h2>
        {isLocal ? (
          <form className="mypage-form mypage-form-col" onSubmit={savePassword}>
            <input
              type="password"
              className="mypage-input"
              placeholder="현재 비밀번호"
              value={pw.current}
              onChange={(e) => setPw({ ...pw, current: e.target.value })}
              autoComplete="current-password"
            />
            <input
              type="password"
              className="mypage-input"
              placeholder="새 비밀번호 (영문 + 숫자 8~20자)"
              value={pw.next}
              onChange={(e) => setPw({ ...pw, next: e.target.value })}
              maxLength={20}
              autoComplete="new-password"
            />
            <input
              type="password"
              className="mypage-input"
              placeholder="새 비밀번호 확인"
              value={pw.confirm}
              onChange={(e) => setPw({ ...pw, confirm: e.target.value })}
              maxLength={20}
              autoComplete="new-password"
            />
            <button
              type="submit"
              className="btn btn-primary"
              disabled={saving || !pw.current || !pw.next || !pw.confirm}
            >
              비밀번호 변경
            </button>
          </form>
        ) : (
          <p className="mypage-desc">
            {PROVIDER_LABEL[user.provider]} 로그인으로 가입한 계정은 비밀번호가 없어요.
          </p>
        )}
        {pwMsg.text && <p className={`mypage-msg ${pwMsg.ok ? 'ok' : 'error'}`}>{pwMsg.text}</p>}
      </section>

      {/* 회원 탈퇴 */}
      <section className="mypage-box mypage-danger">
        <h2 className="mypage-box-title">회원 탈퇴</h2>
        <p className="mypage-desc">
          탈퇴하면 이메일, 닉네임 같은 회원 정보와 관심 종목이 삭제되고 되돌릴 수 없어요.
        </p>

        {!withdrawOpen ? (
          <button type="button" className="btn mypage-danger-btn" onClick={() => setWithdrawOpen(true)}>
            탈퇴하기
          </button>
        ) : (
          <form className="mypage-form mypage-form-col" onSubmit={withdraw}>
            {isLocal && (
              <input
                type="password"
                className="mypage-input"
                placeholder="비밀번호"
                value={withdrawPw}
                onChange={(e) => setWithdrawPw(e.target.value)}
                autoComplete="current-password"
              />
            )}
            <input
              className="mypage-input"
              placeholder={`"${WITHDRAW_TEXT}"를 입력해주세요`}
              value={withdrawText}
              onChange={(e) => setWithdrawText(e.target.value)}
            />
            <div className="mypage-row">
              <button type="button" className="btn" onClick={() => { setWithdrawOpen(false); setWithdrawMsg('') }}>
                취소
              </button>
              <button
                type="submit"
                className="btn mypage-danger-btn"
                disabled={saving || withdrawText.trim() !== WITHDRAW_TEXT || (isLocal && !withdrawPw)}
              >
                탈퇴
              </button>
            </div>
          </form>
        )}
        {withdrawMsg && <p className="mypage-msg error">{withdrawMsg}</p>}
      </section>

    </main>
  )
}

export default UserMyPage