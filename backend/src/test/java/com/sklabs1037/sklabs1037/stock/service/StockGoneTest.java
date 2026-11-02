package com.sklabs1037.sklabs1037.stock.service;

import com.sklabs1037.sklabs1037.stock.vo.CollectResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 토스에 없는 종목 (상장폐지·코드 변경)
 * - 404 stock-not-found 는 실패가 아니라 "수집 대상에서 빼기" (2026-11-02 시세 시작 점검 실패 메일)
 */
class StockGoneTest {

    @Test
    void stock_not_found만_빼기() {

        RuntimeException gone = new RuntimeException("404 Not Found: \"{\"error\":{\"code\":\"stock-not-found\",\"message\":\"종목을 찾을 수 없습니다.\"}}\"");
        assertTrue(StockCollectServiceImpl.isStockGone(gone));
        assertTrue(StockCollectServiceImpl.isStockGone(new IllegalStateException("감싼 오류", gone)), "원인(cause) 안에 있어도");
        assertFalse(StockCollectServiceImpl.isStockGone(new RuntimeException("401 invalid-token")));
        assertFalse(StockCollectServiceImpl.isStockGone(new RuntimeException((String) null)));

    }

    @Test
    void 요약에_제외_개수() {

        assertEquals("종목 5개, 저장 5건, 실패 0종목, 토스에 없는 종목 4개 제외", new CollectResult(5, 5, 0, null, 4).summary());
        assertEquals("종목 5개, 저장 5건, 실패 0종목", new CollectResult(5, 5, 0, null).summary());

    }
}
