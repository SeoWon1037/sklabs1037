package com.sklabs1037.sklabs1037.news.controller;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 뉴스 수집 스케줄러
 * - 앱이 켜지면 1번 바로 수집 (중복 기사는 DB가 걸러줌)
 * - 이후 30분마다 수집
 */
@Slf4j
@Component
public class NewsCollectScheduler {

    @Autowired
    private OpsJobs opsJobs;

    // 앱 시작 직후 1번
    @EventListener(ApplicationReadyEvent.class)
    public void initialLoad() {

        opsJobs.run("NEWS_COLLECT", "STARTUP");   // 실패 기록·메일은 JobRunService

    }

    // 30분마다
    @Scheduled(cron = "${news.collect.cron}", zone = "Asia/Seoul")
    public void scheduledCollect() {

        opsJobs.run("NEWS_COLLECT", "SCHEDULE");

    }
}