// ============================================
// 종목 토론방 (주식 종목 화면의 "토론" 탭)
// - 위: 최근 7일 의견 비율 + 글쓰기 칸 (오를 것 / 내릴 것 선택)
// - 아래: 글 목록 (최신순), 글을 누르면 그 자리에서 댓글이 펼쳐짐
// - 쓰기는 로그인한 사람만, 삭제는 본인 글 또는 관리자만
// - 백엔드 API
//   GET    /api/stock/board?stockId=&page=          글 목록 + 의견 비율
//   POST   /api/stock/board                          글 쓰기
//   DELETE /api/stock/board/{postId}                 글 삭제
//   GET    /api/stock/board/{postId}/comments        댓글 목록
//   POST   /api/stock/board/{postId}/comments        댓글 쓰기
//   DELETE /api/stock/board/comments/{commentId}     댓글 삭제
//   GET    /api/stock/board/stream?stockId=          실시간 알림 (SSE)
// - 실시간: 다른 사람이 글을 쓰면 "새 글 n개" 버튼, 댓글·삭제는 목록 숫자에 바로 반영, 보는 사람 수 표시
//   (읽던 글이 갑자기 밀려 내려가지 않게 새 글은 버튼을 눌러야 위에 붙음)
// ============================================
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import useBoardStream from '../../hooks/useBoardStream'
import './StockBoard.css'

const POST_MAX = 500
const COMMENT_MAX = 200

const OPINIONS = [
  { value: 'UP', label: '🔴 오를 것' },
  { value: 'DOWN', label: '🔵 내릴 것' },
]

