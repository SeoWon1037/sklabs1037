package com.sklabs1037.sklabs1037.ai.service;

import com.sklabs1037.sklabs1037.ai.vo.AiChatVO;

/**
 * AI 주식 비서 Service
 */
public interface IAiChatService {

    // 질문 → (필요한 도구를 스스로 써가며) 답변 → 대화 기록 저장
    AiChatVO chat(AiChatVO vo) throws Exception;

    // 답변 피드백 저장 (👍 UP / 👎 DOWN)
    boolean saveRating(AiChatVO vo);

    // 🔁 프롬프트 개선 채점용: 주어진 시스템 프롬프트로 답만 받기 (대화 기록 저장 안 함) → { answer, tools }
    java.util.Map<String, Object> answerWith(String systemPrompt, String question, Long userId);
}
