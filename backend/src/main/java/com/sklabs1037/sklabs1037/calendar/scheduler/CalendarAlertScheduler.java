package com.sklabs1037.sklabs1037.calendar.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 🔔 증시 일정 알림 스케줄러 (매일 19:00, 내일 일정을 전날 저녁에)
 * - 작업 내용: CalendarAlertService.sendAll / 기록·실패 메일: OpsJobs → JobRunService
 */
@Component
public class CalendarAlertScheduler {

    @Autowired
    private OpsJobs opsJobs;

    @Scheduled(cron = "${calendar.alert.cron:0 0 19 * * *}", zone = "Asia/Seoul")
    public void send() {

        opsJobs.run("CALENDAR_ALERT", "SCHEDULE");

    }
}
