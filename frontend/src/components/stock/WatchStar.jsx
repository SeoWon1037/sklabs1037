// ============================================
// 관심 종목 별 버튼 (☆ / ★)
// - 상태는 부모(주식 종목 화면)가 관리하고, 여기서는 표시 + 클릭만
// ============================================
import './Watchlist.css'

function WatchStar({ active, onClick }) {
  return (
    <button
      type="button"
      className={`watch-star ${active ? 'watch-star-on' : ''}`}
      onClick={onClick}
      aria-label={active ? '관심 종목에서 빼기' : '관심 종목에 추가'}
      title={active ? '관심 종목에서 빼기' : '관심 종목에 추가'}
    >
      {active ? '★' : '☆'}
    </button>
  )
}

export default WatchStar