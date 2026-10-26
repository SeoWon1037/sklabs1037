package com.sklabs1037.sklabs1037.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🔌 연동 기록장
 * - 경로 → 기능 이름 (버전·? 뒤 조건은 버림 → 종목코드·키가 기록에 안 남음)
 * - 주소 → 서비스 이름 (카카오·네이버 로그인)
 * - 큐는 MAX_QUEUE까지만, 넘으면 버리고 센다
 */
class ApiCallLogTest {

    @BeforeEach
    void clear() {
        while (!ApiCallLog.drain(10_000).isEmpty()) { }
    }

    @Test
    void 경로에서_기능_이름만() {

        assertEquals("prices", ApiCallLog.actionOf("/api/v1/prices"));
        assertEquals("stocks/all", ApiCallLog.actionOf("/api/v1/stocks/all?market=KOSPI"));
        assertEquals("oauth2/token", ApiCallLog.actionOf("/oauth2/token"));
        assertEquals("messages", ApiCallLog.actionOf("/v1/messages"));
        assertEquals("rss/search", ApiCallLog.actionOf("/rss/search?q=삼성전자"));
        assertEquals("-", ApiCallLog.actionOf("/"));
        assertEquals("-", ApiCallLog.actionOf(null));

    }

    @Test
    void 주소로_서비스_구분() {

        assertEquals("KAKAO", ApiCallLog.serviceOf("kauth.kakao.com"));
        assertEquals("KAKAO", ApiCallLog.serviceOf("kapi.kakao.com"));
        assertEquals("NAVER", ApiCallLog.serviceOf("nid.naver.com"));
        assertEquals("NAVER", ApiCallLog.serviceOf("openapi.naver.com"));
        assertEquals("OTHER", ApiCallLog.serviceOf("example.com"));

    }

    @Test
    void 성공이면_이유를_안_남기고_실패면_남긴다() {

        ApiCallLog.record("TOSS", "prices", true, 200, 120, "무시됨");
        ApiCallLog.record("TOSS", "prices", false, 401, 80, "HTTP 401");

        List<ApiCallLog.Entry> got = ApiCallLog.drain(10);
        assertEquals(2, got.size());
        assertNull(got.get(0).error());
        assertEquals("HTTP 401", got.get(1).error());
        assertEquals(0, ApiCallLog.pending());

    }

    @Test
    void 큐가_가득_차면_버리고_센다() {

        long before = ApiCallLog.dropped();
        for (int i = 0; i < ApiCallLog.MAX_QUEUE + 5; i++) ApiCallLog.record("TOSS", "prices", true, 200, 1, null);

        assertEquals(ApiCallLog.MAX_QUEUE, ApiCallLog.pending());
        assertEquals(5, ApiCallLog.dropped() - before);

    }
}
