package com.sklabs1037.sklabs1037.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자 피드백 대시보드 VO
 * - 사용 테이블: T_AI_CHAT_LOG, T_USER
 */
@Getter
@Setter
public class AdminFeedbackVO {

    // ===== 조회 조건 =====
    private Long userId;             // 권한 확인할 회원 ID
    private String rating;           // 목록 필터 (UP / DOWN, 없으면 전체)
    private Integer size;            // 목록 개수

    // ===== 권한 =====
    private String role;

    // ===== 요약 =====
    private Long totalCount;         // 전체 질문 수
    private Long upCount;            // 👍
    private Long downCount;          // 👎
    private BigDecimal avgTools;     // 질문당 평균 도구 사용 수

    // ===== 도구별 사용 횟수 =====
    private String toolName;
    private Long useCount;

    // ===== 날짜별 =====
    private LocalDate statDate;
    private Long chatCount;

    // ===== 대화 목록 =====
    private Long chatId;
    private String nickname;
    private String question;
    private String answer;
    private String toolsUsed;
    private LocalDateTime insDay;
}