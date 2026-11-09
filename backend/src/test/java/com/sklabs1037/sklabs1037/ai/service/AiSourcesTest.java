package com.sklabs1037.sklabs1037.ai.service;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🧾 AI 비서 답변 근거: 도구 결과에서 기사 · 종목 · 계좌를 모으고, AI에게는 기사 주소를 안 보냄
 */
class AiSourcesTest {

    @Test
    void 뉴스와_종목을_모으고_이름을_채움() {

        List<Map<String, Object>> src = new ArrayList<>();
        AiChatServiceImpl.collectSources("get_price_history", Map.of("symbol", "005930", "days", 20), List.of(), src);
        AiChatServiceImpl.collectSources("find_stock", Map.of("query", "삼성"), List.of(Map.of("symbol", "005930", "name", "삼성전자")), src);
        AiChatServiceImpl.collectSources("search_news", Map.of("query", "삼성전자"), List.of(
                Map.of("title", "삼성전자 HBM 공급", "press", "연합뉴스", "link", "https://n.news/1"),
                Map.of("title", "같은 기사", "press", "연합뉴스", "link", "https://n.news/1")), src);
        AiChatServiceImpl.collectSources("get_my_mock_portfolio", Map.of(), Map.of(), src);

        assertEquals(3, src.size(), src.toString());
        assertEquals("삼성전자", src.get(0).get("title"), "코드로 먼저 들어온 종목에 나중에 알게 된 이름을 채움");
        assertEquals("/stock?symbol=005930", src.get(0).get("link"));
        assertEquals("NEWS", src.get(1).get("type"));
        assertEquals("/mock", src.get(2).get("link"));

    }

    @Test
    void 최대_8개() {

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 12; i++) rows.add(Map.of("title", "기사" + i, "press", "p", "link", "https://n/" + i));
        List<Map<String, Object>> src = new ArrayList<>();
        AiChatServiceImpl.collectSources("search_news", Map.of(), rows, src);
        assertEquals(AiChatServiceImpl.MAX_SOURCES, src.size());

    }

    @Test
    @SuppressWarnings("unchecked")
    void AI에게는_기사_주소를_빼고_보냄() {

        Object out = AiChatServiceImpl.withoutLinks(List.of(Map.of("title", "t", "link", "https://x")));
        assertFalse(((List<Map<String, Object>>) out).get(0).containsKey("link"));
        Object same = List.of(Map.of("symbol", "005930"));
        assertTrue(same == AiChatServiceImpl.withoutLinks(same), "주소가 없는 결과는 그대로");

    }
}
