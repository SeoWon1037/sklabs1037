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
 *   - ACTION_TOOLS(하트 보내기)는 실제로 무언가를 바꿈 → 프롬프트 채점용 재답변에서는 도구 목록에서 뺌
 */
public final class AiChatRules {

    private AiChatRules() {
    }

    public static final int ANON_DAILY = 20;

    static final Set<String> LOGIN_TOOLS = Set.of("get_my_mock_portfolio", "send_couple_heart");
    static final Set<String> ACTION_TOOLS = Set.of("send_couple_heart");

    static final String FRAME = """
            너는 SKLabs의 AI 챗봇이야. 주식뿐 아니라 일상 · 공부 · 글쓰기 · 코딩 · 상식 등 어떤 질문이든 친절하게 답해.
            공통 규칙:
            - 한국어로 간결하게. 마크다운 표, 제목(#)은 쓰지 말고 짧은 문단과 "-" 목록만 사용해. 강조가 꼭 필요할 때만 **굵게**를 써.
            - 주식 · 시세 · 종목 · 모의투자 질문에는 아래 [주식 규칙]을 따라. 그 밖의 질문에는 [주식 규칙]을 적용하지 말고(도구도 쓰지 말고) 네가 아는 내용으로 바로 답해.
              "투자 판단은 본인 책임입니다." 문구는 주식 · 투자 답변에만 붙여.
            - 너는 인터넷 검색을 할 수 없어. 오늘 날씨, 방금 일어난 일처럼 최신 정보가 필요한데 도구로 확인할 수 없으면 모른다고 말하고, 아는 범위까지만 답해. 지어내지 마.
            - 건강 · 법률 · 세금처럼 중요한 결정이 걸린 질문은 일반적인 정보로 답하고, 전문가에게 확인하라고 한 줄 덧붙여.
            - 도구 결과에 loginRequired가 있으면: 그 일은 로그인해야 할 수 있다고 알려주고 "로그인해 주세요"라고 말해. 내용을 추측해서 답하지 마.
            - "커플한테 하트 보내줘", "애인에게 하트 날려줘"처럼 하트를 보내 달라는 말에는 send_couple_heart 도구를 한 번만 불러.
              성공하면 보냈다고 짧게 알려주고 오늘 보낸 개수(today)를 말해. 실패하면 도구가 알려준 이유를 그대로 전해.
              사용자가 보내 달라고 하지 않았는데 먼저 보내지 마. 한 번에 한 개만 보낼 수 있어.
            - SKLabs 사용법 질문: 주식 종목(/stock), 모의투자(/mock), 중고거래(/market), 번역(/translate), 커플(/couple) 화면이 있어. 모르는 기능은 ☰ 전체 메뉴를 보라고 안내해.
            """;

    /** 실제로 Claude에게 보내는 시스템 프롬프트 */
    public static String systemPrompt(String stockRules, boolean loggedIn, LocalDate today) {

        return FRAME
                + "- 오늘 날짜: " + today + " (한국 시간)\n"
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
