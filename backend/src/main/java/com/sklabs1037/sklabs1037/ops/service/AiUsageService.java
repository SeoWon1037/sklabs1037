package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.client.AiBudgetException;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.ops.dao.AiUsageDao;
import com.sklabs1037.sklabs1037.support.dao.InquiryDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 💸 AI 비용 안전장치 (하네스: AI를 쓰는 모든 기능을 한 곳에서 지킴)
 *
 * ClaudeApiClient가 Claude를 부르기 전 / 후에 이 서비스를 거침
 *   전: check()   → 긴급 정지 중이거나 오늘 쓴 돈 ≥ 하루 한도면 AiBudgetException (Claude를 부르지 않음)
 *   후: record()  → 응답의 usage(토큰 수)로 비용 계산 → T_AI_USAGE에 날짜 · 기능별로 더함
 *                   → 한도의 80% · 100%에 관리자 메일 + 📲 (하루 한 번씩)
 *
 * 비용 = 토큰 수 × 1M 토큰당 가격 (application.properties ai.price.*, 기본값 = Claude Haiku 5.5)
 *   - 한 번에 보낸 입력이 10만 토큰을 넘으면 모든 가격 × 5 (긴 입력 요금)
 *   - 실제 청구서와 조금 다를 수 있음 (추정값) → 정확한 금액은 Anthropic 콘솔
 * 기능 이름: Claude를 부른 우리 코드의 클래스 이름 (AdCopyService → AdCopy)
 */
