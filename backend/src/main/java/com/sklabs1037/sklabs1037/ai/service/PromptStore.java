package com.sklabs1037.sklabs1037.ai.service;

import com.sklabs1037.sklabs1037.ai.dao.AiPromptDao;
import com.sklabs1037.sklabs1037.ai.vo.AiPromptVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 🔁 AI 비서가 지금 쓰는 시스템 프롬프트
 * - 관리자가 적용(ACTIVE)한 개선 버전이 있으면 그것, 없으면 코드의 기본 프롬프트(버전 0)
 * - 매 질문마다 DB를 읽지 않게 1분 캐시 (적용·되돌리기 하면 바로 비움)
 */
@Slf4j
@Component
public class PromptStore {

    private static final long CACHE_SECONDS = 60;

    public record Active(int version, String content) {
    }

    @Autowired
    private AiPromptDao aiPromptDao;

    private volatile Active cached;
    private volatile Instant loadedAt = Instant.EPOCH;

    public Active active() {

        Active a = cached;
        if (a != null && Instant.now().isBefore(loadedAt.plusSeconds(CACHE_SECONDS))) return a;

        try {

            AiPromptVO vo = aiPromptDao.selectActive();
            a = (vo == null) ? base() : new Active(vo.getVersion(), vo.getContent());

        } catch (Exception e) {

            // 테이블이 아직 없어도 AI 비서는 동작 (기본 프롬프트)
            log.warn("AI 프롬프트 읽기 실패, 기본 프롬프트 사용: {}", e.getMessage());
            a = base();

        }

        cached = a;
        loadedAt = Instant.now();

        return a;

    }

    public static Active base() {

        return new Active(0, AiChatServiceImpl.BASE_PROMPT);

    }

    public void invalidate() {

        loadedAt = Instant.EPOCH;

    }
}
