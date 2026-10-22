package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.ops.service.ServerMonitorService.Alert;
import com.sklabs1037.sklabs1037.ops.service.ServerMonitorService.Sample;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🖥 서버 경보 규칙
 * - 순간 튀는 건 무시하고 "5분 내내" 높을 때만 (CPU·메모리)
 * - 디스크·DB 연결은 지금 값으로, DB 느림·끊김은 3번 연속일 때만
 */
class ServerMonitorRulesTest {

    private static final long G = 1L << 30;

    private static Sample s(double cpu, long mem, long disk, boolean dbOk, long dbMs, int dbAll) {
        return new Sample(0, cpu, 0.1, mem, 10 * G, 1, 2, disk, 100 * G, 50, 0, dbOk, dbMs, 3, dbAll, 100, 1, 0.99, 0);
    }

    private static List<String> keys(List<Alert> a) {
        return a.stream().map(Alert::key).toList();
    }

    @Test
    void 평소에는_경보_없음() {

        List<Sample> list = new ArrayList<>();
        for (int i = 0; i < 60; i++) list.add(s(0.3, 4 * G, 50 * G, true, 5, 10));
        assertTrue(ServerMonitorService.alerts(list, list.get(59)).isEmpty());

    }

    @Test
    void CPU는_5분_내내_높을_때만() {

        List<Sample> list = new ArrayList<>();
        for (int i = 0; i < 60; i++) list.add(s(0.95, 4 * G, 50 * G, true, 5, 10));
        assertEquals(List.of("SERVER_CPU"), keys(ServerMonitorService.alerts(list, list.get(59))));

        list.set(30, s(0.5, 4 * G, 50 * G, true, 5, 10));   // 중간에 한 번 내려감
        assertTrue(ServerMonitorService.alerts(list, list.get(59)).isEmpty());

        assertTrue(ServerMonitorService.alerts(list.subList(0, 10), list.get(9)).isEmpty(), "5분 안 됐으면 경보 없음");

    }

    @Test
    void 디스크_DB연결_DB느림_DB끊김() {

        Sample disk = s(0.1, G, 90 * G, true, 5, 10);
        assertEquals(List.of("SERVER_DISK"), keys(ServerMonitorService.alerts(List.of(disk), disk)));

        Sample conn = s(0.1, G, 10 * G, true, 5, 85);
        assertEquals(List.of("DB_CONN"), keys(ServerMonitorService.alerts(List.of(conn), conn)));

        Sample slow = s(0.1, G, 10 * G, true, 1500, 10);
        assertEquals(List.of("DB_SLOW"), keys(ServerMonitorService.alerts(List.of(slow, slow, slow), slow)));
        assertTrue(ServerMonitorService.alerts(List.of(slow, slow), slow).isEmpty());

        Sample down = s(0.1, G, 10 * G, false, -1, -1);
        assertEquals(List.of("DB_DOWN"), keys(ServerMonitorService.alerts(List.of(down, down, down), down)));

    }

    @Test
    void 캐시_적중률과_비율() {

        assertTrue(Math.abs(ServerMonitorService.cacheHit(3, 1) - 0.75) < 1e-9);
        assertTrue(ServerMonitorService.cacheHit(0, 0) < 0, "읽은 적 없으면 모름(-1)");
        assertTrue(ServerMonitorService.ratio(5, 0) < 0, "전체를 모르면 모름(-1)");

    }
}
