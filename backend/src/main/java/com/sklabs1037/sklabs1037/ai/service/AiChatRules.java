package com.sklabs1037.sklabs1037.ai.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 🤖 AI 챗봇의 고정 규칙 (순수 계산 → 테스트 가능)
 *
 * 시스템 프롬프트 = [틀] + [주식 규칙]
 *   - 틀(FRAME)       : 무엇이든 답하기 · 로그인 안내 · 하트 보내기. 서버에 고정 (🔁 프롬프트 개선이 바꿀 수 없음)
 *   - 주식 규칙        : AiChatServiceImpl.BASE_PROMPT 또는 관리자가 승인한 개선 버전 (주식 질문일 때만 적용)
 *
 * 로그인
 *   - 로그인하지 않아도 질문할 수 있음 (IP당 하루 ANON_DAILY번)
 *   - 내 계좌 · 하트처럼 "누구인지" 알아야 하는 도구는 LOGIN_TOOLS → 로그인 전이면 실행하지 않고 loginRequired를 돌려줌
 *
 * 행동 도구
 *   - ACTION_TOOLS(하트 보내기 · 약속 추가 · 삭제)는 실제로 무언가를 바꿈 → 프롬프트 채점용 재답변과 체험 계정에서는 도구 목록에서 뺌
 */
public final class AiChatRules {

    private AiChatRules() {
    }

    public static final int ANON_DAILY = 20;

    static final Set<String> LOGIN_TOOLS = Set.of("get_my_mock_portfolio", "send_couple_heart", "send_couple_message", "get_couple_info", "add_plan", "list_plans", "delete_plan");
    static final Set<String> ACTION_TOOLS = Set.of("send_couple_heart", "send_couple_message", "add_plan", "delete_plan");

    static final String FRAME = """
            너는 SKLabs의 AI 챗봇이야. 주식뿐 아니라 일상 · 공부 · 글쓰기 · 코딩 · 상식 등 어떤 질문이든 친절하게 답해.
            공통 규칙:
            - 한국어로 간결하게. 마크다운 표, 제목(#)은 쓰지 말고 짧은 문단과 "-" 목록만 사용해. 강조가 꼭 필요할 때만 **굵게**를 써.
            - 주식 · 시세 · 종목 · 모의투자 질문에는 아래 [주식 규칙]을 따라. 그 밖의 질문에는 [주식 규칙]을 적용하지 말고(도구도 쓰지 말고) 네가 아는 내용으로 바로 답해.
              "투자 판단은 본인 책임입니다." 문구는 주식 · 투자 답변에만 붙여.
            - 사용자는 글로 쓰기도 하고 마이크로 말하기도 해 (말한 내용이 글자로 바뀌어 너에게 오고, 네 답은 소리로 읽어 줄 수 있어).
              그러니 "내 말 들려?", "듣고 있어?" 같은 말에는 목소리를 못 듣는다고 하지 말고 "네, 잘 들려요. 무엇을 도와드릴까요?"처럼 답해.
            - 너는 인터넷 검색을 할 수 없어. 방금 일어난 일, 실시간 교통처럼 최신 정보가 필요한데 도구로 확인할 수 없으면 모른다고 말하고, 아는 범위까지만 답해. 지어내지 마.
            - 날씨 질문("오늘 날씨 어때?", "수원 비 와?")은 get_weather로 조회한 값만 말해. 서울 · 경기권(도구의 supportedRegions)만 되고, 다른 지역은 아직 지원하지 않는다고 말해. 지역을 말하지 않으면 서울로 조회해.
            - "커플 페이지 열어줘", "sklabs에서 모의투자 화면으로 가줘"처럼 SKLabs의 화면을 열어 달라는 말에는 open_page 도구를 불러.
              도구가 성공하면 화면이 자동으로 열리니까 "○○ 화면을 열게요."라고 한 줄만 답해. 주소를 글로 안내하지 마.
              표에 없는 화면이면 열 수 없다고 말하고 ☰ 전체 메뉴를 안내해.
            - 앞에 나눈 대화가 함께 올 수 있어. "그럼 그 종목 뉴스는?", "그거 다시 설명해줘"처럼 앞 대화를 가리키는 말은 그 흐름을 이어서 답해.
              단, 앞 대화에 있던 주가 · 수익률 같은 숫자는 그대로 다시 쓰지 말고 필요하면 도구로 다시 조회해.
            - 중고거래 상품을 찾는 질문("아이폰 15 중고 있어?", "30만 원 이하 자전거 찾아줘")은 search_market으로 찾아 답해.
              "- 제목 · 가격 · 지역" 한 줄씩 최대 5개, 없으면 없다고 말해. 가격 조건은 max_price(원)로 넣어. 무료 나눔은 0원이야.
            - 약속을 잡아 달라는 말("토요일 저녁 7시 강남에서 민지랑 약속 잡아줘")에는 add_plan을 불러. 날짜는 아래 "오늘 날짜"를 기준으로 YYYY-MM-DD로, 시간은 24시간 HH:mm으로 바꿔 넣어.
              "저녁 7시"는 19:00, "오후 3시 반"은 15:30. 시간을 말하지 않았으면 time을 비워. 날짜를 알 수 없으면 도구를 부르지 말고 날짜를 되물어.
              커플(애인 · 여자친구 · 남자친구)과 함께하는 약속이나 데이트라고 하면 shared=true로 넣어 (상대의 약속 목록과 알림에도 나옴).
              "매주 화요일 7시 헬스"처럼 매주 반복이면 weekly=true로 넣고 date는 가장 가까운 그 요일로 해 (12주치가 만들어짐).
              저장되면 언제 · 어디서 · 무엇인지 한 줄로 확인해 주고, 하루 전과 당일 오전 9시에 알려준다고 말해.
            - "커플한테 사랑해라고 보내줘", "민지한테 오늘 늦는다고 전해줘"처럼 커플에게 말을 전해 달라고 하면 send_couple_message를 한 번만 불러.
              text에는 사용자가 전하려는 말만 넣어 (100자까지, 네가 내용을 지어내거나 덧붙이지 마). 전할 말이 분명하지 않으면 뭐라고 보낼지 되물어.
              성공하면 누구에게 뭐라고 보냈는지 한 줄로 알려줘.
            - "커플 생일 얼마나 남았어?", "우리 며칠 됐어?", "다음 기념일 언제야?" 같은 커플 질문은 get_couple_info로 조회해서 답해.
              생일은 dDay(남은 날)와 날짜를 함께 말해. 생일이 아직 입력되지 않았으면(null) 커플 화면(/couple)에서 생일을 넣어 달라고 안내해.
            - "내 약속 뭐 있어?", "이번 주 일정 알려줘"는 list_plans로 조회해서 답해. 약속을 지워 달라고 하면 list_plans로 id를 찾은 뒤 delete_plan을 불러. 어느 약속인지 분명하지 않으면 되물어.
            - 건강 · 법률 · 세금처럼 중요한 결정이 걸린 질문은 일반적인 정보로 답하고, 전문가에게 확인하라고 한 줄 덧붙여.
            - 도구 결과에 loginRequired가 있으면: 그 일은 로그인해야 할 수 있다고 알려주고 "로그인해 주세요"라고 말해. 내용을 추측해서 답하지 마.
            - "커플한테 하트 보내줘", "애인에게 하트 날려줘"처럼 하트를 보내 달라는 말에는 send_couple_heart 도구를 한 번만 불러.
              성공하면 보냈다고 짧게 알려주고 오늘 보낸 개수(today)를 말해. 실패하면 도구가 알려준 이유를 그대로 전해.
              사용자가 보내 달라고 하지 않았는데 먼저 보내지 마. 한 번에 한 개만 보낼 수 있어.
            - SKLabs 사용법 질문: 주식 종목(/stock), 모의투자(/mock), 중고거래(/market), 번역(/translate), 커플(/couple), 내 약속 · 음성 브리핑(/plans) 화면이 있어. 모르는 기능은 ☰ 전체 메뉴를 보라고 안내해.
            """;

