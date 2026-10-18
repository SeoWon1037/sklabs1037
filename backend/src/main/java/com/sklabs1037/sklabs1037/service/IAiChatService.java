package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.vo.AiChatVO;

/**
 * AI 주식 비서 Service
 */
public interface IAiChatService {

    // 질문 → (필요한 도구를 스스로 써가며) 답변
    AiChatVO chat(AiChatVO vo) throws Exception;
}