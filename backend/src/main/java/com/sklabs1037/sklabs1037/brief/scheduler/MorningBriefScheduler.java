package com.sklabs1037.sklabs1037.brief.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 아침 브리핑 메일 스케줄러 (평일 08:00)
 * - 작업 내용: MorningBriefService.sendAll / 실행 기록·실패 메일: OpsJobs → JobRunService
 */
@Component
public class MorningBriefScheduler {

    @Autowired
    private OpsJobs opsJobs;

    @Scheduled(cron = "${brief.cron:0 0 8 * * MON-FRI}", zone = "Asia/Seoul")
    public void send() {

        opsJobs.run("MORNING_BRIEF", "SCHEDULE");

    }
}
