package com.sklabs1037.sklabs1037.couple.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🎂 커플 생일: 남은 날 · 윤년 · 문장 · 알림
 */
class BirthdayRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    @Test
    void 올해_남은_생일까지() {

        assertEquals(23L, BirthdayRules.dDay(MonthDay.of(12, 7), TODAY));
        assertEquals(LocalDate.of(2026, 12, 7), BirthdayRules.next(MonthDay.of(12, 7), TODAY));

    }

    @Test
    void 오늘이_생일이면_0() {

        assertEquals(0L, BirthdayRules.dDay(MonthDay.of(11, 14), TODAY));
        assertEquals("오늘은 민지님 생일이에요!", BirthdayRules.text("민지", MonthDay.of(11, 14), TODAY));

    }

    @Test
    void 지난_생일은_내년으로() {

        assertEquals(LocalDate.of(2027, 11, 13), BirthdayRules.next(MonthDay.of(11, 13), TODAY));
        assertEquals(364L, BirthdayRules.dDay(MonthDay.of(11, 13), TODAY));
        assertEquals(LocalDate.of(2027, 3, 1), BirthdayRules.next(MonthDay.of(3, 1), TODAY));

    }

    @Test
    void 이월_29일생은_윤년이_아니면_28일에() {

        MonthDay leap = MonthDay.of(2, 29);

        assertEquals(LocalDate.of(2027, 2, 28), BirthdayRules.next(leap, TODAY));
        assertEquals(LocalDate.of(2028, 2, 29), BirthdayRules.next(leap, LocalDate.of(2027, 3, 1)));
        assertEquals(0L, BirthdayRules.dDay(leap, LocalDate.of(2027, 2, 28)));

    }

    @Test
    void 없는_날짜는_거절() {

        assertEquals(MonthDay.of(12, 7), BirthdayRules.monthDay("12", " 7 "));
        assertEquals(MonthDay.of(2, 29), BirthdayRules.monthDay(2, 29));
        assertThrows(IllegalArgumentException.class, () -> BirthdayRules.monthDay(2, 30));
        assertThrows(IllegalArgumentException.class, () -> BirthdayRules.monthDay(13, 1));
        assertThrows(IllegalArgumentException.class, () -> BirthdayRules.monthDay("십이", 7));
        assertThrows(IllegalArgumentException.class, () -> BirthdayRules.monthDay(12, null));

    }

    @Test
    void 화면에_줄_모양과_문장() {

        Map<String, Object> v = BirthdayRules.view("민지", MonthDay.of(12, 7), TODAY);

        assertEquals(12, v.get("month"));
        assertEquals(7, v.get("day"));
        assertEquals("2026-12-07", v.get("date"));
        assertEquals(23L, v.get("dDay"));
        assertEquals("민지님 생일은 12월 7일이고 23일 남았어요.", v.get("text"));
        assertEquals("민지님 생일은 11월 15일, 바로 내일이에요.", BirthdayRules.text("민지", MonthDay.of(11, 15), TODAY));

    }

    @Test
    void 상대에게_보내는_알림() {

        MonthDay md = MonthDay.of(12, 7);

        assertEquals("🎂 민지님 생일이 7일 남았어요", BirthdayRules.partnerMessage("민지", md, 7).title());
        assertEquals("🎂 내일은 민지님 생일이에요", BirthdayRules.partnerMessage("민지", md, 1).title());
        assertEquals("🎂 오늘은 민지님 생일이에요!", BirthdayRules.partnerMessage("민지", md, 0).title());
        assertTrue(BirthdayRules.partnerMessage("민지", md, 7).body().startsWith("12월 7일"));
        assertEquals("[7, 1, 0]", java.util.Arrays.toString(BirthdayRules.REMIND_DAYS));

    }

    @Test
    void 기념일_알림은_7일_전_하루_전_당일만() {

        assertEquals("💕 300일이 7일 남았어요", BirthdayRules.anniversaryMessage("300일", "2027-01-28", 7).title());
        assertEquals("💕 내일은 1주년이에요", BirthdayRules.anniversaryMessage("1주년", "2027-04-04", 1).title());
        assertEquals("🎉 오늘은 300일이에요!", BirthdayRules.anniversaryMessage("300일", "2027-01-28", 0).title());
        assertNull(BirthdayRules.anniversaryMessage("300일", "2027-01-28", 6));
        assertNull(BirthdayRules.anniversaryMessage("300일", "2027-01-28", 75));

    }
}
