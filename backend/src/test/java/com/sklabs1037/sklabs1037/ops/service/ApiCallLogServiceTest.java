package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.client.ApiCallLog;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🔌 외부 연동 경보 규칙 (최근 15분)
 * - 5번 이상 중 절반 넘게 실패 → 경보
 * - 3번 이상 전부 실패 → 경보 (메일·로그인처럼 적게 부르는 서비스)
 * - 가끔 하나 실패는 경보 아님
 */
class ApiCallLogServiceTest {

    private static Map<String, Object> stat(String svc, long calls, long fails) {
        Map<String, Object> m = new HashMap<>();
        m.put("service", svc);
        m.put("calls", calls);
        m.put("fails", fails);
        m.put("lastFailError", fails > 0 ? "HTTP 401" : null);
        return m;
    }

    private static List<String> keys(List<ApiCallLogService.Alert> a) {
        return a.stream().map(ApiCallLogService.Alert::key).toList();
    }

    @Test
    void 가끔_실패는_경보_없음() {

        assertTrue(ApiCallLogService.alerts(List.of(stat("TOSS", 3900, 12), stat("CLAUDE", 40, 1))).isEmpty());
        assertTrue(ApiCallLogService.alerts(List.of(stat("TOSS", 10, 5))).isEmpty(), "딱 절반은 아직 아님");

    }

    @Test
    void 절반_넘게_실패하면_경보() {

        List<ApiCallLogService.Alert> a = ApiCallLogService.alerts(List.of(stat("TOSS", 100, 60), stat("CLAUDE", 40, 0)));
        assertEquals(List.of("API_FAIL_TOSS"), keys(a));
        assertEquals("HTTP 401", a.get(0).lastError());

    }

    @Test
    void 적게_불러도_전부_실패면_경보() {

        assertEquals(List.of("API_FAIL_SMTP"), keys(ApiCallLogService.alerts(List.of(stat("SMTP", 3, 3)))));
        assertTrue(ApiCallLogService.alerts(List.of(stat("SMTP", 2, 2))).isEmpty(), "두 번은 우연일 수 있음");

    }

    @Test
    void 저장용_한_줄로_바꾸기() {

        Instant at = Instant.parse("2026-10-26T03:00:00Z");
        Map<String, Object> row = ApiCallLogService.toRows(List.of(
                new ApiCallLog.Entry("TOSS", "prices", false, 401, 85, "HTTP 401", at))).get(0);

        assertEquals("N", row.get("successYn"));
        assertEquals(401, row.get("statusCode"));
        assertEquals(85, row.get("elapsedMs"));
        assertEquals(OffsetDateTime.parse("2026-10-26T03:00:00Z"), row.get("calledAt"));

    }
}
