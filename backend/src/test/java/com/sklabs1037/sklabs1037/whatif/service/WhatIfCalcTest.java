package com.sklabs1037.sklabs1037.whatif.service;

import com.sklabs1037.sklabs1037.whatif.service.WhatIfCalc.Point;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// ⏳ "그때 샀더라면" 계산 검증 (DB · 외부 호출 없이)
class WhatIfCalcTest {

    private static Point p(String day, long close) {
        return new Point(LocalDate.parse(day), BigDecimal.valueOf(close));
    }

    // 2026-01-05(월)부터 평일마다 한 칸씩
    private static List<Point> weekdays(long... closes) {
        List<Point> out = new ArrayList<>();
        LocalDate d = LocalDate.parse("2026-01-05");
        for (long c : closes) {
            out.add(new Point(d, BigDecimal.valueOf(c)));
            d = d.plusDays(d.getDayOfWeek().getValue() == 5 ? 3 : 1);
        }
        return out;
    }

    @Test
    void 두_배가_되면_수익률_100퍼센트() {

        Map<String, Object> r = WhatIfCalc.calc(weekdays(50_000, 60_000, 75_000, 90_000, 100_000), LocalDate.parse("2026-01-05"), 1_000_000);
        assertEquals(2_000_000L, r.get("value"));
        assertEquals(1_000_000L, r.get("profit"));
        assertEquals("100.00", String.valueOf(r.get("rate")));
        assertEquals("2026-01-05", r.get("buyDate"));
        assertEquals("2026-01-09", r.get("nowDate"));
        assertEquals(4L, r.get("days"));
        assertEquals(false, r.get("clamped"));

    }

    @Test
    void 떨어졌으면_손실로_나온다() {

        Map<String, Object> r = WhatIfCalc.calc(weekdays(80_000, 70_000, 60_000), LocalDate.parse("2026-01-05"), 1_000_000);
        assertEquals(750_000L, r.get("value"));
        assertEquals(-250_000L, r.get("profit"));
        assertEquals("-25.00", String.valueOf(r.get("rate")));

    }

    @Test
    void 원_단위와_소수_둘째_자리에서_반올림한다() {

        // 1,000,000 × 71,000 ÷ 69,000 = 1,028,985.507… / 수익률 2.8985…%
        Map<String, Object> r = WhatIfCalc.calc(weekdays(69_000, 71_000), LocalDate.parse("2026-01-05"), 1_000_000);
        assertEquals(1_028_986L, r.get("value"));
        assertEquals("2.90", String.valueOf(r.get("rate")));

    }

    @Test
    void 쉬는_날을_고르면_그_뒤_첫_거래일에_산다() {

        // 1/10(토)을 고름 → 1/12(월) 종가
        Map<String, Object> r = WhatIfCalc.calc(weekdays(100, 100, 100, 100, 100, 120, 150), LocalDate.parse("2026-01-10"), 100_000);
        assertEquals("2026-01-12", r.get("buyDate"));
        assertEquals("2026-01-10", r.get("askedDate"));
        assertEquals(125_000L, r.get("value"));
        assertEquals(false, r.get("clamped"));

    }

    @Test
    void 기록보다_이른_날을_고르면_가장_이른_날로_당기고_알려준다() {

        Map<String, Object> r = WhatIfCalc.calc(weekdays(100, 110), LocalDate.parse("2020-01-01"), 100_000);
        assertEquals("2026-01-05", r.get("buyDate"));
        assertEquals("2026-01-05", r.get("firstDate"));
        assertEquals(true, r.get("clamped"));
        assertEquals(110_000L, r.get("value"));

    }

    @Test
    void 가장_높았던_날과_낮았던_날() {

        Map<String, Object> r = WhatIfCalc.calc(weekdays(100, 125, 95, 110), LocalDate.parse("2026-01-05"), 100_000);
        Map<?, ?> peak = (Map<?, ?>) r.get("peak");
        Map<?, ?> trough = (Map<?, ?>) r.get("trough");
        assertEquals("2026-01-06", peak.get("date"));
        assertEquals(125_000L, peak.get("value"));
        assertEquals("2026-01-07", trough.get("date"));
        assertEquals(95_000L, trough.get("value"));

    }

