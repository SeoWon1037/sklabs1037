package com.sklabs1037.sklabs1037.calendar.service;

import com.sklabs1037.sklabs1037.calendar.service.CalendarAlertService.Message;
import com.sklabs1037.sklabs1037.calendar.service.MarketCalendar.Event;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🔔 증시 일정 알림: 켠 종류만, 한 사람에 알림 하나로 묶기
 */
class CalendarAlertServiceTest {

    private final List<Event> dec10 = MarketCalendar.between(LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 10));

    @Test
    void 내일_FOMC와_동시만기를_하나로() {

        Message m = CalendarAlertService.message(dec10, CalendarAlertService.KINDS);
        assertNotNull(m);
        assertEquals("📅 내일 🇺🇸 FOMC 금리 결정 외 1개", m.title());
        assertTrue(m.body().startsWith("새벽 04:00 🇺🇸 FOMC 금리 결정"), m.body());
        assertTrue(m.body().contains("동시만기일"));

    }

    @Test
    void 켠_종류만() {

        Message m = CalendarAlertService.message(dec10, List.of("EXPIRY"));
        assertEquals("📅 내일 선물·옵션 동시만기일 (네 마녀의 날)", m.title());
        assertNull(CalendarAlertService.message(dec10, List.of("BOK", "HOLIDAY")));

    }
}
