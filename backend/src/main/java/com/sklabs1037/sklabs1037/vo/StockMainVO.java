package com.sklabs1037.sklabs1037.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 메인 화면(종목 대시보드) VO
 * - 사용 테이블: T_STOCK, T_STOCK_PRICE
 */
@Getter
@Setter
public class StockMainVO {

    // ===== 조회 조건 =====
    private Integer days;            // 차트용 최근 거래일 수

    // ===== 종목 =====
    private Long stockId;
    private String symbol;
    private String name;
    private String market;

    // ===== 최신 시세 =====
    private LocalDate tradeDate;     // 최신 거래일
    private BigDecimal closePrice;   // 최신 종가
    private BigDecimal prevClose;    // 전일 종가
    private BigDecimal changeAmount; // 전일 대비 (원)
    private BigDecimal changeRate;   // 전일 대비 (%)
    private Long volume;             // 거래량

    // ===== 차트 =====
    private List<Map<String, Object>> prices;   // [{ date, close, volume }] 오래된 날짜부터
}