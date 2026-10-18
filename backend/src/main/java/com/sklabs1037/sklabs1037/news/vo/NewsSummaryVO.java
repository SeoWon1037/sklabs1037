package com.sklabs1037.sklabs1037.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 뉴스 요약 VO
 * - 요약 대상 조회, 뉴스 제목 조회, 요약 저장/조회에 같이 사용
 */
@Data
public class NewsSummaryVO {

    // ===== 요약 (T_NEWS_SUMMARY) =====
    private String targetKey;          // 'MARKET' 또는 'STOCK_1'
    private String category;           // MARKET / STOCK
    private Long stockId;
    private String stockName;
    private String summary;            // 요약 본문 (줄바꿈으로 구분된 3줄)
    private Integer newsCount;         // 요약에 쓴 뉴스 건수
    private LocalDateTime summaryAt;   // 요약한 시각 (한국 시간)

    // ===== 뉴스 제목 조회용 =====
    private String title;
    private String press;

    // ===== 조회 조건 =====
    private LocalDateTime fromAt;      // 이 시각 이후 뉴스만
    private Integer limit;             // 최대 건수
    private List<String> keywords;     // 제목에 이 중 하나라도 들어간 기사만 (종목 요약일 때)
}