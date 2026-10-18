package com.sklabs1037.sklabs1037.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 뉴스 수집 VO
 * - 사용 테이블: T_NEWS, T_STOCK
 */
@Getter
@Setter
public class NewsCollectVO {

    // ===== 수집 대상 종목 (T_STOCK) =====
    private Long stockId;
    private String name;

    // ===== T_NEWS =====
    private String category;         // MARKET / STOCK
    private String title;
    private String description;
    private String link;
    private String press;            // 언론사
    private LocalDateTime pubDate;   // 발행 시각 (한국 시간)
}