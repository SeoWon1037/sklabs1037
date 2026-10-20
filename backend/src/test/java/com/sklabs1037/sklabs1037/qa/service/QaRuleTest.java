package com.sklabs1037.sklabs1037.qa.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🤖 QA 봇 규칙 판정
 * - 페이지 오류·5xx·기능 흐름 실패 → 심각(FAIL), 콘솔 에러·가로 넘침·느림·404 → 주의(WARN), 깨끗하면 PASS
 */
class QaRuleTest {

    private static Map<String, Object> clean(String key) {
        return Map.of("key", key, "loadMs", 1200, "overflowPx", 0,
                "consoleErrors", List.of(), "pageErrors", List.of(), "failedRequests", List.of());
    }

    @Test
    void 깨끗하면_통과() {

        List<Map<String, Object>> f = QaBotService.ruleFindings(List.of(clean("home-mobile"), clean("home-pc")));

        assertTrue(f.isEmpty());
        assertEquals("PASS", QaBotService.status(f));

    }

    @Test
    void 서버_오류와_흐름_실패는_심각() {

        Map<String, Object> broken = Map.of("key", "formula-pc", "loadMs", 900, "overflowPx", 0,
                "consoleErrors", List.of(), "pageErrors", List.of(),
                "failedRequests", List.of(Map.of("url", "/api/beauty/formula/check", "status", 500)),
                "flow", Map.of("name", "처방 검토 예시", "ok", false, "message", "기다려도 결과가 나오지 않았어요"));

        List<Map<String, Object>> f = QaBotService.ruleFindings(List.of(broken));

        assertEquals(2, f.stream().filter(x -> "HIGH".equals(x.get("severity"))).count());
        assertEquals("FAIL", QaBotService.status(f));

    }

    @Test
    void 콘솔_에러_넘침_느림은_주의() {

        Map<String, Object> slow = Map.of("key", "stock-mobile", "loadMs", 8200, "overflowPx", 37,
                "consoleErrors", List.of("TypeError: x is undefined"), "pageErrors", List.of(),
                "failedRequests", List.of(Map.of("url", "/old.png", "status", 404)));

        List<Map<String, Object>> f = QaBotService.ruleFindings(List.of(slow));

        assertEquals(4, f.size());
        assertTrue(f.stream().allMatch(x -> "MEDIUM".equals(x.get("severity"))));
        assertEquals("WARN", QaBotService.status(f));

    }
}
