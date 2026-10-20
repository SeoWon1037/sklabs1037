package com.sklabs1037.sklabs1037.predict.service;

import com.sklabs1037.sklabs1037.predict.vo.PredictVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🎯 예측 게임 규칙
 * - 라운드: 평일 09:00 전이면 오늘, 아니면 다음 평일
 * - 문제: 같은 날짜면 항상 같은 5종목
 * - 채점: 오르면 UP, 내리면 DOWN, 같으면 FLAT / 연속 적중
 */
class PredictRulesTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static ZonedDateTime at(String date, int hour, int minute) {
        return LocalDate.parse(date).atTime(hour, minute).atZone(KST);
    }

    @Test
    void 지금_투표할_라운드() {

        // 2026-10-20 화요일
        assertEquals(LocalDate.parse("2026-10-20"), PredictService.roundDate(at("2026-10-20", 8, 59)));
        assertEquals(LocalDate.parse("2026-10-21"), PredictService.roundDate(at("2026-10-20", 9, 0)));
        assertEquals(LocalDate.parse("2026-10-21"), PredictService.roundDate(at("2026-10-20", 21, 0)));

        // 금요일 장 시작 뒤 → 다음 월요일, 토요일 → 월요일
        assertEquals(LocalDate.parse("2026-10-26"), PredictService.roundDate(at("2026-10-23", 10, 0)));
        assertEquals(LocalDate.parse("2026-10-26"), PredictService.roundDate(at("2026-10-24", 7, 0)));

    }

    @Test
    void 같은_날짜면_같은_5종목() {

        List<PredictVO> candidates = new ArrayList<>();
        for (long i = 1; i <= 30; i++) {
            PredictVO v = new PredictVO();
            v.setStockId(i);
            candidates.add(v);
        }

        LocalDate day = LocalDate.parse("2026-10-21");
        List<Long> first = PredictService.pick(day, candidates).stream().map(PredictVO::getStockId).toList();
        List<Long> again = PredictService.pick(day, candidates).stream().map(PredictVO::getStockId).toList();
        List<Long> other = PredictService.pick(day.plusDays(1), candidates).stream().map(PredictVO::getStockId).toList();

        assertEquals(5, first.size());
        assertEquals(first, again);
        assertFalse(first.equals(other));
        assertEquals(5, first.stream().distinct().count());

    }

    @Test
    void 채점과_연속_적중() {

        assertEquals("UP", PredictService.grade(new BigDecimal("100"), new BigDecimal("101")));
        assertEquals("DOWN", PredictService.grade(new BigDecimal("100"), new BigDecimal("99.5")));
        assertEquals("FLAT", PredictService.grade(new BigDecimal("100.00"), new BigDecimal("100")));

        List<PredictVO> recent = new ArrayList<>();
        for (Boolean b : new Boolean[]{true, true, true, false, true}) {
            PredictVO v = new PredictVO();
            v.setCorrect(b);
            recent.add(v);
        }

        assertEquals(3, PredictService.streak(recent));
        assertEquals(0, PredictService.streak(List.of()));

    }
}
