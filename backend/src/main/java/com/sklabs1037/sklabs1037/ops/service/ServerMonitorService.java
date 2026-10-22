package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.dm.service.DmLiveHub;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.ops.dao.ServerMonitorDao;
import com.sklabs1037.sklabs1037.stock.service.BoardLiveHub;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 🖥 서버 상태 (운영 서버 + DB 서버) — 관리자 화면에서 5초마다 실시간
 *
 * 5초마다 한 번 재서 최근 1시간(720개)을 메모리에만 둠 (DB에 안 쌓음 → 가볍고, 배포하면 처음부터)
 *   운영 서버(EC2 · Docker 컨테이너 안 Spring Boot)
 *     - CPU: 서버 전체 / 우리 앱(이 Java 프로세스) — JDK가 컨테이너 한도를 알아서 반영
 *     - 메모리: 서버 전체 / Java 힙, 디스크(/), 스레드, 켜진 시간, 실시간 접속(토론방·메시지 SSE)
 *   DB 서버(RDS PostgreSQL) — PostgreSQL 기본 통계 뷰
 *     - 응답 속도(SELECT 1), 연결 수(전체 / 최대), 초당 트랜잭션(누적값 차이), 캐시 적중률, 가장 오래 도는 쿼리, DB 크기
 *
 * 경보 (메일 + 📲, 같은 경보는 1시간에 한 번 · 로컬 PC에서는 안 보냄)
 *   CPU 90%↑ 5분 · 메모리 90%↑ 5분 · 디스크 85%↑ · DB 연결 80%↑ · DB 응답 1초↑ 3번 연속 · DB 연결 실패 3번 연속
 */
@Slf4j
@Service
public class ServerMonitorService {

    static final int KEEP = 720;            // 5초 × 720 = 1시간
    static final int SUSTAIN = 60;          // 5분 = 60번 연속

    @Autowired
    private ServerMonitorDao dao;

    @Autowired
    private BoardLiveHub boardLiveHub;

    @Autowired
    private DmLiveHub dmLiveHub;

    @Autowired
    private OpsAlertService opsAlertService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private BackgroundJobGuard backgroundJobGuard;

    // 한 번 잰 값 (-1 = 모름)
    public record Sample(long t, double cpu, double appCpu, long memUsed, long memTotal, long heapUsed, long heapMax,
                         long diskUsed, long diskTotal, int threads, int live,
                         boolean dbOk, long dbMs, int dbConn, int dbConnAll, int dbMax, double dbTps, double cacheHit, double longestSec,
                         long swapUsed, long swapTotal) {
    }

    private final Deque<Sample> history = new ArrayDeque<>();
    private volatile Map<String, Object> dbInfo = Map.of();        // 버전·켜진 시각·크기 (자주 안 바뀜)
    private volatile List<Map<String, Object>> connStates = List.of();
    private volatile List<Map<String, Object>> topTables = List.of();
    private volatile long topTablesAt = 0;

    private long prevXacts = -1;
    private long prevAt = 0;

    // ============================================
    // 5초마다 재기
    // ============================================
    @Scheduled(fixedDelay = 5000, initialDelay = 10_000)
    public void sample() {

        try {
            Sample s = measure();
            synchronized (history) {
                history.addLast(s);
                while (history.size() > KEEP) history.removeFirst();
            }
            if (backgroundJobGuard.enabled()) raise(alerts(recent(SUSTAIN), s));
        } catch (Exception e) {
            log.warn("서버 상태 재기 실패: {}", e.getMessage());
        }

    }

