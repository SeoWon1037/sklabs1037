package com.sklabs1037.sklabs1037.demo.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🧪 체험 계정: 할 수 있는 쓰기 / 막는 쓰기
 */
class DemoServiceTest {

    @Test
    void 읽기는_전부_허용() {

        assertTrue(DemoService.allowed("GET", "/api/dm/rooms"));
        assertTrue(DemoService.allowed("get", "/api/admin/server"));   // 관리자 화면은 원래 권한 검사로 막힘

    }

    @Test
    void 체험_기능은_허용() {

        assertTrue(DemoService.allowed("POST", "/api/mock/order"));
        assertTrue(DemoService.allowed("POST", "/api/mock/orders/12/cancel"));
        assertTrue(DemoService.allowed("POST", "/api/ai/chat"));
        assertTrue(DemoService.allowed("POST", "/api/beauty/analyze"));
        assertTrue(DemoService.allowed("POST", "/api/user/logout"));
        // 체험 중에 진짜 계정으로 바꾸기
        assertTrue(DemoService.allowed("POST", "/api/user/login"));
        assertTrue(DemoService.allowed("POST", "/api/user/signup"));
        assertTrue(DemoService.allowed("POST", "/api/user/signup/send-code"));
        assertTrue(DemoService.allowed("POST", "/api/user/find/reset/send-code"));
        assertTrue(DemoService.allowed("DELETE", "/api/watchlist/5"));

    }

    @Test
    void 남에게_보이거나_계정을_바꾸는_건_막음() {

        assertFalse(DemoService.allowed("POST", "/api/stock/board"));
        assertFalse(DemoService.allowed("POST", "/api/dm/rooms/3/messages"));
        assertFalse(DemoService.allowed("POST", "/api/support"));
        assertFalse(DemoService.allowed("POST", "/api/user/mypage/password"));
        assertFalse(DemoService.allowed("POST", "/api/user/mypage/withdraw"));
        assertFalse(DemoService.allowed("POST", "/api/push/subscribe"));
        assertFalse(DemoService.allowed("POST", "/api/mock/reset-request"));
        assertFalse(DemoService.allowed("POST", "/api/couple/heart"));
        assertFalse(DemoService.allowed("POST", "/api/ai/../dm/rooms"));

    }
}
