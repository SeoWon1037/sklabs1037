package com.sklabs1037.sklabs1037.predict.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 🎯 오를까 내릴까 VO
 * - 사용 테이블: T_PREDICT_ITEM, T_PREDICT_VOTE, T_STOCK, T_STOCK_PRICE, T_USER
 */
@Data
public class PredictVO {

    // ===== 문제 (종목 하나) =====
    private Long itemId;
    private LocalDate tradeDate;
    private Long stockId;
    private String symbol;
    private String name;
    private Integer seq;
    private String botPick;           // 모멘텀 봇 예측
    private LocalDate baseDate;
    private BigDecimal baseClose;
    private BigDecimal resultClose;
    private String result;            // UP / DOWN / FLAT / VOID
    private BigDecimal lastClose;     // 화면용 최근 종가
    private BigDecimal ret5;          // 최근 5거래일 수익률 (%)

    // ===== 투표 =====
    private Long userId;
    private String pick;              // UP / DOWN
    private Boolean correct;
    private Integer upCount;
    private Integer downCount;

    // ===== 랭킹 =====
    private String nickname;
    private Integer graded;           // 채점된 투표 수
    private Integer hits;             // 맞힌 수
    private LocalDate fromDate;
    private Integer minGraded;
    private Integer limit;
}
