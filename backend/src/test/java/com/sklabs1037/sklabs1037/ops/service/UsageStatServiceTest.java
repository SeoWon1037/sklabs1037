package com.sklabs1037.sklabs1037.ops.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📊 사용 통계: 주소 묶기 · 봇 거르기
 */
class UsageStatServiceTest {

    @Test
    void 주소는_첫_칸만() {

        assertEquals("/stock", UsageStatService.pathKey("/stock?symbol=005930"));
        assertEquals("/", UsageStatService.pathKey("/"));
        assertEquals("/mock", UsageStatService.pathKey("/mock/history#x"));
        assertEquals("/calendar", UsageStatService.pathKey("/calendar?month=2026-12"));

    }

    @Test
    void 관리자_이상한_주소는_안_셈() {

        assertNull(UsageStatService.pathKey("/admin/stats"));
        assertNull(UsageStatService.pathKey("https://evil.com"));
        assertNull(UsageStatService.pathKey("/<script>"));
        assertNull(UsageStatService.pathKey(null));
        assertNull(UsageStatService.pathKey("/" + "a".repeat(250)));

    }

    @Test
    void 봇은_안_셈() {

        assertTrue(UsageStatService.isBot("Mozilla/5.0 (compatible; Googlebot/2.1)"));
        assertTrue(UsageStatService.isBot("Mozilla/5.0 HeadlessChrome/130"));
        assertTrue(UsageStatService.isBot(null));
        assertFalse(UsageStatService.isBot("Mozilla/5.0 (iPhone; CPU iPhone OS 26_5 like Mac OS X) Safari/604.1"));

    }
}
