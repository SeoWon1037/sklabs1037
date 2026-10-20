package com.sklabs1037.sklabs1037.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🔁 프롬프트 자기 개선 규칙
 * - 안전 규칙 문구를 지운 개선안은 거절
 * - 0.5점 이상 좋아지고, 👍 받던 질문에서 1점 넘게 떨어지지 않아야 "추천"
 */
class PromptImproveRulesTest {

    private static Map<String, Object> eval(String kind, int oldScore, int newScore) {
        return Map.of("kind", kind, "oldScore", oldScore, "newScore", newScore);
    }

    @Test
    void 안전_규칙_문구를_지우면_거절() {

        String base = AiChatServiceImpl.BASE_PROMPT;

        assertNull(PromptImproveService.validate(base, base + "\n- 비교 질문은 표 대신 목록으로 답해."));
        assertNotNull(PromptImproveService.validate(base, base.replace("투자 판단은 본인 책임입니다", "")));
        assertNotNull(PromptImproveService.validate(base, base));          // 바뀐 게 없음
        assertNotNull(PromptImproveService.validate(base, ""));            // 빈 프롬프트
        assertNotNull(PromptImproveService.validate(base, "도구 지어내지 투자 판단은 본인 책임입니다".repeat(300)));   // 너무 김

    }

    @Test
    void 충분히_좋아지면_추천() {

        PromptImproveService.Score s = PromptImproveService.score(List.of(
                eval("DOWN", 4, 7), eval("DOWN", 5, 7), eval("UP", 8, 8)));

        assertTrue(s.recommend());
        assertEquals("5.67", s.oldAvg().toPlainString());
        assertEquals("7.33", s.newAvg().toPlainString());

    }

    @Test
    void 잘_되던_질문을_망치면_추천하지_않는다() {

        PromptImproveService.Score s = PromptImproveService.score(List.of(
                eval("DOWN", 3, 9), eval("DOWN", 3, 9), eval("UP", 9, 7)));

        assertFalse(s.recommend());

    }

    @Test
    void 조금만_좋아지면_보류() {

        PromptImproveService.Score s = PromptImproveService.score(List.of(eval("DOWN", 6, 6), eval("DOWN", 6, 6), eval("DOWN", 6, 7)));   // +0.33점

        assertFalse(s.recommend());
        assertFalse(PromptImproveService.score(List.of()).recommend());

    }
}
