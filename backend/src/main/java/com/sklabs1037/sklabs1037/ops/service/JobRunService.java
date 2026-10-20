package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.ops.dao.JobRunDao;
import com.sklabs1037.sklabs1037.ops.vo.JobRunVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 작업 실행 기록 Service
 *
 *   jobRunService.execute("STOCK_DAILY", "SCHEDULE", () -> { ...; return JobOutcome.of(전체, 실패, "요약"); });
 *
 * - 시작할 때 RUNNING 1줄 → 끝나면 SUCCESS / PARTIAL(일부 실패) / FAIL(예외)
 * - 같은 작업이 이미 돌고 있으면 건너뜀 (관리자 수동 실행과 스케줄이 겹쳐도 한 번만)
 * - FAIL이거나 실패가 10% 이상이면 관리자에게 메일
 *   같은 작업은 1시간에 한 번: 서버 메모리 + DB(최근 1시간 안에 이미 실패 기록이 있으면 보냈던 것) → 재시작·배포해도 또 안 감
 * - 로컬 PC처럼 백그라운드 작업이 꺼진 서버에서는 수동 실행(MANUAL)만 (BackgroundJobGuard)
 * - 기록(DB)이 실패해도 작업 자체는 그대로 돌게 (기록 오류는 로그만)
 */
@Slf4j
@Service
public class JobRunService {

    // 실패 비율이 이 이상이면 PARTIAL이어도 메일
    private static final double ALERT_FAIL_RATE = 0.1;

    @Autowired
    private JobRunDao jobRunDao;

    @Autowired
    private OpsAlertService opsAlertService;

    @Autowired
    private BackgroundJobGuard backgroundJobGuard;

    // 서버가 켜진 시각 (그 전부터 RUNNING인 기록 = 중단된 작업)
    private final OffsetDateTime bootAt = OffsetDateTime.now();

    // 지금 돌고 있는 작업
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    // 1분 작업 실패를 마지막으로 기록한 시각
    private final Map<String, java.time.Instant> failureRecordedAt = new ConcurrentHashMap<>();

    // 작업 결과
    public record JobOutcome(Integer total, Integer failed, String message) {

        public static JobOutcome of(Integer total, Integer failed, String message) {
            return new JobOutcome(total, failed, message);
        }

        public static JobOutcome of(String message) {
            return new JobOutcome(null, null, message);
        }
    }

    // 작업 본문
    @FunctionalInterface
    public interface JobBody {
        JobOutcome run() throws Exception;
    }

    // 서버 시작 시: 지난번에 꺼지면서 RUNNING으로 남은 기록 정리
    @EventListener(ApplicationReadyEvent.class)
    public void abortLeftovers() {

        try {

            JobRunVO vo = new JobRunVO();
            vo.setBootAt(bootAt);

            int n = jobRunDao.abortStaleRuns(vo);
            if (n > 0) log.info("중단된 작업 기록 정리: {}건", n);

        } catch (Exception e) {

            log.warn("중단된 작업 기록 정리 실패: {}", e.getMessage());

        }

    }

    // 작업 실행 + 기록 → 실행했으면 true, 이미 돌고 있어서 건너뛰었으면 false
    public boolean execute(String job, String trigger, JobBody body) {

        // 로컬 PC 등 백그라운드 작업이 꺼진 서버: 관리자 수동 실행만
        if (!"MANUAL".equals(trigger) && !backgroundJobGuard.enabled()) {
            log.debug("백그라운드 작업 꺼짐 → 건너뜀: {} ({})", job, trigger);
            return false;
        }

        if (!running.add(job)) {
            log.info("이미 실행 중이라 건너뜀: {} ({})", job, trigger);
            return false;
        }

        Long runId = start(job, trigger);

        try {

            JobOutcome out = body.run();
            if (out == null) out = JobOutcome.of(null);

            int failed = out.failed() == null ? 0 : out.failed();
            String status = failed > 0 ? "PARTIAL" : "SUCCESS";

            finish(runId, status, out.total(), out.failed(), out.message());

            // 실패가 많으면 메일
            // (전체 수를 모르는 실패 = 단계 자체가 실패한 것도 메일)
            Integer total = out.total();
            boolean many = total == null || total == 0 || (double) failed / total >= ALERT_FAIL_RATE;
            if (failed > 0 && many && !alertedRecently(job, runId)) {
                opsAlertService.alert(job, label(job) + " 일부 실패 (" + failed + (total == null || total == 0 ? "" : "/" + total) + ")",
                        label(job) + " 작업에서 실패가 있었어요. (" + trigger + ")\n\n" + nullToEmpty(out.message()));
            }

            return true;

        } catch (Exception e) {

            log.error("작업 실패: {} ({})", job, trigger, e);
            finish(runId, "FAIL", null, null, errorText(e));

            if (!alertedRecently(job, runId)) opsAlertService.alert(job, label(job) + " 실패",
                    label(job) + " 작업이 실패했어요. (" + trigger + ")\n\n에러: " + errorText(e));

            return true;

        } finally {

            running.remove(job);

        }

    }

