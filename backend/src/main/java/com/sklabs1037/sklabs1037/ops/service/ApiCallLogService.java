package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.client.ApiCallLog;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.ops.dao.ApiCallLogDao;
import com.sklabs1037.sklabs1037.ops.dao.ServerMonitorDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 🔌 외부 연동 모니터링
 *
 * 1. 저장 (10초마다): ApiCallLog 큐에 쌓인 기록을 500줄씩 묶어서 INSERT
 *    - 로컬 PC에서는 저장 안 함 (로컬과 운영이 같은 DB라 운영 기록에 섞이지 않게)
 * 2. 경보 (5분마다): 최근 15분 서비스별 실패율을 보고 관리자에게 메일 + 📲
 *    - 메일(SMTP)이 고장 나면 메일 경보는 못 가니까 📲 푸시도 같이 보냄
 * 3. 정리 (매일 03:05): 30일 지난 기록 삭제
 * 4. 화면용: 오늘 서비스별 숫자, 최근 24시간 시간대별, 기능별, 최근 실패
 */
@Slf4j
@Service
public class ApiCallLogService {

    static final int BATCH = 500;               // INSERT 한 번에 넣는 줄 수
    static final int MAX_BATCHES = 40;          // 한 번 저장할 때 최대 2만 줄 (나머지는 다음 차례)
    static final int KEEP_DAYS = 30;
    static final int WINDOW_MINUTES = 15;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private ApiCallLogDao dao;

    @Autowired
    private ServerMonitorDao serverMonitorDao;

    @Autowired
    private BackgroundJobGuard backgroundJobGuard;

    @Autowired
    private OpsAlertService opsAlertService;

    @Autowired
    private NotificationService notificationService;

    // 저장 실패로 버린 줄 수 (화면 경고용)
    private long lostOnSave = 0;

    // ============================================
    // 1. 10초마다 저장
    // ============================================
    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    public void flush() {

        // 로컬 PC: 저장 안 함 (큐는 최대 5만 줄까지만 차고 그다음은 세기만 함 → 메모리 몇 MB)
        if (!backgroundJobGuard.enabled()) return;

        for (int i = 0; i < MAX_BATCHES; i++) {

            List<ApiCallLog.Entry> batch = ApiCallLog.drain(BATCH);
            if (batch.isEmpty()) return;

            try {
                dao.insertBatch(toRows(batch));
            } catch (Exception e) {
                lostOnSave += batch.size();
                log.warn("외부 연동 기록 저장 실패 ({}줄 버림): {}", batch.size(), e.getMessage());
                return;   // DB가 안 되면 이번 차례는 그만 (다음 10초에 다시)
            }

        }

    }

    static List<Map<String, Object>> toRows(List<ApiCallLog.Entry> batch) {

        List<Map<String, Object>> rows = new ArrayList<>(batch.size());

        for (ApiCallLog.Entry e : batch) {
            Map<String, Object> r = new HashMap<>();
            r.put("service", e.service());
            r.put("action", e.action());
            r.put("successYn", e.ok() ? "Y" : "N");
            r.put("statusCode", e.status());
            r.put("elapsedMs", (int) Math.min(Integer.MAX_VALUE, e.elapsedMs()));
            r.put("errorMsg", e.error());
            r.put("calledAt", e.calledAt().atOffset(ZoneOffset.UTC));
            rows.add(r);
        }

        return rows;

    }

    // ============================================
    // 2. 5분마다 경보 확인
    // ============================================
    public record Alert(String key, String service, long calls, long fails, String lastError) {
    }