// "방금", "3분 전", "2시간 전", "1일 전"
const timeAgo = (millis) => {
  const minutes = Math.floor((Date.now() - millis) / 60000)
  if (minutes < 1) return '방금'
  if (minutes < 60) return `${minutes}분 전`
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}시간 전`
  return `${Math.floor(minutes / (60 * 24))}일 전`
}

// 서버 응답 공통 처리 (실패하면 서버 메시지로 에러)
const request = async (url, options) => {
  const res = await fetch(url, options)
  const data = await res.json().catch(() => ({ success: false, message: '서버 응답을 읽지 못했습니다.' }))
  if (!data.success) throw new Error(data.message || '요청을 처리하지 못했습니다.')
  return data
}

const jsonBody = (method, body) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

// ============================================
// 댓글 영역 (글 하나를 펼쳤을 때)
// ============================================
function CommentArea({ postId, loggedIn, onCountChange, refreshKey }) {

  const [comments, setComments] = useState(null)   // null = 불러오는 중
  const [error, setError] = useState('')
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)

  const load = () =>
    request(`/api/stock/board/${postId}/comments`)
      .then((data) => {
        setComments(data.comments)
        onCountChange(data.comments.length)
      })
      .catch((e) => setError(e.message))

  // 펼칠 때 1번 + 다른 사람이 이 글에 댓글을 달거나 지우면(refreshKey가 바뀜) 다시
  useEffect(() => {
    let ignore = false
    request(`/api/stock/board/${postId}/comments`)
      .then((data) => { if (!ignore) setComments(data.comments) })
      .catch((e) => { if (!ignore) setError(e.message) })
    return () => { ignore = true }
  }, [postId, refreshKey])

  const submit = async (e) => {
    e.preventDefault()
    if (!text.trim() || sending) return
    setSending(true)
    setError('')
    try {
      await request(`/api/stock/board/${postId}/comments`, jsonBody('POST', { content: text }))
      setText('')
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setSending(false)
    }
  }

  const remove = async (commentId) => {
    if (!window.confirm('이 댓글을 삭제할까요?')) return
    setError('')
    try {
      await request(`/api/stock/board/comments/${commentId}`, { method: 'DELETE' })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="board-comments">

      {comments === null && !error && <p className="board-comment-empty">댓글을 불러오는 중...</p>}
      {comments?.length === 0 && <p className="board-comment-empty">첫 댓글을 남겨보세요.</p>}

      {comments?.length > 0 && (
        <ul className="board-comment-list">
          {comments.map((c) => (
            <li key={c.commentId} className="board-comment">
              <span className="board-comment-mark">└</span>
              <div className="board-comment-body">
                <span className="board-comment-meta">
                  <strong>{c.nickname}</strong> · {timeAgo(c.insAt)}
                  {c.canDelete && (
                    <button type="button" className="board-delete" onClick={() => remove(c.commentId)}>삭제</button>
                  )}
                </span>
                <p className="board-comment-text">{c.content}</p>
              </div>
            </li>
          ))}
        </ul>
      )}

      {loggedIn ? (
        <form className="board-comment-form" onSubmit={submit}>
          <input
            className="board-comment-input"
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={COMMENT_MAX}
            placeholder={`댓글 달기 (${COMMENT_MAX}자까지)`}
          />
          <button type="submit" className="btn btn-primary board-comment-btn" disabled={!text.trim() || sending}>
            {sending ? '등록 중' : '등록'}
          </button>
        </form>
      ) : (
        <p className="board-comment-empty"><Link to="/login">로그인</Link>하면 댓글을 달 수 있어요.</p>
      )}

      {error && <p className="board-error">{error}</p>}
    </div>
  )
}

// ============================================
// 토론방
// ============================================
function StockBoard({ stockId, stockName }) {

  // ===== 글 목록 =====
  const [board, setBoard] = useState({ posts: [], total: 0, upCount: 0, downCount: 0, loggedIn: false })
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [openId, setOpenId] = useState(null)        // 댓글을 펼친 글

  // ===== 실시간 =====
  const [incoming, setIncoming] = useState([])      // 아직 목록에 안 붙인 새 글 ("새 글 n개" 버튼)
  const [commentTick, setCommentTick] = useState(0) // 펼친 댓글 다시 불러오기 신호
  const boardRef = useRef(board)                    // 이벤트 처리 때 최신 목록
  const openRef = useRef(openId)
  const syncTimer = useRef(null)
  const helloCount = useRef(0)
  useEffect(() => {
    boardRef.current = board
    openRef.current = openId
  })

  // ===== 글쓰기 =====
  const [text, setText] = useState('')
  const [opinion, setOpinion] = useState(null)
  const [sending, setSending] = useState(false)
  const [writeError, setWriteError] = useState('')

  // 목록 불러오기 (page 1 = 새로 고침, 2 이상 = 아래에 이어 붙이기)
  const loadPage = (nextPage) => {
    setLoading(true)
    return request(`/api/stock/board?stockId=${stockId}&page=${nextPage}`)
      .then((data) => {
        setBoard((prev) => ({
          // 더보기: 그 사이 새 글이 생겨 한 칸씩 밀렸으면 같은 글이 또 올 수 있음 → 이미 있는 글은 빼고 붙이기
          posts: nextPage === 1
            ? data.posts
            : [...prev.posts, ...data.posts.filter((p) => !prev.posts.some((q) => q.postId === p.postId))],
          total: data.total,
          upCount: data.upCount,
          downCount: data.downCount,
          loggedIn: data.loggedIn,
        }))
        setPage(nextPage)
        setListError('')
        if (nextPage === 1) setIncoming([])
      })
      .catch((e) => setListError(e.message))
      .finally(() => setLoading(false))
  }

  // 종목이 바뀌면 처음부터 (StockPage에서 key={stockId}로 새로 만듦)
  useEffect(() => {
    let ignore = false
    request(`/api/stock/board?stockId=${stockId}&page=1`)
      .then((data) => {
        if (ignore) return
        setBoard({ posts: data.posts, total: data.total, upCount: data.upCount, downCount: data.downCount, loggedIn: data.loggedIn })
      })
      .catch((e) => { if (!ignore) setListError(e.message) })
      .finally(() => { if (!ignore) setLoading(false) })
    return () => { ignore = true }
  }, [stockId])

  // 실시간 이벤트가 오면: 1페이지를 조용히 다시 받아서
  //   - 지금 목록에 있는 글은 댓글 수·삭제 여부·의견 비율만 맞추고
  //   - 목록에 없는 새 글은 "새 글 n개" 버튼으로 (내가 쓴 글은 이미 목록에 있어서 버튼에 안 잡힘)
  // - 이벤트가 몰려도 0.4초에 한 번만
  const syncTop = () => {
    clearTimeout(syncTimer.current)
    syncTimer.current = setTimeout(() => {
      request(`/api/stock/board?stockId=${stockId}&page=1`)
        .then((data) => {
          const fresh = new Map(data.posts.map((p) => [p.postId, p]))
          const known = new Set(boardRef.current.posts.map((p) => p.postId))
          setBoard((prev) => ({
            ...prev,
            posts: prev.posts.map((p) => {
              const f = fresh.get(p.postId)
              return f ? { ...p, commentCount: f.commentCount, deleted: f.deleted, canDelete: f.canDelete } : p
            }),
            total: data.total,
            upCount: data.upCount,
            downCount: data.downCount,
          }))
          setIncoming(data.posts.filter((p) => !known.has(p.postId) && !p.deleted))
        })
        .catch(() => { /* 실시간 반영만 빠짐 */ })
    }, 400)
  }

  // 화면을 떠나면 대기 중인 동기화 취소
  useEffect(() => () => clearTimeout(syncTimer.current), [])

  const { viewers, live } = useBoardStream(stockId, (type, data) => {
    if (type === 'hello') {
      // 처음 연결은 이미 목록을 받았으니 건너뛰고, 다시 연결된 경우(끊긴 사이 놓친 글)만 맞추기
      helloCount.current += 1
      if (helloCount.current > 1) syncTop()
      return
    }
    if (type === 'viewers') return
    if ((type === 'comment' || type === 'delete') && openRef.current === data.postId) setCommentTick((n) => n + 1)
    syncTop()
  })

  // 아직 목록에 없는 새 글 수 (방금 내 글쓰기로 목록이 바뀌었으면 자동으로 빠짐)
  const newCount = incoming.filter((p) => !board.posts.some((q) => q.postId === p.postId)).length

  // 글 쓰기
  const submit = async (e) => {
    e.preventDefault()
    if (!text.trim() || sending) return
    setSending(true)
    setWriteError('')
    try {
      await request('/api/stock/board', jsonBody('POST', { stockId, opinion, content: text }))
      setText('')
      setOpinion(null)
      setOpenId(null)
      await loadPage(1)
    } catch (err) {
      setWriteError(err.message)
    } finally {
      setSending(false)
    }
  }

  // 글 삭제
  const removePost = async (postId) => {
    if (!window.confirm('이 글을 삭제할까요?')) return
    try {
      await request(`/api/stock/board/${postId}`, { method: 'DELETE' })
      setOpenId(null)
      await loadPage(1)
    } catch (err) {
      setListError(err.message)
    }
  }

  // 댓글 수가 바뀌면 목록의 숫자도 바로 맞춤
  const setCommentCount = (postId, count) =>
    setBoard((prev) => ({
      ...prev,
      posts: prev.posts.map((p) => (p.postId === postId ? { ...p, commentCount: count } : p)),
    }))

  // 의견 비율 (최근 7일)
  const opinionTotal = board.upCount + board.downCount
  const upRate = opinionTotal ? Math.round((board.upCount / opinionTotal) * 100) : 0

  return (
    <div className="board">

      {/* ===== 실시간 상태 ===== */}
      {live && (
        <div className="board-live">
          <span className="board-live-dot" aria-hidden="true" />
          <span>실시간</span>
          {viewers != null && <span className="board-live-viewers">👀 {viewers.toLocaleString('ko-KR')}명이 보는 중</span>}
        </div>
      )}

      {/* ===== 의견 비율 ===== */}
      <div className="board-ratio">
        <div className="board-ratio-head">
          <span>{stockName} 최근 7일 의견</span>
          <span className="board-ratio-count">{opinionTotal}명 참여</span>
        </div>
        {opinionTotal > 0 ? (
          <>
            <div className="board-ratio-bar">
              <span className="board-ratio-up" style={{ width: `${upRate}%` }} />
              <span className="board-ratio-down" style={{ width: `${100 - upRate}%` }} />
            </div>
            <div className="board-ratio-legend">
              <span className="board-up">🔴 오를 것 {upRate}%</span>
              <span className="board-down">내릴 것 {100 - upRate}% 🔵</span>
            </div>
          </>
        ) : (
          <p className="board-ratio-empty">아직 의견이 없어요. 첫 의견을 남겨보세요.</p>
        )}
      </div>

      {/* ===== 글쓰기 ===== */}
      {board.loggedIn ? (
        <form className="board-write" onSubmit={submit}>
          <textarea
            className="board-textarea"
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={POST_MAX}
            rows={3}
            placeholder={`${stockName}에 대한 의견을 남겨보세요`}
          />
          <div className="board-write-foot">
            <div className="board-opinions">
              {OPINIONS.map((o) => (
                <button
                  key={o.value}
                  type="button"
                  className={`board-opinion ${opinion === o.value ? `board-opinion-on board-opinion-${o.value.toLowerCase()}` : ''}`}
                  onClick={() => setOpinion(opinion === o.value ? null : o.value)}   // 한 번 더 누르면 선택 취소
                >
                  {o.label}
                </button>
              ))}
            </div>
            <span className="board-length">{text.length} / {POST_MAX}</span>
            <button type="submit" className="btn btn-primary board-submit" disabled={!text.trim() || sending}>
              {sending ? '등록 중...' : '등록'}
            </button>
          </div>
          {writeError && <p className="board-error">{writeError}</p>}
        </form>
      ) : (
        <div className="board-login">
          <span>로그인하고 의견을 남겨보세요.</span>
          <Link to="/login" className="btn btn-primary board-submit">로그인</Link>
        </div>
      )}

      {/* ===== 글 목록 ===== */}
      {loading && board.posts.length === 0 && <p className="board-empty">토론 글을 불러오는 중...</p>}
      {listError && <p className="board-error">{listError}</p>}
      {!loading && !listError && board.posts.length === 0 && <p className="board-empty">아직 글이 없어요.</p>}

      {newCount > 0 && (
        <button type="button" className="board-new" onClick={() => loadPage(1)} disabled={loading}>
          🆕 새 글 {newCount}개 보기
        </button>
      )}

      <ul className="board-list">
        {board.posts.map((p) => (
          <li key={p.postId} className="board-post">

            {p.deleted ? (
              <p className="board-deleted">삭제된 글입니다.</p>
            ) : (
              <>
                <div className="board-post-meta">
                  {p.opinion === 'UP' && <span className="board-badge board-badge-up">오를 것</span>}
                  {p.opinion === 'DOWN' && <span className="board-badge board-badge-down">내릴 것</span>}
                  <strong>{p.nickname}</strong>
                  <span>· {timeAgo(p.insAt)}</span>
                  {p.canDelete && (
                    <button type="button" className="board-delete" onClick={() => removePost(p.postId)}>삭제</button>
                  )}
                </div>
                <p className="board-post-text">{p.content}</p>
              </>
            )}

            <button
              type="button"
              className={`board-toggle ${openId === p.postId ? 'board-toggle-on' : ''}`}
              onClick={() => setOpenId(openId === p.postId ? null : p.postId)}
            >
              💬 댓글 {p.commentCount}
            </button>

            {openId === p.postId && (
              <CommentArea
                postId={p.postId}
                loggedIn={board.loggedIn && !p.deleted}
                onCountChange={(count) => setCommentCount(p.postId, count)}
                refreshKey={commentTick}
              />
            )}
          </li>
        ))}
      </ul>

      {/* 새 글 버튼에 잡힌 글은 위에 붙을 글이라 더보기 계산에서 뺌 */}
      {board.posts.length + newCount < board.total && (
        <button type="button" className="board-more" onClick={() => loadPage(page + 1)} disabled={loading}>
          {loading ? '불러오는 중...' : `더보기 (${board.posts.length} / ${board.total})`}
        </button>
      )}
    </div>
  )
}

export default StockBoard