package com.sklabs1037.sklabs1037.league.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI 투자 리그 VO
 * - 사용 테이블: T_AI_TRADER, T_AI_TRADE_LOG, T_USER, T_STOCK, T_STOCK_PRICE, T_NEWS
 */
@Data
public class AiLeagueVO {

    // ===== AI 투자자 =====
    private Long userId;
    private String code;               // NEWS / MOMENTUM / STEADY
    private String nickname;

    // ===== 매매 일지 =====
    private Long logId;
    private LocalDate tradeDate;
    private String status;             // DONE / HOLD / FAIL
    private String marketView;
    private String diary;
    private String ordersJson;
    private BigDecimal totalAsset;     // 매매 전 총자산
    private String message;
    private Long insAt;                // 밀리초
    private Integer limit;

    // ===== 후보 종목 =====
    private Long stockId;
    private String symbol;
    private String name;
    private String market;
    private Long capEok;               // 시가총액 (억 원)
    private BigDecimal closePrice;
    private BigDecimal ret5;           // 5거래일 수익률 (%)
    private BigDecimal ret20;          // 20거래일 수익률 (%)
    private BigDecimal volAnnual;      // 연환산 변동성 (%)

    // ===== 뉴스 =====
    private String title;
    private String pubAt;              // "10-20 08:31"
}
