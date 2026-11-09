package com.sklabs1037.sklabs1037.mock.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📈 수익률 비교: 내 기록 날짜에 KODEX 200 종가 맞추기
 */
class BenchmarkAlignTest {

    private static Map<String, Object> p(String date, boolean live) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date);
        if (live) m.put("live", true);
        return m;
    }

    private static Map<String, Object> r(String date, int close) {
        return Map.of("stockId", 9L, "date", date, "close", new BigDecimal(close));
    }

    @Test
    void 그날_종가_없으면_전_종가_마지막은_지금_가격() {

        List<Map<String, Object>> points = List.of(p("2026-11-02", false), p("2026-11-03", false), p("2026-11-05", false), p("2026-11-09", true));
        List<Map<String, Object>> rows = List.of(r("2026-10-30", 100), r("2026-11-02", 101), r("2026-11-04", 103), r("2026-11-06", 104));

        List<Object> out = MockTradeServiceImpl.alignCloses(points, rows, new BigDecimal(110));

        assertEquals(new BigDecimal(101), out.get(0));
        assertEquals(new BigDecimal(101), out.get(1), "11/3 종가 없음 → 11/2");
        assertEquals(new BigDecimal(103), out.get(2), "11/5 → 11/4");
        assertEquals(new BigDecimal(110), out.get(3), "지금 = 실시간");

    }

    @Test
    void 기록보다_지수가_늦게_시작하면_null() {

        List<Object> out = MockTradeServiceImpl.alignCloses(List.of(p("2026-01-02", false)), List.of(r("2026-02-01", 100)), null);
        assertNull(out.get(0));

    }
}
