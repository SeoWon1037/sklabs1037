package com.sklabs1037.sklabs1037.brain.service;

import com.sklabs1037.sklabs1037.brain.dao.BrainDao;
import com.sklabs1037.sklabs1037.support.Fakes;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** 받기 → 다시 받기 → 고친 노트 · 지운 노트 · 바뀐 파일 (DB 대신 메모리) */
class BrainImportServiceTest {

    // 노트: id → { key, body, hash }
    private final Map<Long, Map<String, Object>> notes = new LinkedHashMap<>();
    private final Set<String> seen = new HashSet<>();
    private final List<long[]> links = new ArrayList<>();
    private long nextId = 1;

    private BrainImportService service(List<BrainImportRules.Note> files) {

        Map<String, Function<Object[], Object>> b = new HashMap<>();
        b.put("countSeen", a -> seen.contains((String) a[1]) ? 1 : 0);
        b.put("insertSeen", a -> seen.add((String) a[1]) ? 1 : 0);
        b.put("countNotes", a -> notes.size());
        b.put("selectSourceNote", a -> notes.entrySet().stream().filter(e -> a[1].equals(e.getValue().get("key"))).findFirst().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.getKey());
            m.put("storedHash", e.getValue().get("hash"));
            m.put("bodyHash", BrainImportRules.hash((String) e.getValue().get("body")));
            return (Object) m;
        }).orElse(null));
        b.put("insertSourceNote", a -> {
            @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) a[0];
            long id = nextId++;
            row.put("id", id);
            notes.put(id, new HashMap<>(Map.of("key", row.get("key"), "body", row.get("body"), "hash", row.get("hash"))));
            return 1;
        });
        b.put("updateSourceNote", a -> {
            Map<String, Object> n = notes.get((Long) a[1]);
            if (!BrainImportRules.hash((String) n.get("body")).equals(n.get("hash"))) return 0;
            n.put("body", a[3]);
            n.put("hash", a[5]);
            return 1;
        });
        b.put("insertLink", a -> { links.add(new long[]{(Long) a[1], (Long) a[2]}); return 1; });

        BrainImportService s = new BrainImportService();
        Fakes.set(s, "brainDao", Fakes.fake(BrainDao.class, b, null));
        Fakes.set(s, "cache", files);
        return s;

    }

    private static BrainImportRules.Note note(String key, String body, String... links) {
        return new BrainImportRules.Note(key, "RAW", key, List.of("spring"), List.of(links), body);
    }

    private String body(String key) {
        return notes.values().stream().filter(n -> key.equals(n.get("key"))).map(n -> (String) n.get("body")).findFirst().orElse(null);
    }

    @Test
    void 처음_받기와_다시_받기() {

        List<BrainImportRules.Note> v1 = List.of(note("code-a", "A1"), note("code-b", "B1", "code-a"));
        Map<String, Object> r = service(v1).sync(7L);
        assertEquals(2, r.get("added"));
        assertEquals(1, links.size());

        r = service(v1).sync(7L);                                   // 같은 파일 → 아무것도 안 바뀜
        assertEquals(0, r.get("added"));
        assertEquals(0, r.get("updated"));

    }

    @Test
    void 안_고친_노트만_새_내용으로() {

        service(List.of(note("code-a", "A1"), note("code-b", "B1"))).sync(7L);
        notes.values().stream().filter(n -> "code-b".equals(n.get("key"))).findFirst().orElseThrow().put("body", "B1 + 내 메모");

        Map<String, Object> r = service(List.of(note("code-a", "A2"), note("code-b", "B2"))).sync(7L);
        assertEquals(1, r.get("updated"));
        assertEquals("A2", body("code-a"));
        assertEquals("B1 + 내 메모", body("code-b"));             // 내가 고친 것은 그대로

    }

    @Test
    void 지운_노트는_다시_넣지_않고_새_파일은_넣는다() {

        service(List.of(note("code-a", "A1"))).sync(7L);
        notes.clear();                                              // 내가 지움

        Map<String, Object> r = service(List.of(note("code-a", "A2"), note("update-112", "U"))).sync(7L);
        assertEquals(1, r.get("added"));
        assertNull(body("code-a"));
        assertEquals("U", body("update-112"));

    }

}
