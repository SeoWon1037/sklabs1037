package com.sklabs1037.sklabs1037.mock.scheduler;

import com.sklabs1037.sklabs1037.mock.service.IMockTradeService;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import com.sklabs1037.sklabs1037.ops.service.JobRunService;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 모의투자 지정가 주문 스케줄러
 * - 정규장(평일 09:00~15:30) 1분마다: 지금 가격으로 조건이 맞는 주문 체결
 * - 평일 15:35: 오늘이 마지막인 미체결 주문 만료 (묶어둔 돈 돌려줌)
 * - 평일 15:40: 거래한 적 있는 계좌의 오늘 총자산 기록 (수익률 그래프, 이번 주 랭킹)
 * - 서버가 켜질 때: 서버가 꺼져 있어서 만료되지 못한 지난 주문 정리 (+ 장 마감 뒤면 오늘 기록)
 *
 * 주문 1건 = 트랜잭션 1개 → 한 건이 실패해도 나머지 주문은 계속 처리
 * 실행 기록·실패 메일: 만료·자산 기록은 매번 기록, 1분마다 도는 체결 확인은 실패했을 때만 (JobRunService)
 */
@Slf4j
@Component
public class MockOrderScheduler {

    @Autowired
    private IMockTradeService mockTradeService;

    @Autowired
    private JobRunService jobRunService;

    @Autowired
    private OpsJobs opsJobs;

    // 정규장 1분마다 체결 확인 (9시~15시대, 15:30 이후는 안에서 걸러냄)
    @Scheduled(cron = "0 * 9-15 * * MON-FRI", zone = "Asia/Seoul")
    public void matchOrders() {

        if (!MockMarketHours.isOpen(MockMarketHours.now()) || !jobRunService.backgroundEnabled()) return;

        try {

            List<Long> ids = mockTradeService.findFillableOrderIds();
            int filled = 0;
            Exception firstError = null;

            for (Long id : ids) {

                try {

                    if (mockTradeService.fillOrder(id)) filled++;

                } catch (Exception e) {

                    log.error("지정가 주문 체결 실패: {}", id, e);
                    if (firstError == null) firstError = e;

                }

            }

            if (filled > 0) log.info("지정가 주문 체결: {}건", filled);
            if (firstError != null) jobRunService.recordFailure("MOCK_MATCH", "SCHEDULE", firstError);

        } catch (Exception e) {

            log.error("지정가 주문 확인 실패", e);
            jobRunService.recordFailure("MOCK_MATCH", "SCHEDULE", e);

        }

    }

    // 장 마감 후 미체결 주문 만료
    @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Asia/Seoul")
    public void expireOrders() {

        expireUpTo(MockMarketHours.now().toLocalDate(), "SCHEDULE");

    }

    // 장 마감 후 일별 자산 기록 (만료 처리 다음, 종가가 반영된 뒤)
    @Scheduled(cron = "0 40 15 * * MON-FRI", zone = "Asia/Seoul")
    public void snapshot() {

        opsJobs.run("MOCK_SNAPSHOT", "SCHEDULE");

    }

    // 서버 시작 직후: 지난 거래일 주문 정리 (오늘 주문은 그대로)
    @EventListener(ApplicationReadyEvent.class)
    public void expireOnStart() {

        ZonedDateTime now = MockMarketHours.now();
        LocalDate upTo = now.toLocalDate().minusDays(1);

        // 오늘 장 마감(15:35)이 지났으면 오늘 주문까지
        if (now.toLocalTime().isAfter(java.time.LocalTime.of(15, 35))) upTo = now.toLocalDate();

        expireUpTo(upTo, "STARTUP");

        // 평일 장 마감(15:40) 뒤에 서버가 켜졌으면 오늘 기록도 (그 시각에 꺼져 있었을 수 있어서, 같은 날은 덮어씀)
        java.time.DayOfWeek d = now.getDayOfWeek();
        boolean weekday = d != java.time.DayOfWeek.SATURDAY && d != java.time.DayOfWeek.SUNDAY;

        if (weekday && now.toLocalTime().isAfter(java.time.LocalTime.of(15, 40))) opsJobs.run("MOCK_SNAPSHOT", "STARTUP");

    }

    private void expireUpTo(LocalDate upTo, String trigger) {

        jobRunService.execute("MOCK_EXPIRE", trigger, () -> {

            List<Long> ids = mockTradeService.findExpiredOrderIds(upTo);
            int expired = 0;
            int failed = 0;

            for (Long id : ids) {

                try {

                    if (mockTradeService.expireOrder(id)) expired++;

                } catch (Exception e) {

                    log.error("지정가 주문 만료 실패: {}", id, e);
                    failed++;

                }

            }

            if (expired > 0) log.info("미체결 지정가 주문 만료: {}건", expired);

            return JobOutcome.of(ids.size(), failed, upTo + "까지 " + expired + "건 만료");

        });

    }
}
