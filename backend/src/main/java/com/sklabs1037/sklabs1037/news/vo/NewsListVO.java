package com.sklabs1037.sklabs1037.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 메인 화면(뉴스 목록) VO
 * - 사용 테이블: T_NEWS, T_STOCK
 */
@Getter
@Setter
public class NewsListVO {

    // ===== 조회 조건 =====
    private String category;         // MARKET / STOCK (없으면 전체)
    private Long stockId;            // 종목 ID (없으면 전체)
    private Integer size;            // 가져올 개수

    // ===== 결과 =====
    private Long newsId;
    private String stockName;        // 종목 뉴스면 종목명
    private String title;
    private String link;
    private String press;            // 언론사
    private LocalDateTime pubDate;   // 발행 시각
}