    // 1분마다 도는 작업의 실패만 기록 + 메일 (성공은 기록하지 않음)
    public void recordFailure(String job, String trigger, Exception e) {

        if (!backgroundJobGuard.enabled()) return;

        // 계속 실패해도 기록은 10분에 한 줄만 (1분마다 쌓이지 않게)
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant before = failureRecordedAt.get(job);
        boolean record = before == null || now.isAfter(before.plusSeconds(600));
        if (record) failureRecordedAt.put(job, now);

        if (record) {

            try {

                JobRunVO vo = new JobRunVO();
                vo.setJobName(job);
                vo.setTriggerType(trigger);
                vo.setStatus("FAIL");
                vo.setMessage(errorText(e));
                jobRunDao.insertFinishedRun(vo);

            } catch (Exception ex) {

                log.warn("작업 실패 기록 실패: {} ({})", job, ex.getMessage());

            }

        }

        if (!alertedRecently(job, null, 1)) {
            opsAlertService.alert(job, label(job) + " 실패", label(job) + " 작업이 실패했어요.\n\n에러: " + errorText(e));
        }

    }

    // 최근 1시간 안에 같은 작업의 다른 실패 기록이 있으면 = 그때 이미 메일을 보냄 (서버 재시작에도 유지)
    private boolean alertedRecently(String job, Long runId) {

        return alertedRecently(job, runId, 0);

    }

    // allowed: 방금 넣은 실패 기록처럼 세지 않을 개수
    private boolean alertedRecently(String job, Long runId, int allowed) {

        try {

            JobRunVO vo = new JobRunVO();
            vo.setJobName(job);
            vo.setRunId(runId);
            vo.setSince(OffsetDateTime.now().minusHours(1));

            return jobRunDao.countProblemsSince(vo) > allowed;

        } catch (Exception e) {

            return false;   // 확인을 못 하면 메모리 기준(1시간 한 번)만

        }

    }

    // 이 서버에서 백그라운드 작업을 돌리는지 (로컬 PC면 false)
    public boolean backgroundEnabled() {

        return backgroundJobGuard.enabled();

    }

    public boolean isRunning(String job) {

        return running.contains(job);

    }

    public Set<String> runningJobs() {

        return Set.copyOf(running);

    }

    // 화면·메일에 쓰는 작업 이름
    public static String label(String job) {

        return LABELS.getOrDefault(job, job);

    }

    public static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("STOCK_DAILY", "시세 정기 수집"),
            Map.entry("STOCK_STARTUP", "시세 시작 점검"),
            Map.entry("STOCK_STALE", "놓친 시세 따라잡기"),
            Map.entry("NEWS_COLLECT", "뉴스 수집"),
            Map.entry("NEWS_SUMMARY", "AI 뉴스 요약"),
            Map.entry("MOCK_EXPIRE", "지정가 주문 만료"),
            Map.entry("MOCK_SNAPSHOT", "모의투자 자산 기록"),
            Map.entry("MOCK_MATCH", "지정가 체결 확인"),
            Map.entry("ALERT_CHECK", "가격 알림 확인"),
            Map.entry("MORNING_BRIEF", "아침 브리핑 메일"),
            Map.entry("AI_LEAGUE", "AI 투자 리그 매매"),
            Map.entry("AI_IMPROVE", "AI 비서 자기 개선"),
            Map.entry("QA_BOT", "AI QA 봇 화면 점검"),
            Map.entry("WATCHDOG", "작업 감시")
    );

    // ============================================
    // 공통
    // ============================================

    private Long start(String job, String trigger) {

        try {

            JobRunVO vo = new JobRunVO();
            vo.setJobName(job);
            vo.setTriggerType(trigger);
            jobRunDao.insertRun(vo);

            return vo.getRunId();

        } catch (Exception e) {

            log.warn("작업 시작 기록 실패: {} ({})", job, e.getMessage());
            return null;

        }

    }

    private void finish(Long runId, String status, Integer total, Integer failed, String message) {

        if (runId == null) return;

        try {

            JobRunVO vo = new JobRunVO();
            vo.setRunId(runId);
            vo.setStatus(status);
            vo.setTotalCount(total);
            vo.setFailCount(failed);
            vo.setMessage(cut(message));
            jobRunDao.finishRun(vo);

        } catch (Exception e) {

            log.warn("작업 끝 기록 실패: {} ({})", runId, e.getMessage());

        }

    }

    private static String errorText(Exception e) {

        String text = e.getClass().getSimpleName() + ": " + nullToEmpty(e.getMessage());

        return cut(text);

    }

    private static String cut(String text) {

        if (text == null) return null;

        return text.length() > 1000 ? text.substring(0, 997) + "..." : text;

    }

    private static String nullToEmpty(String text) {

        return text == null ? "" : text;

    }
}
