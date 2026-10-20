package com.sklabs1037.sklabs1037.duel.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ⚔️ 친구 대결 정산 (매일 00:30, 전날 끝난 대결 + 만료된 초대)
 * 🤖 AI 주간 코치 (월요일 08:10)
 */
@Component
public class DuelScheduler {

    @Autowired
    private OpsJobs opsJobs;

    @Scheduled(cron = "${duel.cron:0 30 0 * * *}", zone = "Asia/Seoul")
    public void settle() {

        opsJobs.run("DUEL_SETTLE", "SCHEDULE");

    }

    @Scheduled(cron = "${coach.cron:0 10 8 * * MON}", zone = "Asia/Seoul")
    public void weeklyCoach() {

        opsJobs.run("WEEKLY_COACH", "SCHEDULE");

    }
}
