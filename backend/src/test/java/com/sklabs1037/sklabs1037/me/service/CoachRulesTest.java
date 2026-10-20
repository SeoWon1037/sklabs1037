package com.sklabs1037.sklabs1037.me.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🤖 AI 주간 코치: AI 답 정리 · 미션 진행률
 */
class CoachRulesTest {

    private static RangeStats stats(int attend, int predictVotes, int quizPlays) {
        return new RangeStats(0, predictVotes, 0, 0, quizPlays, 0, 0, 0, attend, 0, 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 모르는_미션_중복_범위밖은_정리하고_모자라면_기본_미션() {

        Map<String, Object> input = Map.of(
                "headline", "이번 주는 퀴즈로 시작해요",
                "missions", List.of(
                        Map.of("key", "QUIZ", "target", 99, "why", "뉴스 감각"),     // 7로 자름
                        Map.of("key", "QUIZ", "target", 2, "why", "중복"),            // 같은 종류 → 버림
                        Map.of("key", "BUY_SAMSUNG", "target", 1, "why", "x")));     // 없는 종류 → 버림

        Map<String, Object> out = CoachService.clean(input, stats(2, 0, 0));
        List<Map<String, Object>> missions = (List<Map<String, Object>>) out.get("missions");

        assertEquals(3, missions.size());
        assertEquals("QUIZ", missions.get(0).get("key"));
        assertEquals(7, missions.get(0).get("target"));
        assertEquals("ATTEND", missions.get(1).get("key"));       // 기본: 출석 (지난주 2일 → 3일)
        assertEquals(3, missions.get(1).get("target"));
        assertEquals("PREDICT", missions.get(2).get("key"));      // 퀴즈는 이미 있으니 예측
        assertEquals("이번 주는 퀴즈로 시작해요", out.get("headline"));
        assertNotNull(out.get("tip"));                             // 빈 값은 기본 문장

    }

    @Test
    void 진행률은_실제_기록으로() {

        List<Map<String, Object>> saved = List.of(
                Map.of("key", "ATTEND", "target", 3),
                Map.of("key", "PREDICT", "target", 10));

        List<Map<String, Object>> p = CoachService.progress(saved, stats(4, 6, 0));

        assertEquals(3, p.get(0).get("current"));     // 넘쳐도 목표까지만 표시
        assertEquals(true, p.get(0).get("done"));
        assertEquals(6, p.get(1).get("current"));
        assertEquals(false, p.get(1).get("done"));

    }

    @Test
    void 주_시작은_월요일() {

        assertEquals(LocalDate.parse("2026-10-19"), CoachService.weekStart(LocalDate.parse("2026-10-20")));
        assertEquals(LocalDate.parse("2026-10-19"), CoachService.weekStart(LocalDate.parse("2026-10-19")));
        assertEquals(LocalDate.parse("2026-10-19"), CoachService.weekStart(LocalDate.parse("2026-10-25")));

    }
}