@Slf4j
@Service
public class AiUsageService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long REFRESH_MS = 60_000;

    public record Prices(double input, double output, double cacheWrite, double cacheRead, long longThreshold, double longMultiplier) {
    }

    @Autowired
    private AiUsageDao aiUsageDao;

    @Autowired
    private OpsAlertService opsAlertService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private InquiryDao inquiryDao;

    @Value("${ai.price.input:0.10}")
    private double priceInput;

    @Value("${ai.price.output:0.50}")
    private double priceOutput;

    @Value("${ai.price.cache-write:0.125}")
    private double priceCacheWrite;

    @Value("${ai.price.cache-read:0.01}")
    private double priceCacheRead;

    @Value("${ai.daily-usd-default:2.0}")
    private double defaultDailyUsd;

    // 메모리 캐시 (1분마다 DB와 맞춤, 관리자가 바꾸면 바로)
    private volatile double dailyUsd = -1;
    private volatile boolean paused;
    private volatile LocalDate day = LocalDate.now(KST);
    private volatile double todayCost;
    private volatile long loadedAt;

    private final Set<String> alerted = ConcurrentHashMap.newKeySet();

    // ============================================
    // 부르기 전
    // ============================================
    public void check() {

        refresh(false);

        if (paused) throw new AiBudgetException("AI 기능을 잠시 멈췄어요. (관리자 점검 중) 조금 뒤에 다시 시도해주세요.");
        if (todayCost >= limit()) throw new AiBudgetException("오늘 AI 사용량이 많아서 잠시 멈췄어요. 내일 다시 쓸 수 있어요.");

    }

    // ============================================
    // 부른 뒤 (실패해도 기능에는 영향 없게, 로그만)
    // ============================================
    public void record(String feature, Map<String, Object> response, boolean failed) {

        try {

            Map<?, ?> usage = response != null && response.get("usage") instanceof Map<?, ?> u ? u : Map.of();
            long in = num(usage.get("input_tokens"));
            long out = num(usage.get("output_tokens"));
            long cr = num(usage.get("cache_read_input_tokens"));
            long cw = num(usage.get("cache_creation_input_tokens"));
            double cost = cost(in, out, cr, cw, prices());

            Map<String, Object> row = new HashMap<>();
            row.put("feature", feature);
            row.put("calls", 1);
            row.put("fails", failed ? 1 : 0);
            row.put("inTokens", in);
            row.put("outTokens", out);
            row.put("cacheRead", cr);
            row.put("cacheWrite", cw);
            row.put("cost", cost);
            aiUsageDao.upsert(row);

            rollDay();
            todayCost += cost;
            alertIfNeeded();

        } catch (Exception e) {

            log.warn("AI 사용량 기록 실패: {}", e.getMessage());

        }

    }

    // ============================================
    // 관리자 화면
    // ============================================
    public Map<String, Object> dashboard() {

        refresh(true);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("todayCost", todayCost);
        m.put("dailyUsd", limit());
        m.put("paused", paused);
        m.put("blocked", paused || todayCost >= limit());
        m.put("days", aiUsageDao.selectDays(30));
        m.put("features", aiUsageDao.selectTodayFeatures());
        m.put("month", aiUsageDao.selectMonth());
        m.put("prices", prices());
        return m;

    }

    public void update(Long userId, double usd, boolean pause) {

        if (!(usd >= 0.1 && usd <= 100)) throw new IllegalArgumentException("하루 한도는 $0.1 ~ $100 사이로 정해주세요.");

        Map<String, Object> row = new HashMap<>();
        row.put("dailyUsd", Math.round(usd * 100) / 100.0);
        row.put("pausedYn", pause ? "Y" : "N");
        row.put("userId", userId);
        if (aiUsageDao.updateBudget(row) == 0) throw new IllegalStateException("설정 줄이 없어요. sql/ai-usage.sql을 먼저 실행해주세요.");

        refresh(true);

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================
    public static double cost(long in, long out, long cacheRead, long cacheWrite, Prices p) {

        double m = in + cacheRead + cacheWrite > p.longThreshold() ? p.longMultiplier() : 1.0;
        return (in * p.input() + out * p.output() + cacheRead * p.cacheRead() + cacheWrite * p.cacheWrite()) * m / 1_000_000.0;

    }

    // 스택에서 Claude를 부른 우리 코드 찾기 → "AdCopyService" → "AdCopy"
    public static String feature(StackWalker.StackFrame[] frames) {

        for (StackWalker.StackFrame f : frames) {
            String c = f.getClassName();
            if (!c.startsWith("com.sklabs1037.") || c.contains(".client.") || c.endsWith("AiUsageService")) continue;
            return shortName(c);
        }
        return "기타";

    }

    static String shortName(String className) {

        String s = className.substring(className.lastIndexOf('.') + 1);
        int dollar = s.indexOf('$');
        if (dollar > 0) s = s.substring(0, dollar);
        s = s.replaceAll("(ServiceImpl|Service|Controller|Scheduler|Client)$", "");
        if (s.isEmpty()) s = "기타";
        return s.length() > 40 ? s.substring(0, 40) : s;

    }

    // ============================================
    // 내부
    // ============================================
    private Prices prices() {

        return new Prices(priceInput, priceOutput, priceCacheWrite, priceCacheRead, 100_000, 5.0);

    }

    private double limit() {

        return dailyUsd > 0 ? dailyUsd : defaultDailyUsd;

    }

    private synchronized void refresh(boolean force) {

        rollDay();
        long now = System.currentTimeMillis();
        if (!force && now - loadedAt < REFRESH_MS) return;
        loadedAt = now;

        try {

            Map<String, Object> b = aiUsageDao.selectBudget();
            if (b != null) {
                dailyUsd = b.get("dailyUsd") instanceof Number n ? n.doubleValue() : -1;
                paused = Boolean.TRUE.equals(b.get("paused"));
            }
            Double c = aiUsageDao.selectTodayCost();
            todayCost = c == null ? 0 : c;

        } catch (Exception e) {

            // 테이블이 아직 없으면 (SQL 실행 전) 막지 않음
            log.debug("AI 한도 읽기 실패: {}", e.getMessage());

        }

    }

    private synchronized void rollDay() {

        LocalDate d = LocalDate.now(KST);
        if (!d.equals(day)) {
            day = d;
            todayCost = 0;
            alerted.clear();
        }

    }

    private void alertIfNeeded() {

        double lim = limit();
        String usd = String.format("$%.2f / $%.2f", todayCost, lim);

        if (todayCost >= lim && alerted.add(day + ":100")) {
            send("💸 AI 하루 한도 도달 → AI 기능 멈춤", "오늘 AI 비용이 한도에 닿아서 내일 0시까지 AI 기능을 멈췄어요. (" + usd + ")\n"
                    + "더 써야 하면 관리자 > 💸 AI 비용에서 한도를 올리면 바로 다시 열려요.");
        } else if (todayCost >= lim * 0.8 && alerted.add(day + ":80")) {
            send("💸 AI 비용 80% 사용", "오늘 AI 비용이 한도의 80%를 넘었어요. (" + usd + ")\n어떤 기능이 많이 썼는지 관리자 > 💸 AI 비용에서 볼 수 있어요.");
        }

    }

    private void send(String title, String body) {

        opsAlertService.alert("AI_BUDGET_" + title.hashCode() + "_" + day, title, body);
        for (Long admin : inquiryDao.selectAdminIds()) {
            notificationService.notify(admin, "ADMIN", title, body.split("\n")[0], "/admin/ai-cost", "AI_BUDGET:" + day + ":" + title.hashCode());
        }

    }

    private static long num(Object o) {

        return o instanceof Number n ? n.longValue() : 0;

    }
}
