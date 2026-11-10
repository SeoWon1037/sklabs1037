package com.sklabs1037.sklabs1037.invite.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// 🤝 친구 초대 규칙 (순수 계산)
class InviteRulesTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = 24 * 3600_000L;

    @Test
    void 코드는_회원_번호로_되돌아온다() {

        for (long id : new long[]{1, 2, 37, 999, 123_456, 9_999_999_999L}) {
            assertEquals(Long.valueOf(id), InviteRules.userId(InviteRules.code(id)));
        }
        assertEquals("6WS", InviteRules.code(1));   // 1 × 7919 + 1037 = 8956 → 36진수

    }

    @Test
    void 소문자와_앞뒤_공백도_받는다() {

        assertEquals(Long.valueOf(37), InviteRules.userId("  " + InviteRules.code(37).toLowerCase() + " "));

    }

    @Test
    void 맞지_않는_코드는_null() {

        assertNull(InviteRules.userId(null));
        assertNull(InviteRules.userId(""));
        assertNull(InviteRules.userId("A"));
        assertNull(InviteRules.userId("6WT"));            // 한 글자 다름
        assertNull(InviteRules.userId("한글코드"));
        assertNull(InviteRules.userId("ZZZZZZZZZZZZZ"));   // 너무 김
        assertNull(InviteRules.userId("ST"));              // 1037 → 0번 회원은 없음
        assertNull(InviteRules.userId("6W S"));

    }

    @Test
    void 회원_번호가_이상하면_코드를_만들지_않는다() {

        assertThrows(IllegalArgumentException.class, () -> InviteRules.code(0));
        assertThrows(IllegalArgumentException.class, () -> InviteRules.code(-3));

    }

    @Test
    void 가입_3일_안의_새_회원만_받을_수_있다() {

        assertNull(InviteRules.refuse(5L, 9, null, NOW - DAY, NOW));
        assertNull(InviteRules.refuse(5L, 9, "KAKAO", NOW - 3 * DAY, NOW));
        assertNotNull(InviteRules.refuse(5L, 9, null, NOW - 3 * DAY - 1, NOW));
        assertNotNull(InviteRules.refuse(5L, 9, null, null, NOW));

    }

    @Test
    void 내_링크_체험_계정_틀린_코드는_거절() {

        assertNotNull(InviteRules.refuse(9L, 9, null, NOW, NOW));
        assertNotNull(InviteRules.refuse(5L, 9, "DEMO", NOW, NOW));
        assertNotNull(InviteRules.refuse(5L, 9, "AI", NOW, NOW));
        assertNotNull(InviteRules.refuse(null, 9, null, NOW, NOW));

    }
}
