package com.sklabs1037.sklabs1037.stock.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 주식 종목 화면 VO
 * - 사용 테이블: T_STOCK, T_STOCK_PRICE, T_STOCK_DOC
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

    // ===== 시세 =====
    private LocalDate tradeDate;     // 거래일
    private BigDecimal openPrice;    // 시가
    private BigDecimal highPrice;    // 고가
    private BigDecimal lowPrice;     // 저가
    private BigDecimal closePrice;   // 종가
    private BigDecimal prevClose;    // 전일 종가
    private BigDecimal changeAmount; // 전일 대비 (원)
    private BigDecimal changeRate;   // 전일 대비 (%)
    private Long volume;             // 거래량

    // ===== 종목 설명 문서 =====
    private String docTitle;
    private String docContent;

    // ===== 미니 차트 =====
    private List<Map<String, Object>> prices;   // [{ date, close, volume }] 오래된 날짜부터
}