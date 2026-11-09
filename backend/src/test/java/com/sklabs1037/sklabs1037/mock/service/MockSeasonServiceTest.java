package com.sklabs1037.sklabs1037.mock.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🏆 주간 시즌: 사람만 TOP 3, 같은 수익률은 같은 등수
 */
class MockSeasonServiceTest {

    private static Map<String, Object> r(long id, String rate, boolean ai) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", id);
        m.put("nickname", "u" + id);
        m.put("returnRate", new BigDecimal(rate));
        m.put("totalAsset", new BigDecimal(30_000_000));
        m.put("ai", ai);
        return m;
    }

    @Test
    void AI는_빼고_사람_TOP3() {

        List<Map<String, Object>> w = MockSeasonService.winners(List.of(
                r(1, "3.10", false), r(2, "9.00", true), r(3, "5.20", false), r(4, "-1.00", false), r(5, "0.50", false)));

        assertEquals(List.of(3L, 1L, 5L), w.stream().map(x -> x.get("userId")).toList());
        assertEquals(List.of(1, 2, 3), w.stream().map(x -> x.get("rank")).toList());

    }

    @Test
    void 같은_수익률은_같은_등수_3등까지() {

        List<Map<String, Object>> w = MockSeasonService.winners(List.of(
                r(1, "2.00", false), r(2, "2.00", false), r(3, "1.00", false), r(4, "1.00", false), r(5, "0.10", false)));

        assertEquals(List.of(1, 1, 3, 3), w.stream().map(x -> x.get("rank")).toList(), "4명 (3등 동점 둘), 5번째는 5등이라 빠짐");

    }

    @Test
    void 참가자_없으면_빈_목록() {

        assertTrue(MockSeasonService.winners(List.of()).isEmpty());

    }
}