    // 🧭 화면 열기: 챗봇이 열 수 있는 화면 (이 표에 있는 주소로만 이동 → AI가 아무 주소나 만들 수 없음)
    public static final Map<String, String[]> PAGES = pages();

    private static Map<String, String[]> pages() {

        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("home", new String[]{"/", "첫 화면"});
        m.put("stock", new String[]{"/stock", "주식 종목"});
        m.put("mock", new String[]{"/mock", "모의투자"});
        m.put("market", new String[]{"/market", "중고거래"});
        m.put("market_sell", new String[]{"/market/new", "판매하기"});
        m.put("market_my", new String[]{"/market/my", "내 장터"});
        m.put("messages", new String[]{"/messages", "메시지"});
        m.put("couple", new String[]{"/couple", "커플"});
        m.put("plans", new String[]{"/plans", "내 약속 · 음성 브리핑"});
        m.put("translate", new String[]{"/translate", "번역"});
        m.put("calendar", new String[]{"/calendar", "증시 일정"});
        m.put("whatif", new String[]{"/whatif", "그때 샀더라면"});
        m.put("league", new String[]{"/league", "AI 리그"});
        m.put("predict", new String[]{"/predict", "예측 게임"});
        m.put("quiz", new String[]{"/quiz", "뉴스 퀴즈"});
        m.put("persona", new String[]{"/persona", "투자 성향 테스트"});
        m.put("tree", new String[]{"/tree", "글자 트리"});
        m.put("duel", new String[]{"/duel", "친구 대결"});
        m.put("activity", new String[]{"/activity", "내 활동"});
        m.put("mypage", new String[]{"/mypage", "마이페이지"});
        m.put("login", new String[]{"/login", "로그인"});
        m.put("support", new String[]{"/support", "문의하기"});
        m.put("about", new String[]{"/about", "만든 사람"});
        return java.util.Collections.unmodifiableMap(m);

    }

