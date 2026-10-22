package com.sklabs1037.sklabs1037.dm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 💬 메시지 규칙
 */
class DmRulesTest {

    @Test
    void 방은_작은_ID가_A() {

        assertEquals(3L, DmService.pair(7, 3)[0]);
        assertEquals(7L, DmService.pair(7, 3)[1]);
        assertEquals(3L, DmService.pair(3, 7)[0]);

    }

    @Test
    void 메시지_다듬기() {

        assertEquals("안녕", DmService.cleanBody("  안녕 \r\n"));
        assertEquals("a\n\n\nb", DmService.cleanBody("a\n\n\n\n\n\nb"));
        assertThrows(IllegalArgumentException.class, () -> DmService.cleanBody("   "));
        assertThrows(IllegalArgumentException.class, () -> DmService.cleanBody("가".repeat(1001)));

    }

    @Test
    void 푸시_미리보기는_한줄_80자() {

        assertEquals("밥 먹었어?", DmService.preview("밥\n먹었어?"));
        assertEquals(80, DmService.preview("가".repeat(200)).length());

    }
}
