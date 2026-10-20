package com.sklabs1037.sklabs1037.stock.scheduler;

import com.sklabs1037.sklabs1037.stock.service.IStockCollectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시세 수집 스케줄러
 * - 앱 시작 시 (별도 스레드): 종목 목록 동기화 → 상장 주식 수 → 시세 없는 종목은 현재가 임시로 → 최근 거래일 시세가 빠진 종목(20:30 수집을 놓친 날) daily-count일치
 *   → 시세 이력이 부족한 종목만 initial-count일치 채움 → 상장 주식 수
 *   (이력은 주요 종목 → 코스피 → 큰 종목 순이라 목록이 위에서부터 차례로 채워짐)
 * - 평일 20:30: 종목 목록 동기화 → 전 종목(약 3,000개) 최근 daily-count일 수집 (겹치는 날짜는 UPSERT) → 상장 주식 수
 *   → 10분 남짓 걸려서 다른 스케줄 작업이 기다리지 않게 spring.task.scheduling.pool.size를 늘려 둠
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

    // 앱 시작 직후: 종목 목록 동기화 → 이력 부족한 종목 보충 → 상장 주식 수
    // - 처음 한 번은 3,000종목 이력을 받느라 10분 넘게 걸려서, 서버 시작을 막지 않게 별도 스레드에서
    // - 시작 작업 중 가장 먼저 (뉴스 최초 수집이 끝날 때까지 기다리지 않게)
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void initialLoad() {

        Thread worker = new Thread(() -> {

            try {

                stockCollectService.syncStockMaster();

            } catch (Exception e) {

                log.warn("종목 목록 동기화 실패: {}", e.getMessage());

            }

            // 상장 주식 수 먼저 (호출 15번 정도) → 이력이 채워지는 종목부터 바로 시가총액 순 목록에 나옴
            try {

                stockCollectService.updateSharesOutstanding();

            } catch (Exception e) {

                log.warn("상장 주식 수 갱신 실패: {}", e.getMessage());

            }

            // 시세가 아직 없는 종목은 현재가를 임시로 (200개씩, 1분 안에) → 목록에 바로 다 나옴
            try {

                stockCollectService.fillMissingPrices();

            } catch (Exception e) {

                log.warn("현재가 임시 채우기 실패: {}", e.getMessage());

            }

            // 정기 수집(20:30)을 놓친 종목 따라잡기 (서버가 꺼져 있었거나 토스 오류였던 날)
            // - 이력 보충보다 먼저: 주요 종목·큰 종목부터 최신 시세가 바로 맞춰지게
            try {

                stockCollectService.collectStale(dailyCount);

            } catch (Exception e) {

                log.error("놓친 시세 수집 실패", e);

            }

            try {

                stockCollectService.collectMissingHistory(minHistory, initialCount);

            } catch (Exception e) {

                log.error("시세 이력 보충 실패", e);

            }

            // 상장 주식 수 한 번 더 (새로 받은 종목까지)
            try {

                stockCollectService.updateSharesOutstanding();

            } catch (Exception e) {

                log.warn("상장 주식 수 갱신 실패: {}", e.getMessage());

            }

        }, "stock-initial-load");

        worker.setDaemon(true);
        worker.start();

    }

    // 평일 장 마감 후 정기 수집
    @Scheduled(cron = "${stock.collect.cron}", zone = "Asia/Seoul")
    public void dailyCollect() {

        stockCollectService.collectAll(dailyCount);

    }
}