package com.sklabs1037.sklabs1037.me.service;

import com.sklabs1037.sklabs1037.me.service.BadgeService.Stats;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🏅 배지 조건 · 연속 일수 계산
 */
class BadgeRulesTest {

    private static Stats empty() {
        return new Stats(0, null, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0);
    }

    @Test
    void 아무것도_안_했으면_배지_없음() {

        assertTrue(BadgeService.earned(empty()).isEmpty());

    }

    @Test
    void 조건을_넘으면_배지() {

        // 거래 3번 + 수익률 6% + 예측 25문제 중 16개(64%) 적중·최고 5연속 + 퀴즈 만점 1번 + 출석 1일
        Stats s = new Stats(3, 6.0, false, 30, 25, 16, 5, 2, 1, 2, 0, 0, 0, 0, 0, 0, 0, true, 1, 1);
        List<String> codes = BadgeService.earned(s);

        assertTrue(codes.containsAll(List.of("TRADE_FIRST", "PROFIT_5", "PREDICT_FIRST", "PREDICT_STREAK5", "PREDICT_SHARP",
                "QUIZ_FIRST", "QUIZ_PERFECT", "PERSONA_DONE", "ATTEND_FIRST")));
        assertFalse(codes.contains("PROFIT_20"));
        assertFalse(codes.contains("BEAT_AI"));

        // 적중률 60% 미만이면 날카로운 눈 없음
        Stats low = new Stats(0, null, false, 25, 25, 14, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0);
        assertFalse(BadgeService.earned(low).contains("PREDICT_SHARP"));

    }

    @Test
    void 연속_계산() {

        assertEquals(3, ActivityService.bestRun(Arrays.asList(true, false, true, true, true, false)));

        LocalDate d = LocalDate.parse("2026-10-20");
        List<LocalDate> days = List.of(d, d.minusDays(1), d.minusDays(2), d.minusDays(5), d.minusDays(6));

        assertEquals(3, ActivityService.longestDays(days));
        assertEquals(3, ActivityService.currentStreak(days, d));
        assertEquals(3, ActivityService.currentStreak(days, d.plusDays(1)));   // 오늘 아직 안 왔어도 어제까지 이어짐
        assertEquals(0, ActivityService.currentStreak(days, d.plusDays(2)));

    }
}
