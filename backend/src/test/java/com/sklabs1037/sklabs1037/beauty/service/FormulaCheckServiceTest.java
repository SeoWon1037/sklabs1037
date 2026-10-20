package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.service.FormulaCheckService.Item;
import com.sklabs1037.sklabs1037.support.Fakes;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 처방 검토 규칙
 * - 적량(정제수) 채우기, 합계 오류
 * - 원료 한도 / 파라벤 "산으로서" 환산과 합산 한도 / 씻어내는 제품 전용 원료 / 어린이용 금지
 * - 기능성 고시 함량, 알레르기 착향제 표시, 전성분 순서
 */
class FormulaCheckServiceTest {

    private FormulaCheckService service() {

        FormulaCheckService s = new FormulaCheckService();
        Fakes.set(s, "dictionary", BeautyTestData.dictionary(BeautyTestData.dao(0, new ArrayList<>())));

        return s;

    }

    private static Item pct(String name, double pct) {
        return new Item(name, pct, false);
    }

    private static Item qs(String name) {
        return new Item(name, null, true);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> issues(Map<String, Object> r) {
        return (List<Map<String, Object>>) r.get("issues");
    }

    private static boolean hasIssue(Map<String, Object> r, String level, String target) {
        return issues(r).stream().anyMatch(m -> level.equals(m.get("level")) && String.valueOf(m.get("target")).contains(target));
    }

    @Test
    void 정제수_적량을_채우고_문제없으면_통과() {

        Map<String, Object> r = service().check(List.of(
                qs("정제수"), pct("글리세린", 5), pct("나이아신아마이드", 4), pct("부틸렌글라이콜", 3), pct("아데노신", 0.04)
        ), false, List.of("미백", "주름"), false);

        assertEquals((Object) 87.96, r.get("qsPct"));
        assertEquals((Object) 100.0, r.get("total"));
        assertEquals("PASS", r.get("verdict"));
        assertEquals("정제수, 글리세린, 나이아신아마이드, 부틸렌글라이콜, 아데노신", r.get("label"));

    }

    @Test
    void 합계가_100이_아니면_오류() {

        Map<String, Object> r = service().check(List.of(pct("정제수", 80), pct("글리세린", 10)), false, List.of(), false);
        assertEquals("FAIL", r.get("verdict"));
        assertTrue(hasIssue(r, "ERROR", "합계"));

        Map<String, Object> over = service().check(List.of(qs("정제수"), pct("글리세린", 60), pct("부틸렌글라이콜", 50)), false, List.of(), false);
        assertTrue(hasIssue(over, "ERROR", "합계"));

        assertThrows(IllegalArgumentException.class,
                () -> service().check(List.of(qs("정제수"), qs("글리세린")), false, List.of(), false));

    }

    @Test
    void 파라벤은_산으로_환산하고_합산_한도도_본다() {

        // 메틸파라벤 0.4% → 산으로 0.363% (단일 한도 0.4% 안)
        Map<String, Object> single = service().check(List.of(qs("정제수"), pct("메칠파라벤", 0.4)), false, List.of(), false);
        assertFalse(hasIssue(single, "ERROR", "메틸파라벤"));

        // 메틸 0.45 → 산 0.4085 > 0.4 초과
        Map<String, Object> over = service().check(List.of(qs("정제수"), pct("메틸파라벤", 0.45)), false, List.of(), false);
        assertTrue(hasIssue(over, "ERROR", "메틸파라벤"));

        // 메틸 0.4(0.363) + 프로필 0.4(0.307) + 부틸 0.3(0.213) = 0.883 > 0.8 합산 초과, 각각은 단일 한도 안
        Map<String, Object> mixed = service().check(List.of(qs("정제수"), pct("메틸파라벤", 0.4), pct("프로필파라벤", 0.4), pct("부틸파라벤", 0.3)),
                false, List.of(), false);
        assertTrue(hasIssue(mixed, "ERROR", "파라벤 합계"));
        assertFalse(hasIssue(mixed, "ERROR", "프로필파라벤"));

    }

    @Test
    void 살리실릭애시드는_어린이용_금지이고_여드름은_씻어내는_제품만() {

        Map<String, Object> kids = service().check(List.of(qs("정제수"), pct("살리실릭애씨드", 0.5)), false, List.of("여드름"), true);
        assertTrue(hasIssue(kids, "ERROR", "살리실릭애시드"));
        assertTrue(hasIssue(kids, "WARN", "여드름"));            // 씻어내지 않는 제품이라 여드름 기능성 아님

        Map<String, Object> wash = service().check(List.of(qs("정제수"), pct("살리실릭애시드", 0.5)), true, List.of("여드름"), false);
        assertFalse(hasIssue(wash, "ERROR", "살리실릭애시드"));
        assertFalse(hasIssue(wash, "WARN", "여드름"));

    }

    @Test
    void 기능성_고시_함량을_벗어나면_경고() {

        // 나이아신아마이드 1% (고시 2~5%), 알부틴 6% (고시 2~5%)
        Map<String, Object> r = service().check(List.of(qs("정제수"), pct("나이아신아마이드", 1), pct("알부틴", 6)),
                false, List.of("미백", "주름"), false);

        assertTrue(hasIssue(r, "WARN", "미백"));
        assertTrue(hasIssue(r, "WARN", "주름"));                 // 주름 고시 성분 없음
        assertEquals("CHECK", r.get("verdict"));

    }

    @Test
    void 알레르기_착향제는_제품_유형별_기준을_넘으면_표시() {

        // 리모넨 0.005%: 씻어내지 않는 제품(0.001%)은 표시, 씻어내는 제품(0.01%)은 표시 안 함
        Map<String, Object> leave = service().check(List.of(qs("정제수"), pct("리모넨", 0.005)), false, List.of(), false);
        Map<String, Object> rinse = service().check(List.of(qs("정제수"), pct("리모넨", 0.005)), true, List.of(), false);

        assertTrue(hasIssue(leave, "INFO", "알레르기"));
        assertFalse(hasIssue(rinse, "INFO", "알레르기"));

    }

    @Test
    void 전성분은_1퍼센트_초과만_함량순이고_사전에_없는_원료는_안내() {

        Map<String, Object> r = service().check(List.of(
                pct("글리세린", 3), qs("정제수"), pct("판테놀", 0.5), pct("부틸렌글라이콜", 8), pct("신규원료X", 0.2)
        ), false, List.of(), false);

        assertEquals("정제수, 부틸렌글라이콜, 글리세린, 판테놀, 신규원료X", r.get("label"));
        assertTrue(hasIssue(r, "INFO", "사전에 없는 원료"));

    }

    @Test
    void 규칙_숫자_확인() {

        assertEquals((Object) 1.0, FormulaRules.limitOf("페녹시에탄올").leaveOn());
        assertEquals((Object) 25.0, FormulaRules.limitOf("징크옥사이드").leaveOn());
        assertEquals((Object) 7.5, FormulaRules.limitOf("에칠헥실메톡시신나메이트").leaveOn());
        assertNull(FormulaRules.limitOf("징크피리치온").leaveOn());
        assertEquals((Object) 2.5, FormulaRules.limitOf("소듐벤조에이트").rinseOff());
        assertEquals((Object) 0.8, FormulaRules.GROUP_LIMITS.get("PARABEN").limit());

    }
}
