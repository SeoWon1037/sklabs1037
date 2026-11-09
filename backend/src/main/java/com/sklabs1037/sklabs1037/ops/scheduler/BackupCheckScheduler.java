package com.sklabs1037.sklabs1037.ops.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 💾 DB 백업 확인 스케줄러 (매일 06:00, 04:30 백업이 끝났을 시간)
 * - 작업 내용: BackupService.check / 기록·실패 메일: OpsJobs → JobRunService
 */
@Component
public class BackupCheckScheduler {

    @Autowired
    private OpsJobs opsJobs;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void check() {

        opsJobs.run("BACKUP_CHECK", "SCHEDULE");

    }
}
