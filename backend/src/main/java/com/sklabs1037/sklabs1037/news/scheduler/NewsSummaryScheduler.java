package com.sklabs1037.sklabs1037.scheduler;

import com.sklabs1037.sklabs1037.service.INewsSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 뉴스 요약 스케줄러
 * - 매일 07:30, 16:30 (장 시작 전 / 장 마감 후)
 * - 앱이 켜질 때 최근 6시간 안에 만든 요약이 없으면 1번 바로 요약
 *   (배포할 때마다 다시 요약하면 API 비용이 들어서, 최근 요약이 있으면 건너뜀)
 */
@Slf4j
@Component
public class NewsSummaryScheduler {

    @Autowired
    private INewsSummaryService newsSummaryService;

    // 앱 시작 직후 (필요할 때만)
    @EventListener(ApplicationReadyEvent.class)
    public void initialSummary() {

        try {

            if (newsSummaryService.hasRecentSummary(6)) {
                log.info("최근 뉴스 요약이 있어 시작 시 요약을 건너뜀");
                return;
            }

            newsSummaryService.summarizeAll();

        } catch (Exception e) {

            log.error("뉴스 최초 요약 실패", e);

        }

    }

    // 매일 07:30, 16:30
    @Scheduled(cron = "${news.summary.cron:0 30 7,16 * * *}", zone = "Asia/Seoul")
    public void scheduledSummary() {

        newsSummaryService.summarizeAll();

    }
}