    Sample measure() {

        long now = System.currentTimeMillis();

        // ----- 운영 서버 -----
        double cpu = -1, appCpu = -1;
        long memTotal = -1, memUsed = -1;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            cpu = os.getCpuLoad();
            appCpu = os.getProcessCpuLoad();
            memTotal = os.getTotalMemorySize();
            memUsed = memTotal - os.getFreeMemorySize();
        }
        // 리눅스는 남는 메모리를 파일 캐시로 쓰고 필요하면 바로 돌려줌 → "free"로 재면 늘 꽉 차 보임
        // /proc/meminfo 의 MemAvailable(실제로 더 쓸 수 있는 양)로 다시 계산 + 스왑
        long swapTotal = -1, swapUsed = -1;
        Map<String, Long> mi = meminfo();
        if (mi.containsKey("MemTotal") && mi.containsKey("MemAvailable")) {
            memTotal = mi.get("MemTotal");
            memUsed = memTotal - mi.get("MemAvailable");
        }
        if (mi.containsKey("SwapTotal") && mi.containsKey("SwapFree")) {
            swapTotal = mi.get("SwapTotal");
            swapUsed = swapTotal - mi.get("SwapFree");
        }
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        long heapUsed = mem.getHeapMemoryUsage().getUsed();
        long heapMax = mem.getHeapMemoryUsage().getMax();
        File root = new File("/");
        long diskTotal = root.getTotalSpace();
        long diskUsed = diskTotal - root.getUsableSpace();
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        int live = boardLiveHub.totalConnections() + dmLiveHub.totalConnections();

        // ----- DB 서버 -----
        boolean dbOk = false;
        long dbMs = -1;
        int dbConn = -1, dbAll = -1, dbMax = -1;
        double tps = -1, hit = -1, longest = -1;
        try {
            long t0 = System.nanoTime();
            dao.ping();
            dbMs = (System.nanoTime() - t0) / 1_000_000;
            dbOk = true;

            Map<String, Object> st = dao.selectDbStats();
            if (st != null) {
                dbConn = intOf(st.get("connections"));
                dbAll = intOf(st.get("allConnections"));
                dbMax = intOf(st.get("maxConnections"));
                long xacts = longOf(st.get("xacts"));
                if (prevXacts >= 0 && now > prevAt) tps = Math.max(0, (xacts - prevXacts) * 1000.0 / (now - prevAt));
                prevXacts = xacts;
                prevAt = now;
                hit = cacheHit(longOf(st.get("blksHit")), longOf(st.get("blksRead")));
                dbInfo = st;
            }
            connStates = dao.selectConnStates();
            Double l = dao.selectLongestActive();
            longest = l == null ? 0 : l;
        } catch (Exception e) {
            log.warn("DB 상태 재기 실패: {}", e.getMessage());
        }

