package com.sklabs1037.sklabs1037.qa.vo;

import lombok.Data;

/**
 * 🤖 AI QA 봇 점검 기록 (T_QA_RUN)
 */
@Data
public class QaRunVO {

    private Long runId;
    private String source;          // SCHEDULE / DEPLOY / MANUAL
    private String commitSha;
    private String status;          // ANALYZING / PASS / WARN / FAIL
    private String summary;
    private String findingsJson;
    private String checksJson;
    private String shotsJson;
    private Long insAt;             // 밀리초
    private Integer keep;           // 남길 개수 (오래된 기록 지우기)
}
