package com.sklabs1037.sklabs1037.mock.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 모의투자 VO
 * - 사용 테이블: T_MOCK_ACCOUNT, T_MOCK_HOLDING, T_MOCK_TRADE (+ T_STOCK, T_STOCK_PRICE, T_USER)
 */
@Getter
@Setter
public class MockTradeVO {

    // ===== 요청 / 조건 =====
    private Long userId;
    private Integer limit;
    private Integer offset;

    // ===== 종목 =====
    private Long stockId;
    private String symbol;
    private String name;
    private LocalDate tradeDate;        // 최근 종가 날짜
    private BigDecimal closePrice;      // 최근 종가
    private String priceTime;           // 현재가 기준 시각 (토스 timestamp / 종가면 날짜)

    // ===== 계좌 =====
    private BigDecimal cash;            // 현금
    private BigDecimal initCash;        // 시작 자금
    private Integer resetCount;         // 초기화 횟수

    // ===== 보유 종목 / 주문 =====
    private Integer quantity;           // 수량
    private BigDecimal avgPrice;        // 평균 단가

    // ===== 거래 =====
    private Long tradeId;
    private String side;                // BUY / SELL
    private BigDecimal price;           // 체결 가격
    private BigDecimal amount;          // 체결 금액
    private BigDecimal profit;          // 매도 실현손익
    private String priceSource;         // LIVE / CLOSE
    private Long insAt;                 // 거래 시각 (밀리초)

    // ===== 랭킹 =====
    private String nickname;
    private Integer tradeCount;
}
