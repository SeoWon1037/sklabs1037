package com.sklabs1037.sklabs1037.autofix.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 🔁 매일 스스로 개선 스케줄러 (매일 09:20 — 08:30 QA 봇 결과까지 본 뒤)
 * - 작업 내용: DailyImproveService.run / 기록·실패 메일·로컬 PC에서 끄기: OpsJobs → JobRunService
 */
@Component
public class DailyImproveScheduler {

    @Autowired
    private OpsJobs opsJobs;

    @Scheduled(cron = "${autofix.daily.cron:0 20 9 * * *}", zone = "Asia/Seoul")
    public void improve() {

        opsJobs.run("DAILY_IMPROVE", "SCHEDULE");

    }
}
