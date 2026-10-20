package com.sklabs1037.sklabs1037.mock.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 모의투자 장 시간·호가 단위 규칙
 * - 지정가 체결·만료, 이번 주 랭킹이 모두 이 규칙에 기대고 있어서 경계값을 고정
 */
class MockMarketHoursTest {

    private static ZonedDateTime kst(String dateTime) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(dateTime), MockMarketHours.KST);
    }

    @Test
    void 정규장은_평일_9시부터_15시30분까지() {
        assertFalse(MockMarketHours.isOpen(kst("2026-10-20T08:59:59")));
        assertTrue(MockMarketHours.isOpen(kst("2026-10-20T09:00:00")));
        assertTrue(MockMarketHours.isOpen(kst("2026-10-20T15:30:00")));
        assertFalse(MockMarketHours.isOpen(kst("2026-10-20T15:30:01")));
    }

    @Test
    void 주말에는_장이_열리지_않음() {
        assertFalse(MockMarketHours.isOpen(kst("2026-10-24T10:00:00")));   // 토
        assertFalse(MockMarketHours.isOpen(kst("2026-10-25T10:00:00")));   // 일
    }

    @Test
    void 장_마감_전_주문은_오늘_마감_후는_다음_평일() {
        assertEquals(LocalDate.of(2026, 10, 20), MockMarketHours.tradingDay(kst("2026-10-20T08:00:00")));
        assertEquals(LocalDate.of(2026, 10, 20), MockMarketHours.tradingDay(kst("2026-10-20T15:30:00")));
        assertEquals(LocalDate.of(2026, 10, 21), MockMarketHours.tradingDay(kst("2026-10-20T15:31:00")));
    }

    @Test
    void 금요일_마감_후와_주말_주문은_월요일() {
        assertEquals(LocalDate.of(2026, 10, 26), MockMarketHours.tradingDay(kst("2026-10-23T16:00:00")));
        assertEquals(LocalDate.of(2026, 10, 26), MockMarketHours.tradingDay(kst("2026-10-24T11:00:00")));
        assertEquals(LocalDate.of(2026, 10, 26), MockMarketHours.tradingDay(kst("2026-10-25T23:59:00")));
    }

    @Test
    void 주간_랭킹_기준은_그_주_월요일() {
        assertEquals(LocalDate.of(2026, 10, 19), MockMarketHours.weekStart(LocalDate.of(2026, 10, 23)));
        assertEquals(LocalDate.of(2026, 10, 19), MockMarketHours.weekStart(LocalDate.of(2026, 10, 19)));
    }

    @Test
    void 호가_단위_경계값() {
        assertEquals(1, MockMarketHours.tickSize(new BigDecimal("1999")).intValue());
        assertEquals(5, MockMarketHours.tickSize(new BigDecimal("2000")).intValue());
        assertEquals(10, MockMarketHours.tickSize(new BigDecimal("5000")).intValue());
        assertEquals(50, MockMarketHours.tickSize(new BigDecimal("20000")).intValue());
        assertEquals(100, MockMarketHours.tickSize(new BigDecimal("50000")).intValue());
        assertEquals(500, MockMarketHours.tickSize(new BigDecimal("200000")).intValue());
        assertEquals(1000, MockMarketHours.tickSize(new BigDecimal("500000")).intValue());
        assertEquals(1000, MockMarketHours.tickSize(new BigDecimal("1265000")).intValue());
    }
}