    @Test
    void 하루_30퍼센트_움직임은_정상으로_본다() {

        Map<String, Object> up = WhatIfCalc.calc(weekdays(10_000, 13_000), LocalDate.parse("2026-01-05"), 100_000);
        assertEquals(130_000L, up.get("value"));
        Map<String, Object> down = WhatIfCalc.calc(weekdays(10_000, 7_000), LocalDate.parse("2026-01-05"), 100_000);
        assertEquals(70_000L, down.get("value"));

    }

    @Test
    void 액면분할처럼_가격_기준이_바뀐_날이_끼면_답을_내지_않는다() {

        // 50:1 분할 (2,500,000 → 50,000)
        List<Point> split = weekdays(2_400_000, 2_500_000, 50_000, 51_000);
        WhatIfCalc.Broken e = assertThrows(WhatIfCalc.Broken.class, () -> WhatIfCalc.calc(split, LocalDate.parse("2026-01-05"), 1_000_000));
        assertEquals("2026-01-07", e.getBreakDate().toString());
        assertEquals("2026-01-07", e.getRetryFrom().toString());

        // 알려준 날부터는 계산됨
        Map<String, Object> r = WhatIfCalc.calc(split, e.getRetryFrom(), 1_000_000);
        assertEquals(1_020_000L, r.get("value"));

    }

    @Test
    void 병합처럼_크게_뛴_날도_답을_내지_않는다() {

        assertThrows(WhatIfCalc.Broken.class, () -> WhatIfCalc.calc(weekdays(1_000, 1_000, 10_000, 10_100), LocalDate.parse("2026-01-05"), 1_000_000));

    }

    @Test
    void 기준이_여러_번_바뀌면_가장_최근_것을_알려준다() {

        WhatIfCalc.Broken e = assertThrows(WhatIfCalc.Broken.class,
                () -> WhatIfCalc.calc(weekdays(1_000, 100, 100, 1_000, 1_010), LocalDate.parse("2026-01-05"), 1_000_000));
        assertEquals("2026-01-08", e.getBreakDate().toString());

    }

    @Test
    void 산_날_앞에서_기준이_바뀐_것은_상관없다() {

        Map<String, Object> r = WhatIfCalc.calc(weekdays(2_500_000, 50_000, 55_000), LocalDate.parse("2026-01-06"), 1_000_000);
        assertEquals(1_100_000L, r.get("value"));

    }

    @Test
    void 금액_범위() {

        List<Point> pts = weekdays(100, 110);
        LocalDate d = LocalDate.parse("2026-01-05");
        assertThrows(IllegalArgumentException.class, () -> WhatIfCalc.calc(pts, d, 9_999));
        assertThrows(IllegalArgumentException.class, () -> WhatIfCalc.calc(pts, d, 10_000_000_001L));
        assertEquals(11_000L, WhatIfCalc.calc(pts, d, 10_000).get("value"));
        assertEquals(11_000_000_000L, WhatIfCalc.calc(pts, d, 10_000_000_000L).get("value"));

    }

    @Test
    void 거래일이_지나지_않았거나_기록이_없으면_안내() {

        List<Point> pts = weekdays(100, 110, 120);
        assertThrows(IllegalArgumentException.class, () -> WhatIfCalc.calc(pts, LocalDate.parse("2026-01-07"), 100_000));   // 마지막 날
        assertThrows(IllegalArgumentException.class, () -> WhatIfCalc.calc(pts, LocalDate.parse("2026-02-01"), 100_000));   // 그 뒤
        assertThrows(IllegalArgumentException.class, () -> WhatIfCalc.calc(weekdays(100), LocalDate.parse("2026-01-05"), 100_000));
        assertThrows(IllegalArgumentException.class, () -> WhatIfCalc.calc(null, LocalDate.parse("2026-01-05"), 100_000));

    }

