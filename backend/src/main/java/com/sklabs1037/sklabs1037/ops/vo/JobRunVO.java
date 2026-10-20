package com.sklabs1037.sklabs1037.ops.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 작업 실행 기록 VO
 * - 사용 테이블: T_JOB_RUN (+ 데이터 상태 확인용 T_STOCK, T_NEWS, T_NEWS_SUMMARY)
 */
@Getter
@Setter
public class JobRunVO {

    // ===== 조회 조건 =====
    private Integer limit;
    private OffsetDateTime since;          // 이 시각 이후
    private OffsetDateTime bootAt;         // 서버가 켜진 시각 (그 전부터 RUNNING이면 중단된 작업)
    private Integer keepDays;              // 기록 보관 일수

    // ===== 실행 기록 =====
    private Long runId;
    private String jobName;
    private String triggerType;            // SCHEDULE / STARTUP / MANUAL / WATCHDOG
    private String status;                 // RUNNING / SUCCESS / PARTIAL / FAIL / ABORTED
    private Integer totalCount;
    private Integer failCount;
    private String message;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private Long durationSec;              // 걸린 시간 (초)

    // ===== 데이터 상태 =====
    private Integer stockCount;            // 사용 중인 종목
    private Integer pricedCount;           // 종가가 있는 종목
    private Integer staleCount;            // 최신 거래일 시세가 없는 종목
    private LocalDate lastTradeDate;       // 가장 최근 거래일
    private LocalDateTime lastNewsAt;       // 가장 최근 뉴스 기사 시각
    private Integer newsToday;             // 오늘 나온 뉴스 기사 수
    private LocalDateTime lastSummaryAt;   // 마지막 AI 뉴스 요약 시각
    private Integer activeAlerts;          // 켜진 가격 알림
    private Integer pendingOrders;         // 미체결 지정가 주문

    // ===== 관리자 =====
    private String email;
}
