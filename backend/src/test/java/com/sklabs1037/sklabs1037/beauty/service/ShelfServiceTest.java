package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.service.ShelfService.Product;
import com.sklabs1037.sklabs1037.support.Fakes;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🧴 내 화장대 · 성분 궁합 규칙
 * - 레티노이드 + BHA 같은 루틴 → 주의, 아침 레티노이드인데 선크림 없음 → 주의
 * - 아침·저녁 나눠 쓰면 궁합 문제 없음, 루틴 순서는 클렌저 → … → 선크림
 */
class ShelfServiceTest {

    private ShelfService service() {

        ShelfService s = new ShelfService();
        Fakes.set(s, "dictionary", BeautyTestData.dictionary(BeautyTestData.dao(0, new ArrayList<>())));

        return s;

    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> findings(Map<String, Object> r) {
        return (List<Map<String, Object>>) r.get("findings");
    }

    private static boolean has(Map<String, Object> r, String level, String text) {
        return findings(r).stream().anyMatch(f -> level.equals(f.get("level")) && String.valueOf(f.get("title")).contains(text));
    }

    @Test
    void 레티놀과_BHA를_같은_저녁에_쓰면_주의() {

        Map<String, Object> r = service().check(List.of(
                new Product("BHA 토너", "정제수, 살리실릭애씨드, 부틸렌글라이콜", "TONER", "PM"),
                new Product("레티놀 크림", "정제수, 글리세린, 레티놀", "CREAM", "PM")
        ), null);

        assertTrue(has(r, "HIGH", "레티노이드 + 각질 제거"));

    }

    @Test
    void 아침에_레티놀인데_선크림이_없으면_주의() {

        Map<String, Object> r = service().check(List.of(
                new Product("레티놀 세럼", "정제수, 레티놀", "SERUM", "BOTH")
        ), null);

        assertTrue(has(r, "HIGH", "선크림이 없어요"));
        assertTrue(has(r, "MEDIUM", "레티노이드는 저녁에"));

    }

    @Test
    void 아침_저녁으로_나누면_괜찮고_순서는_단계대로() {

        Map<String, Object> r = service().check(List.of(
                new Product("수분 크림", "정제수, 글리세린, 판테놀", "CREAM", "BOTH"),
                new Product("BHA 토너", "정제수, 살리실릭애씨드", "TONER", "AM"),
                new Product("레티놀 크림", "정제수, 레티놀", "CREAM", "PM"),
                new Product("폼 클렌저", "정제수, 글리세린", "CLEANSER", "BOTH"),
                new Product("선크림", "정제수, 징크옥사이드", "SUNSCREEN", "AM")
        ), null);

        assertFalse(has(r, "HIGH", "레티노이드 + 각질 제거"));
        assertFalse(has(r, "HIGH", "선크림이 없어요"));

        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> routines = (Map<String, List<Map<String, Object>>>) r.get("routines");
        assertEquals(List.of("폼 클렌저", "BHA 토너", "수분 크림", "선크림"), routines.get("AM").stream().map(m -> m.get("name")).toList());
        assertEquals(List.of("폼 클렌저", "수분 크림", "레티놀 크림"), routines.get("PM").stream().map(m -> m.get("name")).toList());

    }

    @Test
    void 전성분이_없으면_안내() {

        assertThrows(IllegalArgumentException.class, () -> service().check(List.of(new Product("빈 제품", " ", "CREAM", "AM")), null));
        assertThrows(IllegalArgumentException.class, () -> service().check(List.of(), null));

    }
}
