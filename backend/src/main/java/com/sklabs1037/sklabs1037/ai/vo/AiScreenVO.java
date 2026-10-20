package com.sklabs1037.sklabs1037.ai.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI 종목 스크리너(screen_stocks 도구) VO
 * - AI가 SQL을 쓰는 게 아니라, 정해진 조건 칸만 채우고 SQL은 서버(AIChatMapper.selectScreen)가 만듦
 *   → 아무 SQL이나 실행될 위험 없음, 숫자는 전부 범위를 잘라서 넣음
 * - 사용 테이블: T_STOCK, T_STOCK_PRICE
 */
@Getter
@Setter
public class AiScreenVO {

    // ===== 조건 (null이면 그 조건은 안 씀) =====
    private String market;            // STOCK(코스피+코스닥 주식) / KOSPI / KOSDAQ / ETF / ALL(주식+ETF)
    private Integer days;             // 수익률·거래량·고가 기준 기간 (거래일 1~60)
    private BigDecimal minReturn;     // 기간 수익률 % 이상
    private BigDecimal maxReturn;     // 기간 수익률 % 이하
    private BigDecimal minChange;     // 최근 하루 등락률 % 이상
    private BigDecimal maxChange;     // 최근 하루 등락률 % 이하
    private BigDecimal minCapEok;     // 시가총액 (억 원) 이상
    private BigDecimal maxCapEok;     // 시가총액 (억 원) 이하
    private BigDecimal minPrice;      // 종가 이상
    private BigDecimal maxPrice;      // 종가 이하
    private Integer minUpStreak;      // 연속 상승 일수 이상
    private Integer minDownStreak;    // 연속 하락 일수 이상
    private BigDecimal minVolumeRatio;  // 최근 거래량 ÷ 기간 평균 거래량 이상 (거래량 급증)
    private BigDecimal maxFromHigh;   // 기간 최고 종가 대비 % 이내 (0에 가까울수록 최고가 근처, 예: 3 → 3% 이내)
    private String sort;              // 정렬 (정해진 값만)
    private Integer limit;            // 최대 30

    // ===== 결과 =====
    private String symbol;
    private String name;
    private String stockMarket;       // KOSPI / KOSDAQ
    private String securityType;      // STOCK / ETF
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal closePrice;
    private BigDecimal returnRate;    // 기간 수익률 %
    private BigDecimal changeRate;    // 최근 하루 등락률 %
    private Long marketCapEok;        // 시가총액 (억 원)
    private Integer upStreak;         // 연속 상승 일수
    private Integer downStreak;       // 연속 하락 일수
    private BigDecimal volumeRatio;   // 거래량 배수
    private BigDecimal fromHigh;      // 기간 최고 종가 대비 % (0 이하)
    private Integer matchCount;       // 조건에 맞는 전체 종목 수
}
