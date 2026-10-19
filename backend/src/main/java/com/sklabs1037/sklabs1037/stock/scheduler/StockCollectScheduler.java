package com.sklabs1037.sklabs1037.stock.scheduler;

import com.sklabs1037.sklabs1037.stock.service.IStockCollectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시세 수집 스케줄러
 * - 앱 시작 시: 시세 이력이 부족한 종목(처음 실행, 새로 추가한 종목)만 initial-count일치 채움 + 상장 주식 수 갱신
 * - 평일 20:30: 전 종목 최근 daily-count일 수집 (겹치는 날짜는 UPSERT) + 상장 주식 수 갱신
 */
@Slf4j
@Component
public class StockCollectScheduler {

    @Autowired
    private IStockCollectService stockCollectService;

    @Value("${stock.collect.daily-count}")
    private int dailyCount;

    @Value("${stock.collect.initial-count}")
    private int initialCount;

    @Value("${stock.collect.min-history}")
    private int minHistory;

    // 앱 시작 직후: 이력 부족한 종목 보충
    @EventListener(ApplicationReadyEvent.class)
    public void initialLoad() {

        try {

            stockCollectService.collectMissingHistory(minHistory, initialCount);

        } catch (Exception e) {

            log.error("시세 이력 보충 실패", e);

        }

        // 상장 주식 수 (시가총액 TOP 3용)
        try {

            stockCollectService.updateSharesOutstanding();

        } catch (Exception e) {

            log.warn("상장 주식 수 갱신 실패: {}", e.getMessage());

        }

    }

    // 평일 장 마감 후 정기 수집
    @Scheduled(cron = "${stock.collect.cron}", zone = "Asia/Seoul")
    public void dailyCollect() {

        stockCollectService.collectAll(dailyCount);

    }
}