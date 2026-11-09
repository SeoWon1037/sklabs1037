package com.sklabs1037.sklabs1037.mock.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 🏆 모의투자 주간 시즌 마감 (토요일 09:00)
 * - 작업 내용: MockSeasonService.closeWeek / 기록·실패 메일: OpsJobs → JobRunService
 */
@Component
public class MockSeasonScheduler {

    @Autowired
    private OpsJobs opsJobs;

    @Scheduled(cron = "0 0 9 * * SAT", zone = "Asia/Seoul")
    public void close() {

        opsJobs.run("SEASON_CLOSE", "SCHEDULE");

    }
}
