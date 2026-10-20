package com.sklabs1037.sklabs1037.duel.service;

import com.sklabs1037.sklabs1037.me.service.RangeStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ⚔️ 친구 대결 점수 · 승자
 */
class DuelRulesTest {

    @Test
    void 점수는_적중10_정답5_출석3() {

        // 예측 3개 적중, 퀴즈 정답 8개, 출석 5일 → 30 + 40 + 15
        RangeStats s = new RangeStats(9, 12, 10, 3, 2, 8, 1, 1, 5, 4, 0);
        assertEquals(85, DuelService.score(s));
        assertEquals(0, DuelService.score(RangeStats.EMPTY));

    }

    @Test
    void 승자_비기면_없음() {

        assertEquals((Object) 1L, DuelService.winner(1L, 50, 2L, 40));
        assertEquals((Object) 2L, DuelService.winner(1L, 39, 2L, 40));
        assertNull(DuelService.winner(1L, 40, 2L, 40));

    }
}
