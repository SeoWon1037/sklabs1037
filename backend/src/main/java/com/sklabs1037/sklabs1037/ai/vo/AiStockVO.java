package com.sklabs1037.sklabs1037.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 비서의 도구(tool)가 DB를 조회할 때 쓰는 VO
 * - 사용 테이블: T_STOCK, T_STOCK_PRICE, T_STOCK_DOC, T_NEWS
 */
@Getter
@Setter
public class AiStockVO {

    // ===== 조회 조건 =====
    private String query;            // 종목 검색어 (이름 또는 코드)
    private Integer days;            // 시세 조회 일수 / 수익률 기간 / 뉴스 기간
    private List<String> keywords;   // 문서·뉴스 검색 키워드 (RAG)

    // ===== T_STOCK =====
    private Long stockId;
    private String symbol;
    private String name;
    private String market;

    // ===== T_STOCK_PRICE =====
    private LocalDate tradeDate;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private Long volume;

    // ===== 수익률 순위 (SQL 계산 결과) =====
    private LocalDate startDate;     // 기준 시작일
    private BigDecimal startPrice;   // 시작일 종가
    private LocalDate endDate;       // 최신 거래일
    private BigDecimal endPrice;     // 최신 종가
    private BigDecimal returnRate;   // 수익률 (%)

    // ===== T_STOCK_DOC =====
    private String title;
    private String content;

    // ===== T_NEWS =====
    private String press;            // 언론사
    private String link;             // 기사 주소
    private LocalDateTime pubDate;   // 발행 시각
}