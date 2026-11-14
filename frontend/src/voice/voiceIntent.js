// ============================================
// 🎤 들은 말 → 무엇을 할지 (순수 계산, 화면 · 서버 호출 없음)
// - AI를 쓰지 않고 규칙으로만 가림 → 빠르고, 비용이 없고, 엉뚱한 일을 하지 않음
// - 순서: ① 번역 ("화장실 어디예요 영어로") ② 장터 검색 ("아이폰 중고 찾아줘")
//         ③ 화면 열기 ("모의투자 열어줘") ④ 종목 ("삼성전자 주가 보여줘") ⑤ 나머지는 AI 챗봇에게 질문
// - 실제 동작(주문 · 글쓰기 · 삭제)은 하지 않음: 화면을 열거나 검색하거나 묻기만 함
// ============================================

// 화면 이름 (길게 맞는 것이 이김)
export const PAGES = [
  { to: '/', label: '투자 홈', names: ['투자 홈', '첫 화면', '메인 화면', '메인', '홈'] },
  { to: '/stock', label: '주식 종목', names: ['주식 종목', '종목 목록', '종목', '주식'] },
  { to: '/mock', label: '모의투자', names: ['모의투자', '모의 투자', '모의'] },
  { to: '/ai', label: 'AI 챗봇', names: ['ai 챗봇', 'ai 챗 봇', '에이아이 챗봇', '챗봇', '챗 봇', 'ai 비서', '에이아이 비서', '에이아이', '인공지능 비서', '인공지능', '비서'] },
  { to: '/whatif', label: '그때 샀더라면', names: ['그때 샀더라면', '그 때 샀더라면', '샀더라면'] },
  { to: '/market/new', label: '판매하기', names: ['판매하기', '상품 올리기', '물건 올리기'] },
  { to: '/market/my', label: '내 장터', names: ['내 장터', '내 상품'] },
  { to: '/market', label: '중고거래', names: ['중고거래', '중고 거래', '중고나라', '장터', '중고'] },
  { to: '/messages', label: '메시지', names: ['메시지', '메세지', '쪽지', '채팅'] },
  { to: '/calendar', label: '증시 일정', names: ['증시 일정', '증시일정', '일정', '달력'] },
  { to: '/league', label: 'AI 리그', names: ['ai 리그', '에이아이 리그', '리그'] },
  { to: '/predict', label: '예측 게임', names: ['예측 게임', '예측게임', '오를까 내릴까', '예측'] },
  { to: '/quiz', label: '뉴스 퀴즈', names: ['뉴스 퀴즈', '뉴스퀴즈', '퀴즈'] },
  { to: '/persona', label: '투자 성향 테스트', names: ['투자 성향 테스트', '투자 성향', '성향 테스트', '성향'] },
  { to: '/tree', label: '글자 트리', names: ['글자 트리', '크리스마스 트리', '크리스마스', '트리'] },
  { to: '/translate', label: '번역', names: ['번역기', '번역', '통역'] },
  { to: '/duel', label: '친구 대결', names: ['친구 대결', '대결'] },
  { to: '/activity', label: '내 활동', names: ['내 활동', '활동', '배지', '뱃지'] },
  { to: '/mypage', label: '마이페이지', names: ['마이페이지', '마이 페이지', '내 정보'] },
  { to: '/couple', label: '커플', names: ['커플'] },
  { to: '/pc', label: 'PC 프로그램 실행', names: ['pc 프로그램 실행', 'pc 연결', '피씨 연결', 'pc 도우미'] },
  { to: '/plans', label: '내 약속', names: ['내 약속', '약속 목록', '음성 브리핑', '브리핑 설정'] },
  { to: '/lotto', label: '오늘의 숫자', names: ['오늘의 숫자', '로또'] },
  { to: '/support', label: '문의', names: ['문의하기', '고객센터', '문의'] },
  { to: '/ideas', label: '기능 제안', names: ['기능 제안', '제안'] },
  { to: '/status', label: '서비스 상태', names: ['서비스 상태', '서버 상태'] },
  { to: '/app', label: '앱 설치', names: ['앱 설치', '어플 설치', '앱 깔기'] },
  { to: '/about', label: '만든 사람', names: ['만든 사람', '만든이', '소개'] },
  { to: '/login', label: '로그인', names: ['로그인'] },
]

const LANG_WORDS = { 영어: 'en', 일본어: 'ja', 일어: 'ja', 중국어: 'zh', 스페인어: 'es', 베트남어: 'vi', 한국어: 'ko', 한국말: 'ko', 우리말: 'ko' }
const LANG_PATTERN = Object.keys(LANG_WORDS).join('|')

