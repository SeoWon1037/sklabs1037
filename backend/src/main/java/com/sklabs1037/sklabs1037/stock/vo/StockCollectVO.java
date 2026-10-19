package com.sklabs1037.sklabs1037.stock.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 시세 수집(데이터 파이프라인)에서 쓰는 값을 담는 VO
 * - 사용 테이블: T_STOCK, T_STOCK_PRICE
 */
@Getter
@Setter
public class StockCollectVO {

    // ===== T_STOCK =====
    private Long stockId;            // 종목 번호
    private String symbol;           // 종목코드 (005930)
    private String name;             // 종목명
    private Long sharesOutstanding;  // 상장 주식 수 (시가총액 계산용)

    // ===== T_STOCK_PRICE =====
    private LocalDate tradeDate;     // 거래일
    private BigDecimal openPrice;    // 시가
    private BigDecimal highPrice;    // 고가
    private BigDecimal lowPrice;     // 저가
    private BigDecimal closePrice;   // 종가
    private Long volume;             // 거래량
}