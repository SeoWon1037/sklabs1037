package com.sklabs1037.sklabs1037.ai.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * AI 챗봇 화면의 요청/응답 VO
 * - 사용 테이블: T_AI_CHAT_LOG (대화 기록 + 피드백)
 */
@Getter
@Setter
public class AiChatVO {

    private Long chatId;             // 대화 기록 ID (피드백 보낼 때 사용)
    private Long userId;             // 질문한 회원 ID
    private String message;          // 사용자 질문
    private String answer;           // AI 답변
    private List<String> toolsUsed;  // AI가 사용한 도구 목록 (화면에 표시)
    private String toolsText;        // 도구 목록을 한 줄로 (DB 저장용)
    private List<java.util.Map<String, Object>> sources;   // 🧾 답변 근거 자료 (뉴스 기사 링크 · 종목 · 문서), 화면 표시용 (DB 저장 안 함)
    private Object history;          // 🧠 앞에 나눈 대화 [{ role: user|assistant, text }] (화면이 보냄 → AiChatRules.history가 다듬음)
    private boolean readOnly;        // 체험 계정: 무언가를 바꾸는 도구(하트 · 약속)를 쓰지 않음
    private boolean loginRequired;   // 로그인해야 답할 수 있는 질문이었는지 (화면에 [로그인] 버튼)
    private String rating;           // 피드백 (UP / DOWN)
    private Integer promptVersion;   // 답한 시스템 프롬프트 버전 (0 = 기본, 🔁 개선 버전별 👎 비율 비교)
}