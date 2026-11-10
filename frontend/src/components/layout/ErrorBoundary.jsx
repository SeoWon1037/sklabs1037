// ============================================
// 화면 그리기 실패를 받아주는 울타리
// - 어떤 화면이 그리다 오류를 내면, 전에는 사이트 전체가 흰 화면이 됐음
//   → 이제 메뉴(상단)는 그대로 두고 그 자리에 안내와 [다시 시도] · [첫 화면으로]를 보여줌
// - 오류는 서버로 보고 (🐞 errorReport.js → 관리자 > 화면 오류)
// - resetKey(주소)가 바뀌면 — 다른 화면으로 가면 — 자동으로 풀림
// ============================================
import { Component } from 'react'
import { reportError } from '../../errorReport'

class ErrorBoundary extends Component {

  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidUpdate(prev) {
    if (this.state.failed && prev.resetKey !== this.props.resetKey) this.setState({ failed: false })
  }

  componentDidCatch(error, info) {
    reportError(/dynamically imported module|Loading chunk|Importing a module script failed/i.test(String(error?.message)) ? 'CHUNK' : 'RENDER', error, info?.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children

    return (
      <main className="page-error" role="alert">
        <span aria-hidden="true">🛠️</span>
        <h1>화면을 보여주다 문제가 생겼어요</h1>
        <p>잠깐의 문제일 수 있어요. 다시 시도해도 같으면 조금 뒤에 들어와 주세요. 문제는 자동으로 기록됐어요.</p>
        <div>
          <button type="button" onClick={() => this.setState({ failed: false })}>다시 시도</button>
          <a href="/">첫 화면으로</a>
        </div>
      </main>
    )
  }
}

export default ErrorBoundary
