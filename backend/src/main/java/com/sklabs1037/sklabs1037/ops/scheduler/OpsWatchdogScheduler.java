package com.sklabs1037.sklabs1037.ops.scheduler;

import com.sklabs1037.sklabs1037.ops.dao.JobRunDao;
import com.sklabs1037.sklabs1037.ops.service.JobRunService;
import com.sklabs1037.sklabs1037.ops.service.OpsAlertService;
import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import com.sklabs1037.sklabs1037.ops.vo.JobRunVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 작업 감시 (정해진 시각에 작업이 "성공"했는지 확인)
 * - 실패는 JobRunService가 바로 메일을 보내지만, 서버가 꺼져 있었거나 스케줄이 아예 안 돈 경우는 실패 기록조차 없음
 *   → 이 감시가 "돌았어야 할 작업이 안 돌았다"를 잡아냄 (2026-10-19 20:30 수집이 조용히 빠진 일이 계기)
 * - 평일 21:30: 오늘 20:30 시세 수집 성공 기록이 없으면 → 메일 + 놓친 시세 따라잡기 자동 실행
 * - 평일 15:50: 오늘 15:40 모의투자 자산 기록이 없으면 → 메일 + 다시 실행
 * - 매일 08:00 / 17:00: 07:30 / 16:30 AI 뉴스 요약이 없으면 → 메일 (Claude 비용이 드는 작업이라 자동 재실행은 안 함)
 * - 매일 09:00~23:00 매시 10분: 최근 2시간 뉴스 수집 성공이 없으면 → 메일
 * - 매일 03:00: 60일 지난 기록 삭제
 */
@Slf4j
@Component
public class OpsWatchdogScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private JobRunDao jobRunDao;

    @Autowired
    private OpsAlertService opsAlertService;

    @Autowired
    private OpsJobs opsJobs;

    @Autowired
    private JobRunService jobRunService;

    // 시세 정기 수집 (20:30) 확인 → 없으면 따라잡기
    @Scheduled(cron = "0 30 21 * * MON-FRI", zone = "Asia/Seoul")
    public void checkStockDaily() {

        // 아직 도는 중이면 (종목이 많아 오래 걸리는 날) 기다림
        if (jobRunService.isRunning("STOCK_DAILY") || succeededSince("STOCK_DAILY", todayAt(20, 0))) return;

        opsAlertService.alert("WATCH_STOCK_DAILY", "오늘 시세 정기 수집이 없어요",
                "오늘 20:30 시세 정기 수집의 성공 기록이 없어요. (서버가 꺼져 있었거나 실패)\n"
                        + "놓친 시세 따라잡기를 자동으로 실행합니다.");

        opsJobs.run("STOCK_STALE", "WATCHDOG");

    }

    // 모의투자 자산 기록 (15:40) 확인 → 없으면 다시
    @Scheduled(cron = "0 50 15 * * MON-FRI", zone = "Asia/Seoul")
    public void checkMockSnapshot() {

        if (jobRunService.isRunning("MOCK_SNAPSHOT") || succeededSince("MOCK_SNAPSHOT", todayAt(15, 30))) return;

        opsAlertService.alert("WATCH_MOCK_SNAPSHOT", "오늘 모의투자 자산 기록이 없어요",
                "오늘 15:40 모의투자 자산 기록의 성공 기록이 없어요. 자동으로 다시 실행합니다.");

        opsJobs.run("MOCK_SNAPSHOT", "WATCHDOG");

    }

    // AI 뉴스 요약 (07:30, 16:30) 확인 → 메일만
    @Scheduled(cron = "0 0 8,17 * * *", zone = "Asia/Seoul")
    public void checkNewsSummary() {

        if (succeededSince("NEWS_SUMMARY", ZonedDateTime.now(KST).minusHours(1).toOffsetDateTime())) return;

        opsAlertService.alert("WATCH_NEWS_SUMMARY", "AI 뉴스 요약이 안 됐어요",
                "최근 1시간 안에 AI 뉴스 요약 성공 기록이 없어요.\n"
                        + "Claude 비용이 드는 작업이라 자동으로 다시 돌리지 않아요. 필요하면 운영 화면에서 \"지금 실행\"을 눌러주세요.");

    }

    // 뉴스 수집 (30분마다) 확인
    @Scheduled(cron = "0 10 9-23 * * *", zone = "Asia/Seoul")
    public void checkNewsCollect() {

        if (succeededSince("NEWS_COLLECT", ZonedDateTime.now(KST).minusHours(2).toOffsetDateTime())) return;

        opsAlertService.alert("WATCH_NEWS_COLLECT", "뉴스 수집이 2시간째 없어요",
                "최근 2시간 동안 뉴스 수집 성공 기록이 없어요.");

    }

    // 오래된 기록 정리
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void cleanUp() {

        try {

            JobRunVO vo = new JobRunVO();
            vo.setKeepDays(60);

            int n = jobRunDao.deleteOldRuns(vo);
            if (n > 0) log.info("오래된 작업 기록 삭제: {}건", n);

        } catch (Exception e) {

            log.warn("오래된 작업 기록 삭제 실패: {}", e.getMessage());

        }

    }

    // ============================================
    // 공통
    // ============================================

    // since 이후 성공 기록이 있는지 (DB 오류면 "있다"로 보고 넘어감 → 오탐 메일 방지, 로그만)
    private boolean succeededSince(String job, OffsetDateTime since) {

        try {

            JobRunVO vo = new JobRunVO();
            vo.setJobName(job);
            vo.setSince(since);

            return jobRunDao.countSuccessSince(vo) > 0;

        } catch (Exception e) {

            log.warn("작업 감시 확인 실패: {} ({})", job, e.getMessage());
            return true;

        }

    }

    private OffsetDateTime todayAt(int hour, int minute) {

        return ZonedDateTime.now(KST).with(LocalTime.of(hour, minute)).withSecond(0).withNano(0).toOffsetDateTime();

    }
}