        return new Sample(now, round(cpu), round(appCpu), memUsed, memTotal, heapUsed, heapMax, diskUsed, diskTotal, threads, live,
                dbOk, dbMs, dbConn, dbAll, dbMax, Math.round(tps * 10) / 10.0, round(hit), Math.round(longest * 10) / 10.0,
                swapUsed, swapTotal);

    }

    // ============================================
    // 화면용
    // ============================================
    public Map<String, Object> snapshot() {

        List<Sample> all;
        synchronized (history) {
            all = new ArrayList<>(history);
        }

        // 큰 테이블은 1분에 한 번만 (조금 무거운 쿼리)
        if (System.currentTimeMillis() - topTablesAt > 60_000) {
            try {
                topTables = dao.selectTopTables();
                topTablesAt = System.currentTimeMillis();
            } catch (Exception e) {
                log.warn("큰 테이블 조회 실패: {}", e.getMessage());
            }
        }

        var rt = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("startedAt", rt.getStartTime());
        app.put("cpus", Runtime.getRuntime().availableProcessors());
        app.put("java", System.getProperty("java.version"));
        app.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        app.put("boardLive", boardLiveHub.totalConnections());
        app.put("dmLive", dmLiveHub.totalConnections());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("now", all.isEmpty() ? null : all.get(all.size() - 1));
        out.put("history", all);
        out.put("app", app);
        out.put("db", dbInfo);
        out.put("connStates", connStates);
        out.put("topTables", topTables);
        out.put("intervalSec", 5);
        return out;

    }

    private List<Sample> recent(int n) {

        synchronized (history) {
            List<Sample> all = new ArrayList<>(history);
            return all.subList(Math.max(0, all.size() - n), all.size());
        }

    }

    // ============================================
    // 경보 (순수 계산, 테스트 가능)
    // ============================================
    public record Alert(String key, String title, String body) {
    }

    static List<Alert> alerts(List<Sample> recent, Sample now) {

        List<Alert> out = new ArrayList<>();
        int n = recent.size();

        if (n >= SUSTAIN && recent.stream().allMatch(s -> s.cpu() >= 0.9)) {
            out.add(new Alert("SERVER_CPU", "운영 서버 CPU 90% 이상 5분", "지금 CPU " + pct(now.cpu()) + " (우리 앱 " + pct(now.appCpu()) + ")"));
        }
        if (n >= SUSTAIN && recent.stream().allMatch(s -> ratio(s.memUsed(), s.memTotal()) >= 0.9)) {
            out.add(new Alert("SERVER_MEM", "운영 서버 메모리 90% 이상 5분", "지금 메모리 " + pct(ratio(now.memUsed(), now.memTotal()))));
        }
        if (ratio(now.diskUsed(), now.diskTotal()) >= 0.85) {
            out.add(new Alert("SERVER_DISK", "운영 서버 디스크 85% 이상", "지금 디스크 " + pct(ratio(now.diskUsed(), now.diskTotal())) + " · 로그·도커 이미지 정리가 필요해요"));
        }
        if (now.dbMax() > 0 && now.dbConnAll() >= now.dbMax() * 0.8) {
            out.add(new Alert("DB_CONN", "DB 연결 수 80% 이상", "지금 " + now.dbConnAll() + " / 최대 " + now.dbMax()));
        }
        List<Sample> last3 = recent.subList(Math.max(0, n - 3), n);
        if (last3.size() == 3 && last3.stream().allMatch(s -> s.dbOk() && s.dbMs() >= 1000)) {
            out.add(new Alert("DB_SLOW", "DB 응답 1초 이상 (3번 연속)", "지금 응답 " + now.dbMs() + "ms"));
        }
        if (last3.size() == 3 && last3.stream().noneMatch(Sample::dbOk)) {
            out.add(new Alert("DB_DOWN", "DB에 연결되지 않아요 (15초째)", "RDS 상태와 보안 그룹을 확인해 주세요"));
        }
        return out;

    }

    private void raise(List<Alert> list) {

        if (list.isEmpty()) return;
        String hour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        List<Long> admins = List.of();
        try {
            admins = dao.selectAdminIds();
        } catch (Exception e) {
            log.warn("관리자 조회 실패: {}", e.getMessage());
        }
        for (Alert a : list) {
            if (!opsAlertService.alert(a.key(), a.title(), a.body() + "\n\n관리자 › 🖥 서버 상태에서 확인할 수 있어요.")) continue;   // 1시간 안에 보냈으면 건너뜀
            for (Long admin : admins) {
                notificationService.notify(admin, "ADMIN", "🚨 " + a.title(), a.body(), "/admin/server", a.key() + ":" + hour);
            }
        }

    }

    private static Map<String, Long> meminfo() {

        try {
            return parseMeminfo(java.nio.file.Files.readString(java.nio.file.Path.of("/proc/meminfo")));
        } catch (Exception e) {
            return Map.of();   // 리눅스가 아니면 (로컬 윈도우) JVM 값 그대로
        }

    }

    // "MemAvailable:   512000 kB" → 바이트
    static Map<String, Long> parseMeminfo(String text) {

        Map<String, Long> m = new HashMap<>();
        for (String line : text.split("\n")) {
            String[] p = line.trim().split("\\s+");
            if (p.length >= 2 && p[0].endsWith(":")) {
                try {
                    m.put(p[0].substring(0, p[0].length() - 1), Long.parseLong(p[1]) * 1024);
                } catch (NumberFormatException ignore) {
                    // 숫자가 아닌 줄
                }
            }
        }
        return m;

    }

    static double cacheHit(long hit, long read) {

        return hit + read == 0 ? -1 : (double) hit / (hit + read);

    }

    static double ratio(long used, long total) {

        return total <= 0 || used < 0 ? -1 : (double) used / total;

    }

    private static String pct(double v) {

        return v < 0 ? "-" : Math.round(v * 100) + "%";

    }

    private static double round(double v) {

        return v < 0 || Double.isNaN(v) ? -1 : Math.round(v * 1000) / 1000.0;

    }

    private static int intOf(Object o) {

        return o instanceof Number n ? n.intValue() : -1;

    }

    private static long longOf(Object o) {

        return o instanceof Number n ? n.longValue() : 0;

    }
}
