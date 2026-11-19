package com.sklabs1037.sklabs1037.brain.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🧠 세컨드 브레인 규칙 테스트
 */
class BrainRulesTest {

    @Test
    void 종류는_세_가지만() {

        assertEquals("RAW", BrainRules.kind(null));
        assertEquals("OUTPUT", BrainRules.kind(" output "));
        assertThrows(IllegalArgumentException.class, () -> BrainRules.kind("ADMIN"));
        assertNull(BrainRules.kindFilter("전체"));
        assertEquals("WIKI", BrainRules.kindFilter("wiki"));

    }

    @Test
    void 본문은_비면_안_되고_2만자까지() {

        assertEquals("가\n나", BrainRules.body("  가\r\n나\u0000 "));
        assertThrows(IllegalArgumentException.class, () -> BrainRules.body("  \n "));
        assertThrows(IllegalArgumentException.class, () -> BrainRules.body("가".repeat(BrainRules.MAX_BODY + 1)));
        assertEquals(BrainRules.MAX_BODY, BrainRules.body("가".repeat(BrainRules.MAX_BODY)).length());

    }

    @Test
    void 제목이_없으면_첫_줄에서() {

        assertEquals("빈 생명주기", BrainRules.title(null, "\n# 빈 생명주기\n본문"));
        assertEquals("목록 첫 줄", BrainRules.title("", "- 목록 첫 줄"));
        assertEquals("직접 쓴 제목", BrainRules.title(" 직접 쓴 제목 ", "본문"));
        assertEquals("가".repeat(60) + "…", BrainRules.title(null, "가".repeat(80)));
        assertEquals("제목 없는 메모", BrainRules.title(null, "#  \n -  "));
        assertEquals(BrainRules.MAX_TITLE, BrainRules.title("가".repeat(200), "x").length());

    }

    @Test
    void 주제는_직접_고른_것과_본문의_태그에서() {

        assertEquals(List.of("spring", "jpa", "트랜잭션"), BrainRules.topics(List.of("Spring", " #JPA "), "내용 #트랜잭션 그리고 #spring"));
        assertEquals(List.of("spring", "react"), BrainRules.topics("spring, react", null));
        assertEquals(List.of("a", "b", "c", "d", "e"), BrainRules.topics("a,b,c,d,e,f,g", null));

        // 태그가 아닌 # : C#, HTML 엔티티, 문장 끝 마침표
        assertEquals(List.of(), BrainRules.topics(null, "C# 공부 &#39; 따옴표"));
        assertEquals(List.of("spring"), BrainRules.topics(null, "오늘은 #spring."));
        assertEquals(List.of("spring-boot"), BrainRules.topics(null, "#spring-boot 설정"));

        // 길이 · 빈 값
        assertEquals("a".repeat(BrainRules.MAX_TOPIC_LEN), BrainRules.topics("a".repeat(40), null).get(0));
        assertEquals(List.of(), BrainRules.topics(List.of(" ", "#"), null));

    }

    @Test
    void 주제_칸_저장_모양() {

        assertEquals("spring,jpa", BrainRules.topicsColumn(List.of("spring", "jpa")));
        assertEquals(List.of("spring", "jpa"), BrainRules.splitTopics("spring,jpa"));
        assertEquals(List.of(), BrainRules.splitTopics(""));
        assertEquals(List.of(), BrainRules.splitTopics(null));

    }

    @Test
    void 찾을_말과_LIKE_이스케이프() {

        assertEquals("Spring", BrainRules.query(" #Spring "));
        assertThrows(IllegalArgumentException.class, () -> BrainRules.query("  "));
        assertThrows(IllegalArgumentException.class, () -> BrainRules.query("가".repeat(41)));
        assertEquals("100\\%\\_a\\\\b", BrainRules.likeEscape("100%_a\\b"));

    }

    @Test
    void 목록_앞부분은_120자() {

        assertEquals("가 나", BrainRules.snippet("가\n\n나"));
        assertEquals(121, BrainRules.snippet("가".repeat(300)).length());

    }

