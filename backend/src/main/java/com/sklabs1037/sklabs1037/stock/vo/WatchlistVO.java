package com.sklabs1037.sklabs1037.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 관심 종목 VO
 */
@Data
public class WatchlistVO {

    // ===== 요청 / 조건 =====
    private Long userId;
    private Long stockId;

    // ===== 조회 결과 (종목 + 최신 시세) =====
    private String symbol;
    private String name;
    private LocalDate tradeDate;        // 최신 시세 날짜
    private BigDecimal closePrice;      // 최신 종가
    private BigDecimal changeAmount;    // 전일 대비 (원)
    private BigDecimal changeRate;      // 전일 대비 (%)
}