    @Test
    void 시세_정리는_날짜순_같은_날은_뒤의_값_0원은_버림() {

        List<Point> cleaned = WhatIfCalc.clean(List.of(p("2026-01-07", 300), p("2026-01-05", 100), p("2026-01-06", 0), p("2026-01-05", 150)));
        assertEquals(2, cleaned.size());
        assertEquals("2026-01-05", cleaned.get(0).date().toString());
        assertEquals("150", cleaned.get(0).close().toPlainString());
        assertEquals("2026-01-07", cleaned.get(1).date().toString());

    }

    @Test
    void 차트는_120개_안쪽이고_처음과_끝이_들어간다() {

        long[] closes = new long[1000];
        for (int i = 0; i < closes.length; i++) closes[i] = 10_000 + i;
        Map<String, Object> r = WhatIfCalc.calc(weekdays(closes), LocalDate.parse("2026-01-05"), 1_000_000);
        List<?> series = (List<?>) r.get("series");
        assertTrue(series.size() <= WhatIfCalc.CHART_POINTS + 1);
        assertEquals("2026-01-05", ((Map<?, ?>) series.get(0)).get("d"));
        assertEquals(1_000_000L, ((Map<?, ?>) series.get(0)).get("v"));
        assertEquals(r.get("nowDate"), ((Map<?, ?>) series.get(series.size() - 1)).get("d"));
        assertEquals(r.get("value"), ((Map<?, ?>) series.get(series.size() - 1)).get("v"));

    }

    @Test
    void 긴_시세와_DB_시세_합치기() {

        // 긴 시세: 1/5~1/7 (1/7은 받던 순간의 장중 값 999), DB: 1/6~1/8
        List<Point> api = List.of(p("2026-01-05", 100), p("2026-01-06", 110), p("2026-01-07", 999));
        List<Point> db = List.of(p("2026-01-06", 111), p("2026-01-07", 120), p("2026-01-08", 130));
        List<Point> m = WhatIfService.merge(api, db);
        assertEquals(4, m.size());
        assertEquals("110", m.get(1).close().toPlainString());   // 마지막 날보다 앞: 긴 시세
        assertEquals("120", m.get(2).close().toPlainString());   // 긴 시세의 마지막 날: DB
        assertEquals("130", m.get(3).close().toPlainString());   // 그 뒤: DB

        assertEquals(3, WhatIfService.merge(List.of(), db).size());
        assertEquals(3, WhatIfService.merge(api, List.of()).size());

    }

    @Test
    void 토스_일봉_읽기() {

        Map<String, Object> ok = new HashMap<>(Map.of("timestamp", "2026-01-05T00:00:00+09:00", "closePrice", "71000"));
        Map<String, Object> bad = new HashMap<>(Map.of("timestamp", "어제", "closePrice", "1"));
        Map<String, Object> noPrice = new HashMap<>(Map.of("timestamp", "2026-01-06T00:00:00+09:00"));
        List<Point> pts = WhatIfService.toPoints(List.of(ok, bad, noPrice));
        assertEquals(1, pts.size());
        assertEquals("2026-01-05", pts.get(0).date().toString());
        assertTrue(WhatIfService.toPoints(null).isEmpty());

    }

    @Test
    void 입력_검사() {

        LocalDate today = LocalDate.parse("2026-11-10");
        assertEquals("005930", WhatIfService.symbol(" 005930 "));
        assertEquals("0001A0", WhatIfService.symbol("0001a0"));
        assertThrows(IllegalArgumentException.class, () -> WhatIfService.symbol("삼성전자"));
        assertThrows(IllegalArgumentException.class, () -> WhatIfService.symbol(null));
        assertEquals("2021-11-10", WhatIfService.date("2021-11-10", today).toString());
        assertThrows(IllegalArgumentException.class, () -> WhatIfService.date("2026-11-10", today));   // 오늘
        assertThrows(IllegalArgumentException.class, () -> WhatIfService.date("2016-11-09", today));   // 10년보다 전
        assertThrows(IllegalArgumentException.class, () -> WhatIfService.date("작년", today));
        assertThrows(IllegalArgumentException.class, () -> WhatIfService.date(null, today));

    }
}
