package com.sklabs1037.sklabs1037.quiz.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📰 뉴스 퀴즈 규칙
 * - AI 문제 확인: 보기 4개·중복 없음·정답 번호·근거 기사가 목록에 있어야 통과, 보기를 섞어도 정답이 따라감
 * - 채점, 연속 참여
 */
class NewsQuizRulesTest {

    private static final List<Map<String, Object>> NEWS = List.of(
            Map.of("title", "삼성전자, HBM4 엔비디아 공급 승인", "link", "https://a", "press", "연합뉴스"),
            Map.of("title", "코스피 외국인 순매도 1조", "link", "https://b", "press", "한경"));

    private static Map<String, Object> q(String question, List<String> options, int answer, List<Integer> sources) {
        return Map.of("question", question, "options", options, "answer", answer, "explanation", "해설", "sources", sources);
    }

    @Test
    void 형식이_틀리거나_근거가_없는_문제는_버린다() {

        List<Object> raw = List.of(
                q("삼성전자가 공급 승인을 받은 제품은?", List.of("HBM4", "DDR5", "낸드", "OLED"), 0, List.of(1)),
                q("보기 3개", List.of("a", "b", "c"), 0, List.of(1)),
                q("보기 중복", List.of("a", "a", "b", "c"), 0, List.of(1)),
                q("정답 번호 오류", List.of("a", "b", "c", "d"), 4, List.of(1)),
                q("근거 없음", List.of("a", "b", "c", "d"), 1, List.of(99)));

        List<Map<String, Object>> out = NewsQuizService.clean(raw, NEWS, LocalDate.parse("2026-10-21"));

        assertEquals(1, out.size());
        @SuppressWarnings("unchecked")
        List<String> options = (List<String>) out.get(0).get("options");
        assertEquals("HBM4", options.get((int) out.get(0).get("answer")));   // 섞여도 정답은 HBM4
        assertEquals(4, new HashSet<>(options).size());

    }

    @Test
    void 채점() {

        List<Map<String, Object>> questions = List.of(
                Map.of("question", "1", "options", List.of("a", "b", "c", "d"), "answer", 2),
                Map.of("question", "2", "options", List.of("a", "b", "c", "d"), "answer", 0));

        Map<String, Object> r = NewsQuizService.grade(questions, Arrays.asList(2, 1));

        assertEquals(1, r.get("score"));
        assertEquals(2, r.get("total"));

    }

    @Test
    void 연속_참여() {

        LocalDate d = LocalDate.parse("2026-10-21");

        assertEquals(3, NewsQuizService.streak(List.of(d, d.minusDays(1), d.minusDays(2), d.minusDays(4)), d));
        assertEquals(2, NewsQuizService.streak(List.of(d.minusDays(1), d.minusDays(2)), d));   // 오늘 아직 안 풀었어도 어제까지 이어짐
        assertEquals(0, NewsQuizService.streak(List.of(d.minusDays(3)), d));
        assertEquals(0, NewsQuizService.streak(List.of(), d));

    }
}
