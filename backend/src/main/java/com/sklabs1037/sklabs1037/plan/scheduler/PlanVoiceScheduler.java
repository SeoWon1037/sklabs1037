package com.sklabs1037.sklabs1037.plan.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 🗓️ 약속 알림 · 🔊 음성 브리핑 스케줄러 (한국 시간)
 * - 07:00 매일      VOICE_MORNING  아침 브리핑 (날씨 + 오늘 약속)
 * - 09:00 매일      PLAN_REMIND    약속 알림 (하루 전 · 당일)
 * - 09:00 매일      COUPLE_BIRTHDAY 커플 생일 알림 (7일 전 · 하루 전 · 당일)
 * - 15:40 평일      VOICE_CLOSE    장 마감 브리핑 (관심 종목, 휴장일은 건너뜀)
 * - 작업 내용: PlanService · VoiceBriefService / 기록 · 실패 메일: OpsJobs → JobRunService
 */
@Component
public class PlanVoiceScheduler {

    @Autowired
    private OpsJobs opsJobs;

    @Scheduled(cron = "${voice.morning.cron:0 0 7 * * *}", zone = "Asia/Seoul")
    public void morning() {

        opsJobs.run("VOICE_MORNING", "SCHEDULE");

    }

    @Scheduled(cron = "${plan.remind.cron:0 0 9 * * *}", zone = "Asia/Seoul")
    public void remind() {

        opsJobs.run("PLAN_REMIND", "SCHEDULE");

    }

    @Scheduled(cron = "${couple.birthday.cron:0 0 9 * * *}", zone = "Asia/Seoul")
    public void birthday() {

        opsJobs.run("COUPLE_BIRTHDAY", "SCHEDULE");

    }

    @Scheduled(cron = "${voice.close.cron:0 40 15 * * MON-FRI}", zone = "Asia/Seoul")
    public void close() {

        opsJobs.run("VOICE_CLOSE", "SCHEDULE");

    }
}