// "열어줘 · 가줘 · 보여줘 · 켜줘 · 이동 · 틀어줘 …" 같은 시키는 말
const GO_WORDS = /(열어|가\s?줘|가자|가기|보여|켜|이동|틀어|띄워|들어가|넘어가|실행|으로\s?가|로\s?가)/
const ASK_WORDS = /(어때|어땠|왜|얼마|언제|누구|무슨|뭐야|뭔가|뭘까|알려|설명|추천|전망|이유|비교|요약|어떻게|될까|할까|\?)/

const tidy = (s) => String(s ?? '').replace(/[.!?。？！~]+$/g, '').replace(/\s+/g, ' ').trim()

// 시키는 말 · 조사 · 군말을 떼고 이름만
const strip = (s) => tidy(s)
  .replace(/^(sk\s?labs|에스케이\s?랩스|sk랩스|이\s?사이트|사이트|여기)\s*(에서|의)?\s*/i, '')   // "sklabs에서 커플 페이지 열어줘"
  .replace(/(좀|한번|한 번|빨리|지금|바로|직접|그냥|화면|페이지|메뉴|창)/g, ' ')
  .replace(/(으로|로)?\s*(이동해\s?줘|이동해|이동|가\s?줘|가자|가기|들어가\s?줘|들어가|넘어가\s?줘|넘어가)$/g, '')
  .replace(/(을|를)?\s*(열어\s?줘요?|열어\s?주세요|열어|보여\s?줘요?|보여\s?주세요|보여|켜\s?줘요?|켜\s?주세요|켜|틀어\s?줘|띄워\s?줘|실행해\s?줘|실행)$/g, '')
  .replace(/(을|를|으로|로)$/g, '')
  .replace(/\s+/g, ' ')
  .trim()

function findPage(name) {
  const n = name.toLowerCase()
  let best = null
  for (const page of PAGES) {
    for (const alias of page.names) {
      if (n.includes(alias) && (!best || alias.length > best.alias.length)) best = { page, alias }
    }
  }
  return best
}

/**
 * @param {string} heard 들은 말
 * @returns {{ type: 'go', to, label } | { type: 'translate', text, to } | { type: 'market', q } | { type: 'stock', q } | { type: 'ask', q } | { type: 'none' }}
 */
export function voiceIntent(heard) {
  const text = tidy(heard)
  if (text.length < 1) return { type: 'none' }

  // ⓪ 하트 보내기: "커플한테 하트 보내줘" → AI 챗봇이 실제로 보냄 (커플 화면으로 가지 않게 먼저 걸러 둠)
  if (/하트/.test(text) && /(보내|날려|쏴|전해|줘)/.test(text)) return { type: 'ask', q: text }

  // ① 번역: "… 영어로", "… 일본어로 번역해줘", "… 영어로 뭐라고 해"
  const tr = text.match(new RegExp(`^(.*?)\\s*(?:을|를|은|는)?\\s*(${LANG_PATTERN})로(?:는)?\\s*(?:번역|통역|뭐|어떻게|말|바꿔|해|알려)?.*$`))
  if (tr) {
    const body = tidy(tr[1]).replace(/(이거|이걸|이 말|다음 말)$/g, '').trim()
    return body.length >= 1 ? { type: 'translate', text: body, to: LANG_WORDS[tr[2]] } : { type: 'go', to: `/translate?to=${LANG_WORDS[tr[2]]}`, label: '번역' }
  }

  // ② 장터 검색: "아이폰 중고 찾아줘", "장터에서 자전거 검색"
  const mk = text.match(/^(?:장터|중고거래|중고)에서\s*(.+?)\s*(?:을|를)?\s*(?:찾아|검색)/) ?? text.match(/^(.+?)\s*(?:중고|매물)\s*(?:를|을)?\s*(?:찾아|검색|있어|있나|보여)/)
  if (mk && tidy(mk[1]).length >= 1) return { type: 'market', q: tidy(mk[1]) }

  // ③ 화면 열기: 이름만 말했거나("모의투자"), 시키는 말이 붙었을 때("모의투자 열어줘")
  const name = strip(text)
  const hit = findPage(name)
  const asking = ASK_WORDS.test(text)
  if (hit && !asking) {
    const extra = name.toLowerCase().replace(hit.alias, '').replace(/\s+/g, '')
    if (extra.length <= 1 || (GO_WORDS.test(text) && extra.length <= 3)) return { type: 'go', to: hit.page.to, label: hit.page.label }
  }

  // ④ 종목: "삼성전자 주가 보여줘", "카카오 차트", "현대차 보여줘"
  const st = text.match(/^(.+?)\s*(?:의)?\s*(?:주식|종목|주가|차트|시세|현재가)(?:\s|$)/)
  if (st && !asking && tidy(st[1]).length >= 2) return { type: 'stock', q: tidy(st[1]) }
  if (GO_WORDS.test(text) && !asking && name.length >= 2 && name.length <= 12 && !/\s.*\s/.test(name)) return { type: 'stock', q: name }

  // ⑤ 나머지는 AI 챗봇에게
  return { type: 'ask', q: text }
}
