package com.sklabs1037.sklabs1037.couple.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 💕 함께한 날 · 다가오는 기념일 · 시작일 확인
 */
class CoupleRulesTest {

    private static final LocalDate START = LocalDate.parse("2026-04-04");

    @Test
    void 시작일이_1일() {

        assertEquals(1L, CoupleService.days(START, START));
        assertEquals(200L, CoupleService.days(START, START.plusDays(199)));

    }

    @Test
    void 다가오는_기념일() {

        // 첫날 → 100일 (시작일 + 99일), D-99
        Map<String, Object> first = CoupleService.nextMilestone(START, START);
        assertEquals("100일", first.get("label"));
        assertEquals("2026-07-12", first.get("date"));
        assertEquals(99L, first.get("dDay"));

        // 오늘이 200일이면 D-day 0
        Map<String, Object> today200 = CoupleService.nextMilestone(START, START.plusDays(199));
        assertEquals("200일", today200.get("label"));
        assertEquals(0L, today200.get("dDay"));

        // 300일(2027-01-29)보다 1주년(2027-04-04)이 늦으니 300일 → 지나면 1주년
        assertEquals("300일", CoupleService.nextMilestone(START, LocalDate.parse("2027-01-01")).get("label"));
        assertEquals("1주년", CoupleService.nextMilestone(START, LocalDate.parse("2027-02-01")).get("label"));
        assertEquals(0L, CoupleService.nextMilestone(START, LocalDate.parse("2027-04-04")).get("dDay"));   // 1주년 당일

    }

    @Test
    void 시작일은_오늘이나_과거() {

        LocalDate today = LocalDate.parse("2026-10-20");
        assertEquals(today, CoupleService.parseStart("2026-10-20", today));
        assertThrows(IllegalArgumentException.class, () -> CoupleService.parseStart("2026-10-21", today));
        assertThrows(IllegalArgumentException.class, () -> CoupleService.parseStart("어제", today));

    }
}
