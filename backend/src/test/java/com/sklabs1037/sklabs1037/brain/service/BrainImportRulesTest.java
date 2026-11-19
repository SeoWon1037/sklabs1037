package com.sklabs1037.sklabs1037.brain.service;

import com.sklabs1037.sklabs1037.brain.service.BrainImportRules.Action;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BrainImportRulesTest {

    private static final String FILE = """
            key: code-plan
            title: Spring 코드 · plan 패키지
            topics: spring, Plan
            links: code-ai, code-plan, BAD KEY, code-ai
            ---
            ## 요약
            <select> … WHERE USER_ID = #{userId} #태그아님
            """;

    @Test
    void 머리와_본문을_읽는다() {

        BrainImportRules.Note n = BrainImportRules.parse("a.md", FILE);
        assertEquals("code-plan", n.key());
        assertEquals("RAW", n.kind());
        assertEquals("Spring 코드 · plan 패키지", n.title());
        assertEquals(List.of("spring", "plan"), n.topics());           // 본문의 #… 는 태그로 안 봄
        assertEquals(List.of("code-ai"), n.links());                   // 자기 자신 · 잘못된 key · 겹침 제외
        assertTrue(n.body().startsWith("## 요약"));

    }

    @Test
    void 잘못된_파일은_거절() {

        assertThrows(IllegalArgumentException.class, () -> BrainImportRules.parse("a.md", "key: x\n본문만"));
        assertThrows(IllegalArgumentException.class, () -> BrainImportRules.parse("a.md", "key: Bad Key\n---\n본문"));
        assertThrows(IllegalArgumentException.class, () -> BrainImportRules.parse("a.md", "title: 키 없음\n---\n본문"));
        assertThrows(IllegalArgumentException.class, () -> BrainImportRules.parse("a.md", "key: a\nkind: NOTE\n---\n본문"));
        assertThrows(IllegalArgumentException.class, () -> BrainImportRules.parse("a.md", "key: a\n---\n   "));

    }

    @Test
    void 윈도우_줄바꿈과_BOM도_읽는다() {

        BrainImportRules.Note n = BrainImportRules.parse("a.md", "﻿key: a-1\r\n---\r\n첫 줄\r\n둘째 줄");
        assertEquals("a-1", n.key());
        assertEquals("첫 줄\n둘째 줄", n.body());
        assertEquals("첫 줄", n.title());

    }

    @Test
    void 지문은_PostgreSQL_MD5와_같다() {

        // SELECT md5('가 #{userId}' || chr(10) || '끝')
        assertEquals("44b9abaac470e0444ad13b236e3760ef", BrainImportRules.hash("가 #{userId}\n끝"));

    }

    @Test
    void 할_일_정하기() {

        assertEquals(Action.NEW, BrainImportRules.decide(false, null, null, "n"));
        assertEquals(Action.UPDATE, BrainImportRules.decide(true, "o", "o", "n"));   // 안 고침 + 새 내용
        assertEquals(Action.KEEP, BrainImportRules.decide(true, "n", "n", "n"));     // 같은 내용
        assertEquals(Action.KEEP, BrainImportRules.decide(true, "o", "x", "n"));     // 내가 고침
        assertEquals(Action.KEEP, BrainImportRules.decide(true, null, null, "n"));   // 내가 지움

    }

    @Test
    void 같이_배포되는_노트_파일은_모두_읽힌다() throws Exception {

        Path dir = Path.of("src/main/resources/brain/notes");
        if (!Files.isDirectory(dir)) dir = Path.of("backend/src/main/resources/brain/notes");
        Set<String> keys = new HashSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".md")).toList()) {
                BrainImportRules.Note n = BrainImportRules.parse(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8));
                assertTrue(keys.add(n.key()), "key 겹침: " + n.key());
                assertTrue(n.topics().contains("spring"), p + ": spring 주제가 없어요");
            }
        }
        assertTrue(keys.contains("code-overview"));

    }

}
