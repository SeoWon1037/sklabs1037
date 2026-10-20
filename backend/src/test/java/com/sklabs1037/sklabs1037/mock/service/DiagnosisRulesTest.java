package com.sklabs1037.sklabs1037.mock.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🩺 포트폴리오 진단: 서버 숫자 · 규칙 점검 · AI 답 정리
 */
class DiagnosisRulesTest {

    @Test
    @SuppressWarnings("unchecked")
    void 몰빵_현금부족_손실이_크면_경고() {

        long now = 1_800_000_000_000L;
        Map<String, Object> account = Map.of("totalAsset", 9_000_000, "cash", 194_500, "pendingCash", 0, "returnRate", -8.49);
        List<Map<String, Object>> holdings = List.of(
                Map.of("name", "SK하이닉스", "weight", 91.0, "returnRate", -8.81),
                Map.of("name", "삼성전자", "weight", 6.8, "returnRate", -6.49));
        List<Map<String, Object>> trades = List.of(
                Map.of("side", "SELL", "name", "카카오", "profit", -300_000, "insAt", now - 1000),
                Map.of("side", "SELL", "name", "카카오", "profit", 100_000, "insAt", now - 2000),
                Map.of("side", "SELL", "name", "NAVER", "profit", -200_000, "insAt", now - 3000),
                Map.of("side", "BUY", "name", "SK하이닉스", "insAt", now - 9L * 24 * 3600 * 1000));

        Map<String, Object> s = DiagnosisService.stats(account, holdings, trades, now);

        assertEquals("SK하이닉스", s.get("topName"));
        assertEquals((Object) 2.2, s.get("cashRatio"));
        assertEquals(3, s.get("sellCount"));
        assertEquals((Object) 33.3, s.get("winRate"));
        assertEquals(3, s.get("tradesLast7d"));      // 9일 전 거래는 빼고

        List<Map<String, Object>> checks = (List<Map<String, Object>>) s.get("checks");
        assertEquals("bad", checks.get(0).get("level"));     // 분산: 91%
        assertEquals("warn", checks.get(1).get("level"));    // 현금 2.2%
        assertEquals("warn", checks.get(2).get("level"));    // 승률 33%
        assertEquals("warn", checks.get(3).get("level"));    // 손익비: 평균 손실 250,000 > 평균 이익 100,000 × 1.5

    }

    @Test
    @SuppressWarnings("unchecked")
    void AI_답_정리() {

        Map<String, Object> out = DiagnosisService.clean(Map.of(
                "summary", "한 종목에 몰려 있어요.",
                "observations", List.of(
                        Map.of("title", "집중 투자", "detail", "91%", "tone", "warn"),
                        Map.of("title", "", "detail", "제목 없음", "tone", "good"),
                        Map.of("title", "기록", "detail", "꾸준해요", "tone", "이상한값"))));

        List<Map<String, Object>> obs = (List<Map<String, Object>>) out.get("observations");
        assertEquals(2, obs.size());                        // 제목 없는 건 버림
        assertEquals("warn", obs.get(1).get("tone"));       // 모르는 값은 warn
        assertEquals(1, ((List<?>) out.get("habits")).size());   // 없으면 기본 습관 1개

    }
}
