package com.sklabs1037.sklabs1037.league.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 투자 리그 스케줄러 (평일 09:10, 장 시작 10분 뒤 → 시초가 출렁임을 조금 피해서)
 * - 작업 내용: AiLeagueService.runAll / 실행 기록·실패 메일·로컬 PC에서 끄기: OpsJobs → JobRunService
 */
@Component
public class AiLeagueScheduler {

    @Autowired
    private OpsJobs opsJobs;

    @Scheduled(cron = "${league.cron:0 10 9 * * MON-FRI}", zone = "Asia/Seoul")
    public void trade() {

        opsJobs.run("AI_LEAGUE", "SCHEDULE");

    }
}
