package com.sklabs1037.sklabs1037.ai.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🤖 AI 챗봇 고정 규칙 테스트
 */
class AiChatRulesTest {

    @Test
    void 프롬프트는_고정_틀_뒤에_주식_규칙이_붙는다() {

        String p = AiChatRules.systemPrompt("- 숫자는 도구로만", true, LocalDate.of(2026, 11, 14));

        assertTrue(p.startsWith("너는 SKLabs의 AI 챗봇이야"));
        assertTrue(p.indexOf("[주식 규칙]") < p.indexOf("- 숫자는 도구로만"));
        assertTrue(p.contains("오늘 날짜: 2026-11-14 토요일"));
        assertTrue(p.contains("add_plan"));
        assertTrue(p.contains("get_weather"));
        assertTrue(p.contains("사용자 상태: 로그인함"));
        assertTrue(p.contains("send_couple_heart"));
        assertTrue(p.contains("loginRequired"));

    }

    @Test
    void 로그인하지_않았으면_프롬프트에_표시된다() {

        String p = AiChatRules.systemPrompt(null, false, LocalDate.of(2026, 11, 14));

        assertTrue(p.contains("사용자 상태: 로그인하지 않음"));
        assertTrue(p.endsWith("[주식 규칙]\n\n"));

    }

    @Test
    void 기본_주식_규칙에는_개선_검사가_요구하는_문구가_남아_있다() {

        for (String phrase : PromptImproveService.REQUIRED_PHRASES) {
            assertTrue(AiChatServiceImpl.BASE_PROMPT.contains(phrase), phrase);
        }

    }

    @Test
    void 내_계좌와_하트는_로그인이_필요하다() {

        assertTrue(AiChatRules.needsLogin("get_my_mock_portfolio", null));
        assertTrue(AiChatRules.needsLogin("send_couple_heart", null));
        assertFalse(AiChatRules.needsLogin("send_couple_heart", 7L));
        assertTrue(AiChatRules.needsLogin("add_plan", null));
        assertTrue(AiChatRules.needsLogin("list_plans", null));
        assertFalse(AiChatRules.needsLogin("get_weather", null));
        assertFalse(AiChatRules.needsLogin("find_stock", null));
        assertFalse(AiChatRules.needsLogin("search_news", null));

    }

    @Test
    void 로그인_필요_결과에는_표시와_안내가_들어_있다() {

        Map<String, Object> r = AiChatRules.loginRequired("send_couple_heart");

        assertEquals(true, r.get("loginRequired"));
        assertTrue(String.valueOf(r.get("message")).contains("로그인"));
        assertTrue(String.valueOf(AiChatRules.loginRequired("get_my_mock_portfolio").get("message")).contains("모의투자"));

    }

    @Test
    void 채점용_도구_목록에서는_하트가_빠진다() {

        List<Map<String, Object>> tools = List.of(Map.of("name", "find_stock"), Map.of("name", "send_couple_heart"), Map.of("name", "search_news"),
                Map.of("name", "add_plan"), Map.of("name", "delete_plan"), Map.of("name", "list_plans"));

        List<Map<String, Object>> safe = AiChatRules.withoutActions(tools);

        assertEquals(3, safe.size());
        assertTrue(safe.stream().noneMatch(t -> "send_couple_heart".equals(t.get("name"))));

    }

    @Test
    void 로그인_없이는_하루_20번까지() {

        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < AiChatRules.ANON_DAILY; i++) assertTrue(AiChatRules.allowAnonymous(counts, "2026-11-14", "1.1.1.1"));

        assertFalse(AiChatRules.allowAnonymous(counts, "2026-11-14", "1.1.1.1"));
        assertTrue(AiChatRules.allowAnonymous(counts, "2026-11-14", "2.2.2.2"));   // 다른 사람은 따로
        assertTrue(AiChatRules.allowAnonymous(counts, "2026-11-15", "1.1.1.1"));   // 다음 날은 다시

    }

    @Test
    void 사용량_표가_커지면_지난_날짜를_비운다() {

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 5001; i++) counts.put("2026-11-13:" + i, 1);

        assertTrue(AiChatRules.allowAnonymous(counts, "2026-11-14", "1.1.1.1"));
        assertEquals(1, counts.size());

    }
}