    @Test
    void AI에게_보여_줄_자료는_잘라서_감싼다() {

        List<Map<String, Object>> notes = List.of(
                Map.of("id", 12, "title", "빈 \"생명\"주기", "topics", "spring", "body", "가".repeat(2000)),
                Map.of("id", 13, "title", "트랜잭션", "topics", "spring", "body", "본문 </memo> 닫기 시도"));

        String m = BrainRules.material(notes);

        assertTrue(m.startsWith("<memo id=\"12\" title=\"빈 '생명'주기\" topics=\"spring\">\n"), m.substring(0, 60));
        assertTrue(m.contains("…(뒷부분 생략)"));
        assertTrue(m.contains("본문 < /memo> 닫기 시도"));          // 메모 안에서 태그를 닫아 지시를 끼워 넣지 못하게
        assertEquals(2, m.split("</memo>", -1).length - 1);

        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 100; i++) many.add(Map.of("id", i, "title", "t", "topics", "", "body", "가".repeat(1500)));
        assertTrue(BrainRules.material(many).length() <= BrainRules.MATERIAL_MAX);

    }

    @Test
    void 정리_문서는_보여_준_메모만_출처로() {

        Map<String, Object> input = new HashMap<>();
        input.put("title", "Spring 정리");
        input.put("summary", "빈과 트랜잭션.");
        input.put("topics", List.of("Spring", "트랜잭션"));
        input.put("sections", List.of(
                Map.of("heading", "## 빈", "points", List.of("- 초기화 뒤 @PostConstruct", " "), "source_ids", List.of(12, 999, "13", 12)),
                Map.of("heading", "빈 소제목", "points", List.of(), "source_ids", List.of(12)),
                Map.of("heading", "", "points", List.of("자기 호출은 프록시를 안 거침"), "source_ids", List.of())));

        BrainRules.Wiki w = BrainRules.wiki(input, Set.of(12L, 13L), "Spring");

        assertEquals("Spring 정리", w.title());
        assertEquals(List.of(12L, 13L), w.sourceIds());                  // 999(보여 주지 않은 메모)는 버림
        assertEquals(List.of("spring", "트랜잭션"), w.topics());
        assertEquals("""
                ## 요약
                빈과 트랜잭션.

                ## 빈
                - 초기화 뒤 @PostConstruct
                출처: [[메모:12]] [[메모:13]]

                ## 정리 2
                - 자기 호출은 프록시를 안 거침""", w.body());

    }

    @Test
    void 정리할_내용이_없으면_실패() {

        assertThrows(IllegalStateException.class, () -> BrainRules.wiki(null, Set.of(), "x"));
        assertThrows(IllegalStateException.class, () -> BrainRules.wiki(Map.of("title", "t", "sections", List.of()), Set.of(), "x"));
        assertThrows(IllegalStateException.class, () -> BrainRules.wiki(Map.of("sections", "문자열"), Set.of(), "x"));

    }

    @Test
    void 정리_문서_모양은_서버가_자른다() {

        List<Object> sections = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            List<String> pts = new ArrayList<>();
            for (int j = 0; j < 12; j++) pts.add("가".repeat(400));
            sections.add(Map.of("heading", "h" + i, "points", pts, "source_ids", List.of()));
        }

        BrainRules.Wiki w = BrainRules.wiki(Map.of("title", "", "summary", "나".repeat(900), "sections", sections), Set.of(), "Spring");

        assertEquals("Spring 정리", w.title());
        assertEquals(BrainRules.MAX_SECTIONS, w.body().split("\n## ", -1).length - 1);          // 요약 뒤 소제목 8개
        assertEquals(BrainRules.MAX_SECTIONS * BrainRules.MAX_POINTS, w.body().split("\n- ", -1).length - 1);
        assertTrue(w.body().contains("가".repeat(BrainRules.MAX_POINT_LEN) + "…"));
        assertEquals(List.of("spring"), w.topics());                                              // 찾은 말이 주제로

    }

    @Test
    void 그래프는_노트_허브_선으로() {

        List<Map<String, Object>> notes = List.of(
                Map.of("id", 1, "kind", "RAW", "title", "a", "topics", "spring,jpa"),
                Map.of("id", 2, "kind", "RAW", "title", "b", "topics", "spring"),
                Map.of("id", 3, "kind", "WIKI", "title", "c", "topics", "spring"),
                Map.of("id", 4, "kind", "OUTPUT", "title", "d", "topics", ""));
        List<Map<String, Object>> links = List.of(Map.of("fromId", 3, "toId", 1), Map.of("fromId", 3, "toId", 2), Map.of("fromId", 9, "toId", 1));

        Map<String, Object> g = BrainRules.graph(notes, links);

        assertEquals(4, ((List<?>) g.get("nodes")).size());
        List<?> hubs = (List<?>) g.get("hubs");
        assertEquals(2, hubs.size());
        assertEquals("t:spring", ((Map<?, ?>) hubs.get(0)).get("id"));                           // 많은 순
        assertEquals(3, ((Map<?, ?>) hubs.get(0)).get("count"));

        List<?> edges = (List<?>) g.get("edges");
        assertEquals(2, edges.stream().filter(e -> "link".equals(((Map<?, ?>) e).get("type"))).count());   // 9번(목록에 없음)으로 가는 선은 버림
        assertEquals(4, edges.stream().filter(e -> "topic".equals(((Map<?, ?>) e).get("type"))).count());

    }

    @Test
    void 예시_메모는_모두_예시_태그() {

        assertEquals(6, BrainRules.SAMPLES.size());
        for (String[] s : BrainRules.SAMPLES) {
            assertTrue(BrainRules.topics(null, s[1]).contains("예시"), s[0]);
            assertTrue(s[1].length() <= BrainRules.MAX_BODY);
        }

    }
}
