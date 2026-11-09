package com.sklabs1037.sklabs1037.qa.service;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ⏪ 배포 지킴이: 되돌리는 기준
 * - 규칙(RULE)으로 찾은 심각(HIGH) 문제 중 바로 전 점검에는 없던 것만
 * - AI 판정 · 주의(MEDIUM) · 전에도 있던 문제는 되돌리지 않음
 */
class DeployGuardServiceTest {

    private static Map<String, Object> f(String sev, String key, String title, String by) {
        return Map.of("severity", sev, "key", key, "title", title, "by", by);
    }

    @Test
    void 새로_생긴_규칙_심각_문제만() {

        List<Map<String, Object>> before = List.of(f("HIGH", "stock-390", "요청 실패 502", "RULE"));
        List<Map<String, Object>> now = List.of(
                f("HIGH", "stock-390", "요청 실패 502", "RULE"),      // 전에도 있던 것 (외부 장애)
                f("HIGH", "home-390", "페이지 오류", "RULE"),          // 새로 생김 → 되돌림
                f("HIGH", "home-1400", "글자가 겹쳐 보임", "AI"),       // AI 판정 → 알림만
                f("MEDIUM", "mock-390", "느림 4.2초", "RULE"));        // 주의 → 알림만

        assertEquals(List.of("home-390 · 페이지 오류"), DeployGuardService.newSeriousProblems(now, before));

    }

    @Test
    void 문제_없거나_전과_같으면_되돌리지_않음() {

        assertTrue(DeployGuardService.newSeriousProblems(List.of(), List.of()).isEmpty());
        List<Map<String, Object>> same = List.of(f("HIGH", "a", "기능 흐름 실패: 로그인", "RULE"));
        assertTrue(DeployGuardService.newSeriousProblems(same, same).isEmpty());
        assertEquals(1, DeployGuardService.newSeriousProblems(same, List.of()).size(), "처음 점검이면 전 기록 없음 → 새 문제");

    }
}
