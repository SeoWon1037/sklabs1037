package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.ops.dao.JobRunDao;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import com.sklabs1037.sklabs1037.ops.vo.JobRunVO;
import com.sklabs1037.sklabs1037.support.Fakes;
import com.sklabs1037.sklabs1037.support.Fakes.Call;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 작업 실행 기록·장애 메일 규칙
 * - 2026-10-20 장애(로컬 PC와 운영이 같은 토스 키로 동시에 수집 → 401 → 메일 폭탄)에서 나온 규칙들을 고정
 */
class JobRunServiceTest {

    private final List<Call> daoCalls = new ArrayList<>();
    private final List<String> alerts = new ArrayList<>();

    private JobRunService service(boolean backgroundEnabled, int problemsInLastHour) {

        Map<String, Function<Object[], Object>> b = new HashMap<>();
        b.put("insertRun", a -> {
            ((JobRunVO) a[0]).setRunId(10L);
            return 1;
        });
        b.put("countProblemsSince", a -> problemsInLastHour);

        JobRunService s = new JobRunService();
        Fakes.set(s, "jobRunDao", Fakes.fake(JobRunDao.class, b, daoCalls));
        Fakes.set(s, "opsAlertService", new OpsAlertService() {
            @Override
            public boolean alert(String key, String title, String body) {
                alerts.add(title);
                return true;
            }
        });
        Fakes.set(s, "backgroundJobGuard", new BackgroundJobGuard(backgroundEnabled ? "true" : "false", "https://sklabs1037.com"));

        return s;

    }

    private JobRunVO finished() {

        return (JobRunVO) daoCalls.stream().filter(c -> c.method().equals("finishRun")).findFirst().orElseThrow().args()[0];

    }

    @Test
    void 성공하면_SUCCESS로_기록하고_메일_없음() {

        JobRunService s = service(true, 0);

        assertTrue(s.execute("STOCK_DAILY", "SCHEDULE", () -> JobOutcome.of(3897, 0, "ok")));

        assertEquals("SUCCESS", finished().getStatus());
        assertEquals(3897, (int) finished().getTotalCount());
        assertTrue(alerts.isEmpty());

    }

    @Test
    void 실패가_10퍼센트_이상이면_PARTIAL_메일() {

        JobRunService s = service(true, 0);

        s.execute("STOCK_STARTUP", "STARTUP", () -> JobOutcome.of(3668, 3222, "401 invalid-token"));

        assertEquals("PARTIAL", finished().getStatus());
        assertEquals(1, alerts.size());

    }

    @Test
    void 실패가_조금이면_PARTIAL이어도_메일_없음() {

        JobRunService s = service(true, 0);

        s.execute("STOCK_DAILY", "SCHEDULE", () -> JobOutcome.of(3897, 3, "3종목 실패"));

        assertEquals("PARTIAL", finished().getStatus());
        assertTrue(alerts.isEmpty());

    }

    @Test
    void 예외는_FAIL로_기록하고_메일() {

        JobRunService s = service(true, 0);

        s.execute("NEWS_SUMMARY", "SCHEDULE", () -> {
            throw new IllegalStateException("Claude 오류");
        });

        assertEquals("FAIL", finished().getStatus());
        assertTrue(finished().getMessage().contains("Claude 오류"));
        assertEquals(1, alerts.size());

    }

    @Test
    void 최근_1시간_안에_같은_실패가_있으면_재시작해도_메일_안_보냄() {

        JobRunService s = service(true, 1);

        s.execute("STOCK_STARTUP", "STARTUP", () -> {
            throw new IllegalStateException("또 실패");
        });

        assertEquals("FAIL", finished().getStatus());
        assertTrue(alerts.isEmpty());

    }

    @Test
    void 로컬_PC에서는_자동_작업을_건너뛰고_수동_실행만() {

        JobRunService s = service(false, 0);
        AtomicBoolean ran = new AtomicBoolean(false);

        assertFalse(s.execute("STOCK_STARTUP", "STARTUP", () -> {
            ran.set(true);
            return JobOutcome.of("x");
        }));
        assertFalse(ran.get());
        assertEquals(0, Fakes.count(daoCalls, "insertRun"));

        assertTrue(s.execute("STOCK_STALE", "MANUAL", () -> {
            ran.set(true);
            return JobOutcome.of("x");
        }));
        assertTrue(ran.get());

    }

    @Test
    void 같은_작업이_돌고_있으면_한_번만() {

        JobRunService s = service(true, 0);
        List<Boolean> inner = new ArrayList<>();

        s.execute("STOCK_DAILY", "SCHEDULE", () -> {
            inner.add(s.execute("STOCK_DAILY", "MANUAL", () -> JobOutcome.of("중복")));
            return JobOutcome.of("ok");
        });

        assertEquals(List.of(false), inner);
        assertFalse(s.isRunning("STOCK_DAILY"), "끝나면 실행 중 표시가 풀려야 함");

    }

    @Test
    void 백그라운드_작업_자동_설정은_운영_주소에서만_켜짐() {

        assertTrue(new BackgroundJobGuard("auto", "https://sklabs1037.com").enabled());
        assertFalse(new BackgroundJobGuard("auto", "http://localhost:5173").enabled());
        assertFalse(new BackgroundJobGuard("auto", "").enabled());
        assertTrue(new BackgroundJobGuard("true", "http://localhost:5173").enabled());
        assertFalse(new BackgroundJobGuard("false", "https://sklabs1037.com").enabled());

    }
}
