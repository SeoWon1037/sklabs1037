package com.sklabs1037.sklabs1037.fx.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 💱 달러 기준 환율 → 원화 환율 · 전날 대비
 */
class FxRulesTest {

    private static Map<String, Object> r(String date, String quote, double rate) {
        return Map.of("date", date, "base", "USD", "quote", quote, "rate", rate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 원화로_바꾸고_엔은_100엔_기준() {

        Map<String, Object> out = FxService.compute(List.of(
                r("2026-10-19", "KRW", 1332.44), r("2026-10-19", "JPY", 158.18), r("2026-10-19", "EUR", 0.9),
                r("2026-10-20", "KRW", 1330.18), r("2026-10-20", "JPY", 158.30), r("2026-10-20", "EUR", 0.9)));

        assertEquals("2026-10-20", out.get("date"));
        List<Map<String, Object>> rates = (List<Map<String, Object>>) out.get("rates");

        Map<String, Object> usd = rates.get(0);
        assertEquals("USD", usd.get("code"));
        assertEquals((Object) 1330.18, usd.get("rate"));
        assertEquals((Object) (-2.26), usd.get("change"));

        Map<String, Object> jpy = rates.get(1);
        assertEquals("JPY", jpy.get("code"));
        assertEquals((Object) 840.29, jpy.get("rate"));           // 1330.18 / 158.30 × 100

        Map<String, Object> eur = rates.get(2);
        assertEquals((Object) 1477.98, eur.get("rate"));          // 1330.18 / 0.9
        assertEquals(2, ((List<?>) eur.get("history")).size());

        // 데이터가 없는 통화(CNY, GBP)는 빠짐
        assertEquals(3, rates.size());

    }

    @Test
    void 원화가_없으면_에러() {

        assertThrows(IllegalStateException.class, () -> FxService.compute(List.of(r("2026-10-20", "JPY", 158.3))));

    }
}
