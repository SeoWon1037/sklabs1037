package com.sklabs1037.sklabs1037.ai.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 🔁 AI 비서 프롬프트 버전 / 개선 재료
 * - 사용 테이블: T_AI_PROMPT, T_AI_CHAT_LOG
 */
@Data
public class AiPromptVO {

    // ===== 프롬프트 버전 =====
    private Long promptId;
    private Integer version;
    private Integer baseVersion;
    private String content;
    private String status;           // CANDIDATE / ACTIVE / RETIRED / REJECTED
    private String analysisJson;
    private String changesJson;
    private String evalJson;
    private BigDecimal scoreOld;
    private BigDecimal scoreNew;
    private String recommendYn;
    private Integer downCount;
    private Long decidedBy;
    private Long decidedAt;          // 밀리초
    private Long insAt;              // 밀리초

    // ===== 대화 기록 (개선 재료) =====
    private Long chatId;
    private Long userId;
    private String question;
    private String answer;
    private String toolsUsed;
    private String rating;
    private Long sinceAt;            // 이 시각(밀리초) 이후 기록만
    private Integer limit;

    // ===== 버전별 통계 =====
    private Integer chatCount;
    private Integer ratedCount;
    private Integer upCount;
    private Integer downRatedCount;
}
