package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.service.AdCopyRules.Finding;
import com.sklabs1037.sklabs1037.beauty.service.AdCopyRules.Level;
import com.sklabs1037.sklabs1037.beauty.vo.BeautyLogVO;
import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.support.Fakes;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📣 광고 문구 검수
 * - 규칙: 의약품 오인 · 기능성 오인 · 절대 표현 · 실증 필요 표현을 찾고, 겹치면 더 심각한 것 하나만
 * - 기능성을 심사받았다고 고르면 그 표현은 참고(공식 효능 문구 안내)로 내려감
 * - AI가 고친 문구도 서버가 같은 규칙으로 다시 검사
 */
class AdCopyServiceTest {

    private static Map<String, Level> levels(List<Finding> fs) {
        Map<String, Level> m = new LinkedHashMap<>();
        for (Finding f : fs) m.put(f.phrase(), f.level());
        return m;
    }

    @Test
    void 의약품_오인과_절대_표현은_고칠_것() {

        List<Finding> fs = AdCopyRules.check("피부과 의사가 추천한 최고의 크림! 여드름 치료와 피부 재생, 부작용 없음", List.of());
        Map<String, Level> m = levels(fs);

        assertEquals(Level.ERROR, m.get("의사가 추천"));
        assertEquals(Level.ERROR, m.get("최고"));
        assertEquals(Level.ERROR, m.get("여드름 치료"), "여드름(실증)보다 치료(의약품)가 더 심각 → 긴 표현 하나로");
        assertEquals(Level.ERROR, m.get("피부 재생"));
        assertEquals(Level.ERROR, m.get("부작용 없"));
        assertEquals("FAIL", AdCopyRules.verdict(fs));

    }

    @Test
    void 기능성은_심사받았다고_고르면_참고로() {

        String text = "미백과 주름 개선 2중 기능성, SPF50+ PA++++";

        Map<String, Level> none = levels(AdCopyRules.check(text, List.of()));
        assertEquals(Level.ERROR, none.get("미백"));
        assertEquals(Level.ERROR, none.get("주름 개선"));
        assertEquals(Level.ERROR, none.get("SPF50"));

        List<Finding> owned = AdCopyRules.check(text, List.of("미백", "주름", "자외선"));
        assertTrue(owned.stream().allMatch(f -> f.level() == Level.INFO));
        assertEquals("PASS", AdCopyRules.verdict(owned));
        assertTrue(owned.get(0).suggest().contains("피부의 미백에 도움을 줌"));

    }

    @Test
    void 기미_제거는_기능성이어도_고칠_것() {

        Map<String, Level> m = levels(AdCopyRules.check("기미 제거 크림", List.of("미백")));
        assertEquals(Level.ERROR, m.get("기미 제거"));
        assertEquals(1, m.size(), "같은 자리의 '기미'(기능성)는 따로 안 나옴");

    }

    @Test
    void 실증_필요_표현은_확인() {

        List<Finding> fs = AdCopyRules.check("피부과 테스트 완료, 판매 1위, 붓기 케어, 기존 제품보다 2배 촉촉", List.of());
        assertTrue(fs.stream().allMatch(f -> f.level() == Level.WARN), fs.toString());
        assertEquals(List.of("피부과 테스트", "1위", "붓기", "기존 제품보다", "2배"), fs.stream().map(Finding::phrase).toList());
        assertEquals("CHECK", AdCopyRules.verdict(fs));

    }

    @Test
    void 평범한_문구는_통과() {

        List<Finding> fs = AdCopyRules.check("촉촉하고 산뜻한 젤 크림. 바르는 순간 시원하게 스며들어 하루 종일 편안해요.", List.of());
        assertTrue(fs.isEmpty(), fs.toString());
        assertEquals("PASS", AdCopyRules.verdict(fs));

    }

    @Test
    void 무첨가는_뺀_성분을_밝히면_통과() {

        assertTrue(AdCopyRules.check("파라벤 무첨가 크림", List.of()).isEmpty());
        assertEquals(Level.WARN, AdCopyRules.check("100% 무첨가 크림", List.of()).get(0).level());

    }

    @Test
    void 기능성_목록에_없는_값은_버림() {

        assertEquals(List.of("미백"), AdCopyService.cleanClaims(List.of("미백", "보습", "미백")));
        assertThrows(IllegalArgumentException.class, () -> AdCopyService.cleanText("짧음"));

    }

    @Test
    @SuppressWarnings("unchecked")
    void AI가_고친_문구도_서버가_다시_검사() {

        List<BeautyLogVO> saved = new ArrayList<>();
        AdCopyService s = new AdCopyService();
        Fakes.set(s, "beautyLabDao", BeautyTestData.dao(0, saved));
        Fakes.set(s, "jsonMapper", JsonMapper.builder().build());
        Fakes.set(s, "claudeApiClient", new ClaudeApiClient() {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public Map<String, Object> createMessage(String system, List<Map<String, Object>> tools, List<Map<String, Object>> messages,
                                                     Map<String, Object> toolChoice, int maxTokens) {
                // 일부러 "최고"를 남긴 AI 답
                return Map.of("content", List.of(Map.of("type", "tool_use", "name", "rewrite_copy", "input", Map.of(
                        "rewritten", "최고의 보습감으로 피부를 편안하게",
                        "changes", List.of(Map.of("before", "아토피 치료", "after", "피부를 편안하게", "why", "의약품 오인"))))));
            }
        });

        Map<String, Object> r = s.rewrite(7L, "아토피 치료에 좋은 크림", List.of());
        Map<String, Object> rw = (Map<String, Object>) r.get("rewrite");

        assertEquals("FAIL", r.get("verdict"));
        assertEquals("FAIL", rw.get("verdict"), "AI가 남긴 '최고'를 서버가 다시 잡아야 함");
        assertEquals(1L, rw.get("errorCount"));
        assertEquals(1, ((List<?>) rw.get("changes")).size());
        assertEquals(1, saved.size());
        assertEquals("ADCOPY", saved.get(0).getKind());

    }

    @Test
    void 고칠_게_없으면_AI를_부르지_않음() {

        AdCopyService s = new AdCopyService();
        Fakes.set(s, "beautyLabDao", BeautyTestData.dao(0, new ArrayList<>()));
        assertThrows(IllegalArgumentException.class, () -> s.rewrite(1L, "촉촉하고 산뜻한 젤 크림이에요", List.of()));

    }
}
