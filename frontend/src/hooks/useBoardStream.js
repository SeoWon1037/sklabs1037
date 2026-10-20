// ============================================
// 실시간 토론방 연결 (SSE, 브라우저 기본 EventSource)
//   useBoardStream(stockId, onEvent)   종목 토론 탭: 그 종목 글·댓글·삭제 + 보는 사람 수
//   useBoardStream(null, onEvent)      메인 화면: 전 종목 새 글·삭제
//
// - 서버: GET /api/stock/board/stream?stockId=  (응답을 끊지 않고 이벤트를 한 줄씩 보냄)
// - 이벤트: hello / viewers {viewers}, post {stockId}, comment·delete {stockId, postId}
// - 끊기면(배포·와이파이 전환) 브라우저가 몇 초 뒤 알아서 다시 연결 → 다시 붙으면 hello가 또 옴
// - 이벤트에는 "무엇이 바뀌었는지"만 있고, 화면이 기존 API로 목록을 다시 불러옴
// ============================================
import { useEffect, useRef, useState } from 'react'

const EVENTS = ['hello', 'viewers', 'post', 'comment', 'delete']

function useBoardStream(stockId, onEvent) {

  const [viewers, setViewers] = useState(null)   // 보는 사람 수 (메인은 안 씀)
  const [live, setLive] = useState(false)        // 지금 연결돼 있는지

  // 화면이 다시 그려질 때마다 연결을 새로 만들지 않도록, 처리 함수는 ref로 최신 것만 들고 있음
  const handler = useRef(onEvent)
  useEffect(() => { handler.current = onEvent })

  useEffect(() => {

    if (typeof EventSource === 'undefined') return undefined   // 아주 오래된 브라우저

    const url = stockId ? `/api/stock/board/stream?stockId=${stockId}` : '/api/stock/board/stream'
    const source = new EventSource(url)

    const listeners = EVENTS.map((type) => {
      const listener = (e) => {
        let data = {}
        try { data = JSON.parse(e.data) } catch { /* 빈 데이터 */ }
        if (data.viewers != null) setViewers(data.viewers)
        handler.current?.(type, data)
      }
      source.addEventListener(type, listener)
      return [type, listener]
    })

    source.onopen = () => setLive(true)
    source.onerror = () => setLive(false)      // 다시 연결하는 동안 잠깐 꺼짐

    // 화면을 떠나면 연결 끊기 (서버 쪽 보는 사람 수도 줄어듦)
    return () => {
      listeners.forEach(([type, listener]) => source.removeEventListener(type, listener))
      source.close()
    }

  }, [stockId])

  return { viewers, live }
}

export default useBoardStream
