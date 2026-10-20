package com.sklabs1037.sklabs1037.ai.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 🔁 AI 비서 프롬프트 개선 스케줄러 (매일 04:10, 사람이 거의 안 쓰는 시간)
 * - 새 👎가 3개 이상일 때만 실제로 AI를 부름 (적으면 바로 끝)
 * - 작업 내용: PromptImproveService.run / 기록·실패 메일·로컬 PC에서 끄기: OpsJobs → JobRunService
 */
@Component
public class PromptImproveScheduler {

    @Autowired
    private OpsJobs opsJobs;

    @Scheduled(cron = "${ai.improve.cron:0 10 4 * * *}", zone = "Asia/Seoul")
    public void improve() {

        opsJobs.run("AI_IMPROVE", "SCHEDULE");

    }
}
