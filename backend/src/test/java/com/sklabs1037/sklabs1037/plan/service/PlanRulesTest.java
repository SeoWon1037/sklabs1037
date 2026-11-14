package com.sklabs1037.sklabs1037.plan.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🗓️ 내 약속 규칙 테스트 (2026-11-14는 토요일)
 */
class PlanRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 16, 40);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Test
    void 날짜와_시간을_약속_시각으로() {

        assertEquals(LocalDateTime.of(2026, 11, 21, 19, 0), PlanRules.when("2026-11-21", "19:00", NOW));
        assertEquals(LocalDateTime.of(2026, 11, 21, 9, 5), PlanRules.when("2026-11-21", "9:05", NOW));
        assertEquals(LocalDateTime.of(2026, 11, 21, 19, 0), PlanRules.when(" 2026-11-21 ", "19:00:30", NOW));

    }

    @Test
    void 시간이_없으면_시간_미정() {

        assertEquals(LocalDateTime.of(2026, 11, 21, 0, 0), PlanRules.when("2026-11-21", null, NOW));
        assertEquals(LocalDateTime.of(2026, 11, 14, 0, 0), PlanRules.when("2026-11-14", "", NOW));   // 오늘 · 시간 미정은 가능

    }

    @Test
    void 지난_시각과_너무_먼_날짜는_거절() {

        assertThrows(IllegalArgumentException.class, () -> PlanRules.when("2026-11-14", "16:00", NOW));
        assertThrows(IllegalArgumentException.class, () -> PlanRules.when("2026-11-13", null, NOW));
        assertThrows(IllegalArgumentException.class, () -> PlanRules.when("2027-11-15", "10:00", NOW));
        assertEquals(LocalDateTime.of(2026, 11, 14, 17, 0), PlanRules.when("2026-11-14", "17:00", NOW));

    }

    @Test
    void 알_수_없는_날짜와_시간은_거절() {

        assertThrows(IllegalArgumentException.class, () -> PlanRules.when("토요일", "19:00", NOW));
        assertThrows(IllegalArgumentException.class, () -> PlanRules.when("2026-11-21", "저녁", NOW));
        assertThrows(IllegalArgumentException.class, () -> PlanRules.when(null, null, NOW));

    }

    @Test
    void 이름과_장소_정리() {

        assertEquals("민지랑 저녁", PlanRules.title("  민지랑   저녁 "));
        assertEquals("a b", PlanRules.title("<a>\nb"));
        assertThrows(IllegalArgumentException.class, () -> PlanRules.title(" "));
        assertThrows(IllegalArgumentException.class, () -> PlanRules.title("가".repeat(61)));
        assertNull(PlanRules.place(" "));
        assertEquals("강남", PlanRules.place(" 강남 "));

    }

    @Test
    void 시간을_말로() {

        assertEquals("저녁 7시", PlanRules.timeText(LocalTime.of(19, 0)));
        assertEquals("오후 3시 30분", PlanRules.timeText(LocalTime.of(15, 30)));
        assertEquals("오전 9시", PlanRules.timeText(LocalTime.of(9, 0)));
        assertEquals("낮 12시", PlanRules.timeText(LocalTime.of(12, 0)));
        assertEquals("새벽 5시", PlanRules.timeText(LocalTime.of(5, 0)));
        assertEquals("밤 10시", PlanRules.timeText(LocalTime.of(22, 0)));
        assertEquals("새벽 12시 30분", PlanRules.timeText(LocalTime.of(0, 30)));
        assertEquals("", PlanRules.timeText(LocalTime.MIDNIGHT));

    }

    @Test
    void 날짜를_말로() {

        assertEquals("오늘", PlanRules.dayText(TODAY, TODAY));
        assertEquals("내일", PlanRules.dayText(TODAY.plusDays(1), TODAY));
        assertEquals("모레", PlanRules.dayText(TODAY.plusDays(2), TODAY));
        assertEquals("11월 21일 토요일", PlanRules.dayText(LocalDate.of(2026, 11, 21), TODAY));
        assertEquals("토요일", PlanRules.weekday(TODAY.getDayOfWeek()));

    }

    @Test
    void 약속_한_줄() {

        assertEquals("저녁 7시 강남에서 민지랑 저녁", PlanRules.line(LocalDateTime.of(2026, 11, 21, 19, 0), "민지랑 저녁", "강남"));
        assertEquals("치과", PlanRules.line(LocalDateTime.of(2026, 11, 21, 0, 0), "치과", null));

    }

    @Test
    void 알림은_그날_약속을_하나로_묶는다() {

        List<Map<String, Object>> plans = List.of(
                Map.of("planAt", LocalDateTime.of(2026, 11, 15, 19, 0), "title", "민지랑 저녁", "place", "강남"),
                Map.of("planAt", LocalDateTime.of(2026, 11, 15, 22, 0), "title", "영화"));

        PlanRules.Message m = PlanRules.reminder(plans, "내일");

        assertEquals("🗓️ 내일 약속: 민지랑 저녁 외 1개", m.title());
        assertEquals("내일 약속이 2개 있어요. 저녁 7시 강남에서 민지랑 저녁. 밤 10시 영화.", m.body());
        assertNull(PlanRules.reminder(List.of(), "내일"));

    }

    @Test
    void 약속이_하나면_개수를_말하지_않는다() {

        List<Map<String, Object>> one = List.of(Map.of("planAt", LocalDateTime.of(2026, 11, 14, 19, 0), "title", "회식"));

        assertEquals("오늘 약속이 있어요. 저녁 7시 회식.", PlanRules.speech(one, "오늘"));
        assertEquals("🗓️ 오늘 약속: 회식", PlanRules.reminder(one, "오늘").title());
        assertEquals("오늘 약속은 없어요.", PlanRules.speech(List.of(), "오늘"));

    }

    @Test
    void 매주_반복은_한_주씩_뒤로() {

        List<LocalDateTime> w = PlanRules.weekly(LocalDateTime.of(2026, 11, 17, 19, 0), PlanRules.REPEAT_WEEKS);

        assertEquals(12, w.size());
        assertEquals(LocalDateTime.of(2026, 11, 17, 19, 0), w.get(0));
        assertEquals(LocalDateTime.of(2026, 11, 24, 19, 0), w.get(1));
        assertEquals(LocalDateTime.of(2027, 2, 2, 19, 0), w.get(11));
        assertEquals(26, PlanRules.weekly(NOW, 100).size());   // 아무리 많아도 26주
        assertEquals(1, PlanRules.weekly(NOW, 0).size());

    }

    @Test
    void 예_아니오_읽기() {

        assertTrue(PlanRules.yes(true));
        assertTrue(PlanRules.yes("true"));
        assertTrue(PlanRules.yes("Y"));
        assertFalse(PlanRules.yes(null));
        assertFalse(PlanRules.yes("false"));
        assertFalse(PlanRules.yes(0));

    }

    @Test
    void 화면에_줄_모양에_함께와_내_것_표시() {

        Map<String, Object> row = new java.util.HashMap<>(Map.of("id", 7, "ownerId", 2L, "title", "데이트", "place", "강남",
                "planAt", LocalDateTime.of(2026, 11, 15, 19, 0), "shared", "Y"));

        Map<String, Object> mine = PlanService.view(row, 2L, TODAY);
        Map<String, Object> partners = PlanService.view(row, 1L, TODAY);

        assertEquals(true, mine.get("shared"));
        assertEquals(true, mine.get("mine"));
        assertEquals(false, partners.get("mine"));      // 상대가 만든 함께 약속
        assertEquals(false, mine.get("repeat"));
        assertEquals("내일 저녁 7시 강남에서 데이트", mine.get("text"));
        assertEquals("19:00", mine.get("time"));

    }
}
