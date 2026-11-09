package com.sklabs1037.sklabs1037.demo.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 🧪 체험 계정 매일 초기화 (05:10)
 * - 작업 내용: DemoService.resetDaily / 기록·실패 메일: OpsJobs → JobRunService
 */
@Component
public class DemoResetScheduler {

    @Autowired
    private OpsJobs opsJobs;

    @Scheduled(cron = "0 10 5 * * *", zone = "Asia/Seoul")
    public void reset() {

        opsJobs.run("DEMO_RESET", "SCHEDULE");

    }
}
