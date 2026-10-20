package com.sklabs1037.sklabs1037.stock.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시세 수집 스케줄러 (작업 내용은 OpsJobs, 실행 기록·실패 메일은 JobRunService)
 * - 앱 시작 시 (별도 스레드) STOCK_STARTUP: 종목 목록 동기화 → 상장 주식 수 → 시세 없는 종목은 현재가 임시로
 *   → 최근 거래일 시세가 빠진 종목(20:30 수집을 놓친 날) daily-count일치 → 시세 이력이 부족한 종목만 initial-count일치 → 상장 주식 수
 *   (이력은 주요 종목 → 코스피 → 큰 종목 순이라 목록이 위에서부터 차례로 채워짐)
 * - 평일 20:30 STOCK_DAILY: 종목 목록 동기화 → 전 종목(약 3,900개) 최근 daily-count일 수집 (겹치는 날짜는 UPSERT) → 상장 주식 수
 *   → 10분 남짓 걸려서 다른 스케줄 작업이 기다리지 않게 spring.task.scheduling.pool.size를 늘려 둠
 */
@Slf4j
@Component
public class StockCollectScheduler {

    @Autowired
    private OpsJobs opsJobs;

    // 앱 시작 직후
    // - 처음 한 번은 3,000종목 이력을 받느라 10분 넘게 걸려서, 서버 시작을 막지 않게 별도 스레드에서
    // - 시작 작업 중 가장 먼저 (뉴스 최초 수집이 끝날 때까지 기다리지 않게)
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void initialLoad() {

        Thread worker = new Thread(() -> opsJobs.run("STOCK_STARTUP", "STARTUP"), "stock-initial-load");
        worker.setDaemon(true);
        worker.start();

    }

    // 평일 장 마감 후 정기 수집
    @Scheduled(cron = "${stock.collect.cron}", zone = "Asia/Seoul")
    public void dailyCollect() {

        opsJobs.run("STOCK_DAILY", "SCHEDULE");

    }
}
