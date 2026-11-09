package com.sklabs1037.sklabs1037.calendar.service;

import com.sklabs1037.sklabs1037.calendar.service.MarketCalendar.Event;
import com.sklabs1037.sklabs1037.calendar.service.MarketCalendar.Kind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📅 증시 일정: 만기일 규칙 · FOMC 한국 시간 · 휴장일
 */
class MarketCalendarTest {

    @Test
    void 옵션_만기일은_둘째_목요일() {

        assertEquals(LocalDate.of(2026, 11, 12), MarketCalendar.expiry(YearMonth.of(2026, 11)));
        assertEquals(LocalDate.of(2026, 12, 10), MarketCalendar.expiry(YearMonth.of(2026, 12)));

    }

    @Test
    void 만기일이_휴장일이면_그_전_거래일() {

        // 2026년 2월 둘째 목요일 = 2/12 (휴장 아님), 9월 둘째 목요일 = 9/10
        assertEquals(LocalDate.of(2026, 2, 12), MarketCalendar.expiry(YearMonth.of(2026, 2)));
        assertTrue(MarketCalendar.isTradingDay(MarketCalendar.expiry(YearMonth.of(2026, 9))));

    }

    @Test
    void FOMC는_한국_시간으로_다음날_새벽() {

        // 미국 서머타임 끝난 뒤 (12/9 오후 2시 EST) → 한국 12/10 04:00
        List<Event> dec = MarketCalendar.between(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
        Event fomc = dec.stream().filter(e -> e.kind() == Kind.FOMC).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 12, 10), fomc.date());
        assertEquals("04:00", fomc.time());

        // 서머타임 중 (9/16 오후 2시 EDT) → 한국 9/17 03:00
        Event sep = MarketCalendar.between(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).stream()
                .filter(e -> e.kind() == Kind.FOMC).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 9, 17), sep.date());
        assertEquals("03:00", sep.time());

    }

    @Test
    void 십이월_일정() {

        List<Event> dec = MarketCalendar.between(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
        assertTrue(dec.stream().anyMatch(e -> e.kind() == Kind.HOLIDAY && e.date().equals(LocalDate.of(2026, 12, 31))));
        assertTrue(dec.stream().anyMatch(e -> e.kind() == Kind.EXPIRY && e.big()), "12월은 동시만기");
        assertFalse(MarketCalendar.isTradingDay(LocalDate.of(2026, 12, 25)));

    }

    @Test
    void 아침_브리핑에_사흘_일정() {

        String s = com.sklabs1037.sklabs1037.brief.service.MorningBriefService.calendarLines(LocalDate.of(2026, 12, 9));
        assertTrue(s.contains("내일 04:00 · 🇺🇸 FOMC 금리 결정"), s);
        assertTrue(s.contains("선물·옵션 동시만기일"), s);
        assertEquals("", com.sklabs1037.sklabs1037.brief.service.MorningBriefService.calendarLines(LocalDate.of(2026, 12, 14)));

    }
}
