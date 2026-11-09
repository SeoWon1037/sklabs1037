package com.sklabs1037.sklabs1037.ops.config;

import com.sklabs1037.sklabs1037.ops.config.RateLimitGuard.Rule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🚦 요청 횟수 제한: 허용 횟수까지 통과 → 넘으면 남은 초 → 기간이 지나면 다시 통과, IP마다 따로
 */
class RateLimitGuardTest {

    @Test
    void 로그인은_5분에_10번() {

        RateLimitGuard g = new RateLimitGuard();
        Rule r = RateLimitGuard.ruleFor("/api/user/login");
        assertEquals(10, r.limit());

        long t = 1_000_000L;
        for (int i = 0; i < 10; i++) assertEquals(0, g.hit(r, "1.1.1.1", t + i));
        long wait = g.hit(r, "1.1.1.1", t + 10_000);
        assertTrue(wait > 280 && wait <= 300, "약 290초 기다리기: " + wait);

        assertEquals(0, g.hit(r, "2.2.2.2", t + 10_000), "다른 IP는 따로");
        assertEquals(0, g.hit(r, "1.1.1.1", t + 300_000), "5분 지나면 다시");

    }

    @Test
    void 규칙_없는_주소는_제한_없음() {

        assertNull(RateLimitGuard.ruleFor("/api/mock/order"));
        assertEquals("/api/user/signup", RateLimitGuard.ruleFor("/api/user/signup/send-code").prefix());
        assertNull(RateLimitGuard.ruleFor("/api/user/logout"));

    }
}
