package com.sklabs1037.sklabs1037.mock.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 모의투자 VO
 * - 사용 테이블: T_MOCK_ACCOUNT, T_MOCK_HOLDING, T_MOCK_TRADE, T_MOCK_ORDER (+ T_STOCK, T_STOCK_PRICE, T_USER)
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
    private BigDecimal pendingCash;     // 매수 주문에 묶여 있는 돈

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
    private Long insAt;                 // 거래·주문 시각 (밀리초)

    // ===== 지정가 주문 =====
    private Long orderId;
    private String orderType;           // MARKET / LIMIT (요청)
    private BigDecimal limitPrice;      // 지정가
    private BigDecimal reservedAmount;  // 매수 주문에 묶어둔 돈
    private String status;              // PENDING / FILLED / CANCELED / EXPIRED
    private LocalDate orderDate;        // 이 주문이 유효한 거래일

    // ===== 랭킹 / 일별 기록 =====
    private String nickname;
    private Integer tradeCount;
    private String aiCode;              // AI 투자 리그 참가자면 NEWS / MOMENTUM / STEADY (사람은 null)
    private LocalDate weekStart;        // 이번 주 월요일 (조건)
    private BigDecimal weekBase;        // 이번 주 수익률 기준 총자산
    private LocalDate snapDate;         // 기록한 거래일
    private BigDecimal totalAsset;      // 총자산
    private BigDecimal returnRate;      // 시작 자금 대비 수익률 (%)
}
