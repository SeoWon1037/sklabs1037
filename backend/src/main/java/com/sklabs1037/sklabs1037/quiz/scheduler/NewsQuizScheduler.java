package com.sklabs1037.sklabs1037.quiz.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 📰 오늘의 뉴스 퀴즈 출제 (매일 07:40, 07:30 AI 뉴스 요약 직후)
 */
@Component
public class NewsQuizScheduler {

    @Autowired
    private OpsJobs opsJobs;

    @Scheduled(cron = "${quiz.cron:0 40 7 * * *}", zone = "Asia/Seoul")
    public void generate() {

        opsJobs.run("NEWS_QUIZ", "SCHEDULE");

    }
}
