package com.sklabs1037.sklabs1037.scheduler;

import com.sklabs1037.sklabs1037.news.service.INewsCollectService;
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
    private INewsCollectService newsCollectService;

    // 앱 시작 직후 1번
    @EventListener(ApplicationReadyEvent.class)
    public void initialLoad() {

        try {

            newsCollectService.collectAll();

        } catch (Exception e) {

            log.error("뉴스 최초 수집 실패", e);

        }

    }

    // 30분마다
    @Scheduled(cron = "${news.collect.cron}", zone = "Asia/Seoul")
    public void scheduledCollect() {

        newsCollectService.collectAll();

    }
}