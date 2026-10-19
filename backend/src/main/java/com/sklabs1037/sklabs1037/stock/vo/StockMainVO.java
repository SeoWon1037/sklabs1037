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
    private Integer limit;           // 목록·검색 개수
    private String query;            // 검색어
    private List<Long> stockIds;     // 미니 차트를 그릴 종목들

    // ===== 종목 =====
    private Long stockId;
    private String symbol;
    private String name;
    private String market;
    private String securityType;     // STOCK / ETF
    private Boolean featured;        // 주요 종목

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

    // ===== 시가총액 =====
    private Long sharesOutstanding;  // 상장 주식 수
    private BigDecimal marketCap;    // 시가총액 (상장 주식 수 × 최신 종가, 주식 수가 없으면 null)

    // ===== 종목 설명 문서 =====
    private String docTitle;
    private String docContent;

    // ===== 미니 차트 =====
    private List<Map<String, Object>> prices;   // [{ date, close, volume }] 오래된 날짜부터
}