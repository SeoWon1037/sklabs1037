package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.brief.service.MorningBriefService;
import com.sklabs1037.sklabs1037.league.service.AiLeagueService;
import com.sklabs1037.sklabs1037.mock.service.IMockTradeService;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import com.sklabs1037.sklabs1037.news.service.INewsCollectService;
import com.sklabs1037.sklabs1037.news.service.INewsSummaryService;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobBody;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import com.sklabs1037.sklabs1037.stock.service.IStockCollectService;
import com.sklabs1037.sklabs1037.stock.vo.CollectResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 운영 작업 모음 (스케줄러와 관리자 "지금 실행" 버튼이 같은 작업을 쓰게)
 *
 *   opsJobs.run("STOCK_DAILY", "SCHEDULE")   스케줄러
 *   opsJobs.runAsync("STOCK_DAILY")          관리자 화면 (별도 스레드, 바로 응답)
 *
 * - 실행 기록·실패 메일은 JobRunService가 맡음
 */
@Slf4j
@Component
public class OpsJobs {

    // 관리자 화면에서 직접 실행할 수 있는 작업
    public static final List<String> MANUAL_JOBS = List.of(
            "STOCK_STALE", "STOCK_DAILY", "STOCK_STARTUP", "NEWS_COLLECT", "NEWS_SUMMARY", "MOCK_SNAPSHOT", "MORNING_BRIEF", "AI_LEAGUE");

    @Autowired
    private JobRunService jobRunService;

    @Autowired
    private IStockCollectService stockCollectService;

    @Autowired
    private INewsCollectService newsCollectService;

    @Autowired
    private INewsSummaryService newsSummaryService;

    @Autowired
    private IMockTradeService mockTradeService;

    @Autowired
    private MorningBriefService morningBriefService;

    @Autowired
    private AiLeagueService aiLeagueService;

    @Value("${stock.collect.daily-count}")
    private int dailyCount;

    @Value("${stock.collect.initial-count}")
    private int initialCount;

    @Value("${stock.collect.min-history}")
    private int minHistory;

    // 기록하면서 실행 (이 스레드에서) → 이미 돌고 있으면 false
    public boolean run(String job, String trigger) {

        return jobRunService.execute(job, trigger, body(job));

    }

    // 관리자 "지금 실행": 별도 스레드로 → 시작했으면 true
    public boolean runAsync(String job) {

        if (!MANUAL_JOBS.contains(job)) throw new IllegalArgumentException("실행할 수 없는 작업입니다.");
        if (jobRunService.isRunning(job)) return false;

        Thread worker = new Thread(() -> run(job, "MANUAL"), "ops-manual-" + job);
        worker.setDaemon(true);
        worker.start();

        return true;

    }

    public Set<String> runningJobs() {

        return jobRunService.runningJobs();

    }

    // ============================================
    // 작업 본문
    // ============================================
    private JobBody body(String job) {

        return switch (job) {

            // 평일 20:30 전 종목 수집
            case "STOCK_DAILY" -> () -> {
                CollectResult r = stockCollectService.collectAll(dailyCount);
                return JobOutcome.of(r.stocks(), r.failed(), r.summary());
            };

            // 최근 거래일 시세가 빠진 종목만
            case "STOCK_STALE" -> () -> {
                CollectResult r = stockCollectService.collectStale(dailyCount);
                return JobOutcome.of(r.stocks(), r.failed(), r.summary());
            };

            // 서버 시작 시 점검 (단계마다 따로 실패 처리)
            case "STOCK_STARTUP" -> this::startupCheck;

            case "NEWS_COLLECT" -> () -> {
                int saved = newsCollectService.collectAll();
                return JobOutcome.of(saved, 0, "새 뉴스 " + saved + "건");
            };

            case "NEWS_SUMMARY" -> () -> {
                int saved = newsSummaryService.summarizeAll();
                return JobOutcome.of(saved, 0, "요약 " + saved + "건 저장");
            };

            case "MOCK_SNAPSHOT" -> () -> {
                int saved = mockTradeService.snapshotAll(MockMarketHours.now().toLocalDate());
                return JobOutcome.of(saved, 0, saved + "계좌 기록");
            };

            // 오늘 아직 안 받은 신청자에게만 (여러 번 눌러도 한 사람에게 하루 한 통)
            case "MORNING_BRIEF" -> morningBriefService::sendAll;

            // 🤖 AI 투자자들 하루 한 번 매매 (이미 한 AI는 건너뜀)
            case "AI_LEAGUE" -> aiLeagueService::runAll;

            default -> throw new IllegalArgumentException("모르는 작업: " + job);

        };

    }

    // 종목 목록 → 상장 주식 수 → 현재가 임시 → 놓친 날짜 → 이력 보충 → 상장 주식 수
    // - 한 단계가 실패해도 다음 단계는 진행, 실패한 단계는 결과 요약에 남김
    private JobOutcome startupCheck() {

        List<String> notes = new ArrayList<>();
        int total = 0;
        int failed = 0;

        try {
            notes.add("종목 " + stockCollectService.syncStockMaster() + "개 동기화");
        } catch (Exception e) {
            notes.add("종목 동기화 실패: " + e.getMessage());
            failed++;
        }

        try {
            notes.add("상장 주식 수 " + stockCollectService.updateSharesOutstanding() + "개");
        } catch (Exception e) {
            notes.add("상장 주식 수 실패: " + e.getMessage());
        }

        try {
            notes.add("현재가 임시 " + stockCollectService.fillMissingPrices() + "개");
        } catch (Exception e) {
            notes.add("현재가 임시 실패: " + e.getMessage());
        }

        try {
            CollectResult r = stockCollectService.collectStale(dailyCount);
            notes.add("놓친 날짜 " + r.summary());
            total += r.stocks();
            failed += r.failed();
        } catch (Exception e) {
            notes.add("놓친 날짜 실패: " + e.getMessage());
            failed++;
        }

        try {
            CollectResult r = stockCollectService.collectMissingHistory(minHistory, initialCount);
            notes.add("이력 보충 " + r.summary());
            total += r.stocks();
            failed += r.failed();
        } catch (Exception e) {
            notes.add("이력 보충 실패: " + e.getMessage());
            failed++;
        }

        try {
            stockCollectService.updateSharesOutstanding();
        } catch (Exception e) {
            notes.add("상장 주식 수(2차) 실패: " + e.getMessage());
        }

        return JobOutcome.of(total, failed, String.join(" / ", notes));

    }
}
