package com.sklabs1037.sklabs1037.idea.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 💡 비슷한 제안 찾기 · 카테고리 추측
 */
class IdeaRulesTest {

    private static final List<Map<String, Object>> CANDIDATES = List.of(
            Map.of("id", 1L, "title", "환율 알림 기능", "body", "달러 환율이 정해둔 값이 되면 알림", "status", "OPEN", "voteCount", 5),
            Map.of("id", 2L, "title", "다크 모드 글자 크게", "body", "글자 크기 조절", "status", "PLANNED", "voteCount", 2),
            Map.of("id", 3L, "title", "친구 초대 보상", "body", "", "status", "OPEN", "voteCount", 1));

    @Test
    void 낱말이_겹치면_비슷한_제안() {

        List<Map<String, Object>> found = IdeaService.bySimilarity("달러 환율 알림을 받고 싶어요", CANDIDATES);

        assertEquals(1, found.size());
        assertEquals(1L, ((Number) found.get(0).get("id")).longValue());

    }

    @Test
    void AI가_고른_번호는_있는_것만_최대3개() {

        List<Map<String, Object>> picked = IdeaService.pick(CANDIDATES, List.of(3L, 99L, 3L, 1L));

        assertEquals(2, picked.size());
        assertEquals(3L, ((Number) picked.get(0).get("id")).longValue());
        assertEquals(1L, ((Number) picked.get(1).get("id")).longValue());

    }

    @Test
    void 카테고리_추측() {

        assertEquals("뷰티", IdeaService.guessCategory("성분 비교를 더 자세히"));
        assertEquals("투자", IdeaService.guessCategory("배당 일정 보여주세요"));
        assertEquals("기타", IdeaService.guessCategory("안녕하세요"));

    }
}
