package com.sklabs1037.sklabs1037.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * AI 주식 비서 화면의 요청/응답 VO
 */
@Getter
@Setter
public class AiChatVO {

    private String message;          // 사용자 질문
    private String answer;           // AI 답변
    private List<String> toolsUsed;  // AI가 사용한 도구 목록 (화면에 표시)
}