    /** open_page 도구 결과: 표에 있는 화면이면 { open: 주소, label } , 아니면 예외 */
    public static Map<String, Object> openPage(Object page) {

        String[] p = page == null ? null : PAGES.get(String.valueOf(page).strip().toLowerCase());
        if (p == null) throw new IllegalArgumentException("열 수 없는 화면이에요. 열 수 있는 화면: " + String.join(", ", PAGES.keySet()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", p[0]);
        m.put("label", p[1]);
        return m;

    }

    // 🧠 대화 기억: 화면이 보내 준 앞 대화 중 최근 몇 개만, 길이를 잘라서 Claude에게 같이 보냄
    public static final int HISTORY_MAX = 6;          // 메시지 수 (질문 3 + 답 3)
    public static final int HISTORY_TEXT = 500;       // 한 메시지 글자 수

    /**
     * 화면이 보낸 앞 대화 → Claude 메시지 목록 (없거나 이상하면 빈 목록)
     * - role은 user / assistant만, 글이 아닌 것은 버림
     * - user로 시작하고 assistant로 끝나게, 같은 역할이 이어지지 않게 다듬음 (바로 뒤에 이번 질문(user)이 붙음)
     * - 앞 대화는 참고용: 도구 결과는 들어 있지 않으므로 숫자는 다시 조회해야 함 (FRAME에 적혀 있음)
     */
    public static List<Map<String, Object>> history(Object raw) {

        List<Map<String, Object>> out = new java.util.ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;

        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String role = String.valueOf(m.get("role"));
            Object text = m.get("text") != null ? m.get("text") : m.get("content");
            if (!(text instanceof String s) || s.isBlank()) continue;
            if (!role.equals("user") && !role.equals("assistant")) continue;

            String body = s.strip();
            if (body.length() > HISTORY_TEXT) body = body.substring(0, HISTORY_TEXT) + "…";

            // 같은 역할이 이어지면 뒤의 것으로 바꿈
            if (!out.isEmpty() && role.equals(out.get(out.size() - 1).get("role"))) out.remove(out.size() - 1);
            out.add(Map.of("role", role, "content", body));
        }

        while (out.size() > HISTORY_MAX) out.remove(0);
        while (!out.isEmpty() && !"user".equals(out.get(0).get("role"))) out.remove(0);
        while (!out.isEmpty() && !"assistant".equals(out.get(out.size() - 1).get("role"))) out.remove(out.size() - 1);
        return out;

    }

    /** 실제로 Claude에게 보내는 시스템 프롬프트 */
    public static String systemPrompt(String stockRules, boolean loggedIn, LocalDate today) {

        return FRAME
                + "- 오늘 날짜: " + today + " " + com.sklabs1037.sklabs1037.plan.service.PlanRules.weekday(today.getDayOfWeek()) + " (한국 시간)\n"
                + "- 사용자 상태: " + (loggedIn ? "로그인함" : "로그인하지 않음 (내 계좌 · 하트 같은 개인 기능은 로그인이 필요함)") + "\n"
                + "\n[주식 규칙]\n" + (stockRules == null ? "" : stockRules.strip()) + "\n";

    }

    /** 로그인이 필요한 도구를 로그인 없이 불렀을 때 AI에게 돌려줄 결과 */
    public static Map<String, Object> loginRequired(String tool) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("loginRequired", true);
        m.put("message", switch (tool) {
            case "get_my_mock_portfolio" -> "내 모의투자 계좌는 로그인해야 볼 수 있어요.";
            case "send_couple_heart" -> "하트는 로그인해야 보낼 수 있어요.";
            case "get_couple_info" -> "커플 정보는 로그인해야 볼 수 있어요.";
            case "send_couple_message" -> "한마디는 로그인해야 보낼 수 있어요.";
            case "add_plan", "list_plans", "delete_plan" -> "약속은 로그인해야 저장하고 볼 수 있어요.";
            default -> "로그인해야 쓸 수 있는 기능이에요.";
        });
        return m;

    }

    public static boolean needsLogin(String tool, Long userId) {

        return userId == null && LOGIN_TOOLS.contains(tool);

    }

    /** 채점용 재답변처럼 실제 행동을 하면 안 되는 곳에서 쓸 도구 목록 */
    public static List<Map<String, Object>> withoutActions(List<Map<String, Object>> tools) {

        return tools.stream().filter(t -> !ACTION_TOOLS.contains(String.valueOf(t.get("name")))).toList();

    }

    /** 로그인하지 않은 사람의 하루 사용량: 남았으면 true (counts는 "날짜:IP" → 횟수) */
    public static boolean allowAnonymous(Map<String, Integer> counts, String day, String ip) {

        if (counts.size() > 5000) counts.keySet().removeIf(k -> !k.startsWith(day + ":"));
        String key = day + ":" + ip;
        int used = counts.getOrDefault(key, 0);
        if (used >= ANON_DAILY) return false;
        counts.put(key, used + 1);
        return true;

    }
}
