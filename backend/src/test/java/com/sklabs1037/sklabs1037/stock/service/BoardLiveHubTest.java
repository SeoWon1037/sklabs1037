package com.sklabs1037.sklabs1037.stock.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실시간 토론방 연결 관리 규칙
 * - 종목별로 보는 사람 수가 따로 세어지는지
 * - 메인 화면(전 종목) 연결은 종목 인원에 안 섞이는지
 * - 이벤트를 보내도 연결이 유지되는지 (보내기 실패한 연결만 정리)
 */
class BoardLiveHubTest {

    @Test
    void 종목마다_보는_사람_수를_따로_센다() {

        BoardLiveHub hub = new BoardLiveHub();

        assertNotNull(hub.subscribe(1L));
        assertNotNull(hub.subscribe(1L));
        assertNotNull(hub.subscribe(2L));
        assertNotNull(hub.subscribe(null));   // 메인 화면

        assertEquals(2, hub.viewers(1L));
        assertEquals(1, hub.viewers(2L));
        assertEquals(0, hub.viewers(3L));
        assertEquals(4, hub.totalConnections());

    }

    @Test
    void 이벤트를_보내도_연결은_그대로() {

        BoardLiveHub hub = new BoardLiveHub();
        hub.subscribe(1L);
        hub.subscribe(null);

        hub.postCreated(1L);
        hub.commentCreated(1L, 10L);
        hub.deleted(1L, 10L, true);
        hub.heartbeat();

        assertEquals(1, hub.viewers(1L));
        assertEquals(2, hub.totalConnections());

    }
}
