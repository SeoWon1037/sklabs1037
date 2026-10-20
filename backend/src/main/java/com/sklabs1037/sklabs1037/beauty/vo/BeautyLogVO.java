package com.sklabs1037.sklabs1037.beauty.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 뷰티 랩 기록 VO (성분 분석 / 기획 브리프)
 * - 사용 테이블: T_BEAUTY_LOG
 */
@Getter
@Setter
public class BeautyLogVO {

    private Long logId;
    private Long userId;
    private String kind;            // ANALYZE / BRIEF
    private String inputType;       // IMAGE / TEXT
    private String title;           // 제품명 또는 요청 앞부분
    private Integer itemCount;      // 읽은 성분 수
    private String resultJson;      // 결과 전체 (다시 보기)
    private OffsetDateTime insDay;

    // 조회 조건
    private Integer limit;
}
