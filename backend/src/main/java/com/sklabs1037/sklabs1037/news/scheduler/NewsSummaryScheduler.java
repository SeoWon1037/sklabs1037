package com.sklabs1037.sklabs1037.news.scheduler;

import com.sklabs1037.sklabs1037.news.service.INewsSummaryService;
import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
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

    @Autowired
    private OpsJobs opsJobs;

    // 앱 시작 직후 (필요할 때만)
    @EventListener(ApplicationReadyEvent.class)
    public void initialSummary() {

        try {

            if (newsSummaryService.hasRecentSummary(6)) {
                log.info("최근 뉴스 요약이 있어 시작 시 요약을 건너뜀");
                return;
            }

        } catch (Exception e) {

            // 확인을 못 하면 요약하지 않음 (배포마다 Claude 비용이 나가지 않게, 정해진 시각에 다시 돎)
            log.warn("최근 요약 확인 실패 → 시작 시 요약 건너뜀: {}", e.getMessage());
            return;

        }

        opsJobs.run("NEWS_SUMMARY", "STARTUP");

    }

    // 매일 07:30, 16:30
    @Scheduled(cron = "${news.summary.cron:0 30 7,16 * * *}", zone = "Asia/Seoul")
    public void scheduledSummary() {

        opsJobs.run("NEWS_SUMMARY", "SCHEDULE");

    }
}