    // 최근 15분 서비스별 숫자 → 경보 (테스트하기 쉽게 static)
    //   - 5번 이상 불렀는데 절반 넘게 실패
    //   - 또는 3번 이상 불렀는데 전부 실패 (메일·로그인처럼 호출이 적은 서비스)
    static List<Alert> alerts(List<Map<String, Object>> stats) {

        List<Alert> out = new ArrayList<>();

        for (Map<String, Object> s : stats) {

            long calls = num(s.get("calls"));
            long fails = num(s.get("fails"));
            if (fails == 0) continue;

            boolean mostly = calls >= 5 && fails * 2 > calls;
            boolean all = calls >= 3 && fails == calls;

            if (mostly || all) {
                String svc = String.valueOf(s.get("service"));
                Object err = s.get("lastFailError");
                out.add(new Alert("API_FAIL_" + svc, svc, calls, fails, err == null ? null : String.valueOf(err)));
            }

        }

        return out;

    }

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    public void checkAlerts() {

        if (!backgroundJobGuard.enabled()) return;   // 로컬 PC에서는 경보 안 보냄

        try {

            OffsetDateTime since = OffsetDateTime.now().minusMinutes(WINDOW_MINUTES);
            List<Alert> found = alerts(dao.selectServiceStats(since));
            if (found.isEmpty()) return;

            List<Long> admins = serverMonitorDao.selectAdminIds();
            String hour = ZonedDateTime.now(KST).truncatedTo(ChronoUnit.HOURS).toString();

            for (Alert a : found) {

                String title = label(a.service()) + " 연동 실패가 많아요";
                String body = String.format("최근 %d분 동안 %d번 중 %d번 실패했어요.%s",
                        WINDOW_MINUTES, a.calls(), a.fails(), a.lastError() == null ? "" : "\n마지막 실패: " + a.lastError());

                // 메일 (1시간에 한 번) — SMTP 자체가 고장이면 이건 실패하고 푸시만 감
                opsAlertService.alert(a.key(), title, body + "\n\n관리자 › 🔌 외부 연동에서 확인할 수 있어요.");

                for (Long admin : admins) {
                    notificationService.notify(admin, "ADMIN", "🔌 " + title, body, "/admin/api", a.key() + ":" + hour);
                }

            }

        } catch (Exception e) {

            log.warn("외부 연동 경보 확인 실패: {}", e.getMessage());

        }

    }

    // ============================================
    // 3. 매일 새벽 정리
    // ============================================
    @Scheduled(cron = "0 5 3 * * *", zone = "Asia/Seoul")
    public void cleanUp() {

        if (!backgroundJobGuard.enabled()) return;

        try {
            int n = dao.deleteOld(KEEP_DAYS);
            if (n > 0) log.info("오래된 외부 연동 기록 삭제: {}줄", n);
        } catch (Exception e) {
            log.warn("오래된 외부 연동 기록 삭제 실패: {}", e.getMessage());
        }

    }

    // ============================================
    // 4. 화면용
    // ============================================
    public Map<String, Object> dashboard() {

        OffsetDateTime today = ZonedDateTime.now(KST).truncatedTo(ChronoUnit.DAYS).toOffsetDateTime();
        OffsetDateTime day = ZonedDateTime.now(KST).truncatedTo(ChronoUnit.HOURS).minusHours(23).toOffsetDateTime();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("services", dao.selectServiceStats(today));
        out.put("recent15", dao.selectServiceStats(OffsetDateTime.now().minusMinutes(WINDOW_MINUTES)));
        out.put("hourly", dao.selectHourly(day));
        out.put("actions", dao.selectActionStats(today, 30));
        out.put("recentFails", dao.selectRecentFails(30));
        out.put("pending", ApiCallLog.pending());
        out.put("dropped", ApiCallLog.dropped() + lostOnSave);
        out.put("saving", backgroundJobGuard.enabled());
        out.put("labels", LABELS);

        return out;

    }

    // 서비스 → 화면 이름
    static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("TOSS", "토스증권"),
            Map.entry("CLAUDE", "Claude"),
            Map.entry("VOYAGE", "Voyage 임베딩"),
            Map.entry("NEWS_RSS", "Google 뉴스 RSS"),
            Map.entry("FX", "환율(Frankfurter)"),
            Map.entry("KAKAO", "카카오 로그인"),
            Map.entry("NAVER", "네이버 로그인"),
            Map.entry("SMTP", "메일(네이버 SMTP)"),
            Map.entry("PUSH", "푸시 알림"),
            Map.entry("GITHUB", "GitHub")
    );

    static String label(String service) {

        return LABELS.getOrDefault(service, service);

    }

    private static long num(Object o) {

        return o instanceof Number n ? n.longValue() : 0;

    }
}
