package com.sklabs1037.sklabs1037.stock.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🧾 종목 요약: 가격 흐름 계산 · 재료 없을 때 문장
 */
class StockBriefRulesTest {

    @Test
    void 등락률과_20일_위치() {

        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 21; i++) closes.add(100.0 + i);   // 100 → 120

        Map<String, Object> m = StockBriefService.priceStats(closes);

        assertEquals((Object) 120.0, m.get("close"));
        assertEquals((Object) 0.84, m.get("change1d"));     // 119 → 120
        assertEquals((Object) 20.0, m.get("change20d"));    // 100 → 120
        assertEquals((Object) 100L, m.get("position20"));   // 20일 최고
        assertTrue(StockBriefService.priceStats(List.of()).isEmpty());
        assertNull(StockBriefService.priceStats(List.of(1.0, 2.0)).get("change5d"));

    }

    @Test
    void 기사_글이_없으면_AI_말_대신_정해진_문장() {

        List<Map<String, Object>> lines = StockBriefService.lines(Map.of("news", "지어낸 말", "board", "", "price", "5일 +3%"), true, true);

        assertEquals("최근 7일 관련 기사가 없어요.", lines.get(0).get("text"));
        assertEquals("아직 토론 글이 거의 없어요.", lines.get(1).get("text"));
        assertEquals("5일 +3%", lines.get(2).get("text"));

    }
}
