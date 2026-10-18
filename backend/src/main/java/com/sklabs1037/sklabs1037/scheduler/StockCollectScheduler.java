package com.sklabs1037.sklabs1037.scheduler;

import com.sklabs1037.sklabs1037.service.IStockCollectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시세 수집 스케줄러 (데이터 파이프라인의 "언제 실행할지" 담당)
 * - 서버가 켜질 때: 시세가 비어 있으면 최근 N일 초기 적재
 * - 평일 20:30   : 최근 5일 일일 적재 (겹치는 날짜는 UPSERT로 덮어씀)
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

    // 서버 시작 완료 후 1번 실행
    @EventListener(ApplicationReadyEvent.class)
    public void initialLoad() {

        try {

            if (stockCollectService.isEmpty()) {

                log.info("시세 데이터가 없어 초기 적재를 시작합니다. (최근 {}일)", initialCount);
                stockCollectService.collectAll(initialCount);

            }

        } catch (Exception e) {

            // 초기 적재가 실패해도 서버는 정상적으로 켜져 있게
            log.error("초기 적재 실패", e);

        }

    }

    // 평일 20:30 (한국 시간) 자동 실행
    @Scheduled(cron = "${stock.collect.cron}", zone = "Asia/Seoul")
    public void dailyCollect() {

        log.info("일일 시세 수집 시작");
        stockCollectService.collectAll(dailyCount);

    }
}