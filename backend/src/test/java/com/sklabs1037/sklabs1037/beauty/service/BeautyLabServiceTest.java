package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.dao.BeautyLabDao;
import com.sklabs1037.sklabs1037.beauty.vo.BeautyLogVO;
import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.support.Fakes;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 뷰티 랩 판단 규칙 (AI 없이 사전·규칙으로 판단하는 부분)
 * - 글로 붙여 넣은 분석은 AI를 부르지 않아야 함 (비용 0)
 * - 기능성·알레르기 착향제·피부 타입 주의는 사전 값 그대로
 * - 브리프는 AI 결과를 정해진 모양으로 다듬고, 규제 확인·추천 성분은 서버 규칙으로
 */
class BeautyLabServiceTest {

    private final List<BeautyLogVO> saved = new ArrayList<>();
    private final List<Map<String, Object>> claudeCalls = new ArrayList<>();

    private BeautyLabService service(int todayCount, Map<String, Object> briefInput) {

        BeautyLabDao dao = BeautyTestData.dao(todayCount, saved);

        BeautyLabService s = new BeautyLabService();
        Fakes.set(s, "beautyLabDao", dao);
        Fakes.set(s, "dictionary", BeautyTestData.dictionary(dao));
        Fakes.set(s, "jsonMapper", JsonMapper.builder().build());
        Fakes.set(s, "claudeApiClient", new ClaudeApiClient() {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public Map<String, Object> createMessage(String system, List<Map<String, Object>> tools, List<Map<String, Object>> messages,
                                                     Map<String, Object> toolChoice, int maxTokens) {
                claudeCalls.add(toolChoice);
                return Map.of("content", List.of(Map.of("type", "tool_use", "name", "write_brief", "input", briefInput)));
            }
        });

        return s;

    }

    @Test
    @SuppressWarnings("unchecked")
    void 글_분석은_AI_없이_사전으로_판단() {

        BeautyLabService s = service(0, Map.of());

        Map<String, Object> r = s.analyze(1L, null, null,
                "[전성분] 정제수, 글리세린, 나이아신아마이드(5%), 1,2-헥산다이올, 메칠파라벤, 향료, 리모넨, 이상한성분ABC", "민감성");

        assertTrue(claudeCalls.isEmpty(), "글 분석은 Claude를 부르면 안 됨");
        assertEquals(8, r.get("count"));
        assertEquals(7, r.get("matchedCount"));
        assertEquals(List.of("나이아신아마이드"), ((Map<String, List<String>>) r.get("functional")).get("미백"));
        assertEquals(List.of("리모넨"), r.get("allergens"));
        assertEquals(true, r.get("fragrance"));

        List<String> cautions = ((List<Map<String, Object>>) r.get("skinCautions")).stream().map(c -> (String) c.get("name")).toList();
        assertEquals(List.of("메틸파라벤", "향료", "리모넨"), cautions);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) r.get("ingredients");
        assertEquals("1,2-헥산다이올", rows.get(3).get("name"), "숫자 사이 쉼표에서 나누면 안 됨");
        assertEquals(false, rows.get(7).get("matched"));

        assertEquals(1, saved.size());
        assertEquals("ANALYZE", saved.get(0).getKind());
        assertEquals("TEXT", saved.get(0).getInputType());

    }

    @Test
    void 하루_사용_횟수를_넘으면_막음() {

        BeautyLabService s = service(BeautyLabService.DAILY_LIMIT, Map.of());

        assertThrows(IllegalArgumentException.class, () -> s.analyze(1L, null, null, "정제수, 글리세린", null));
        assertTrue(saved.isEmpty());

    }

    @Test
    void 성분이_없으면_오류() {

        BeautyLabService s = service(0, Map.of());

        assertThrows(IllegalArgumentException.class, () -> s.analyze(1L, null, null, " , , ", null));

    }

    @Test
    @SuppressWarnings("unchecked")
    void 두_제품_비교는_사전_기준으로_같은_성분을_맞춤() {

        BeautyLabService s = service(0, Map.of());

        Map<String, Object> r = s.compare(1L,
                Map.of("text", "정제수, 글리세린, 병풀추출물, 향료", "name", "A크림"),
                Map.of("text", "정제수, 글리세린, 병풀잎추출물, 아데노신", "name", ""),
                "민감성");

        assertEquals(List.of("정제수", "글리세린", "병풀추출물"), r.get("common"), "병풀잎추출물 = 병풀추출물");
        assertEquals(List.of("향료"), r.get("onlyA"));
        assertEquals(List.of("아데노신"), r.get("onlyB"));
        assertEquals(60, r.get("similarity"));   // 3 / (4 + 4 - 3)

        List<String> verdicts = (List<String>) r.get("verdicts");
        assertTrue(verdicts.stream().anyMatch(v -> v.contains("B 제품에만 주름")));
        assertTrue(verdicts.stream().anyMatch(v -> v.contains("B 제품의 주의 성분이 더 적어요")));

    }

    @Test
    @SuppressWarnings("unchecked")
    void 브리프는_정해진_모양으로_다듬고_서버_규칙을_덧붙임() {

        Map<String, Object> ai = new HashMap<>();
        ai.put("productName", "시카 수분 크림");
        ai.put("category", "스킨케어 - 크림");
        ai.put("skinTypes", List.of("민감성"));
        ai.put("texture", "가벼운 젤 크림");
        ai.put("keyBenefits", List.of("보습", "미백", "아무효능"));
        ai.put("mustHave", List.of("병풀추출물", "비밀원료"));
        ai.put("avoid", List.of("향료", "파라벤"));
        ai.put("claims", List.of("비건"));
        ai.put("missingQuestions", List.of("목표 단가는?"));
        ai.put("summary", "요약");

        BeautyLabService s = service(0, ai);
        Map<String, Object> b = s.brief(1L, "민감 피부용 수분 크림, 향료 파라벤 빼고 비건으로");

        assertEquals(Map.of("type", "tool", "name", "write_brief"), claudeCalls.get(0), "정해진 도구로만 답하게 강제");
        assertEquals(List.of("보습", "미백"), b.get("keyBenefits"), "정해진 효능만 남김");

        List<String> avoid = (List<String>) b.get("avoidIngredients");
        assertTrue(avoid.containsAll(List.of("향료", "리모넨", "리날룰", "메틸파라벤", "프로필파라벤", "부틸파라벤")),
                "향료를 빼면 알레르기 착향제도, 파라벤은 파라벤류 전부");

        List<Map<String, Object>> picks = (List<Map<String, Object>>) b.get("suggestions");
        List<String> whitening = ((List<Map<String, Object>>) picks.stream().filter(p -> "미백".equals(p.get("benefit"))).findFirst().orElseThrow().get("ingredients"))
                .stream().map(i -> (String) i.get("name")).toList();
        assertTrue(whitening.contains("나이아신아마이드"));

        List<Map<String, Object>> mustHave = (List<Map<String, Object>>) b.get("mustHaveCheck");
        assertEquals(true, mustHave.get(0).get("known"));
        assertEquals(false, mustHave.get(1).get("known"));

        List<String> regulatory = (List<String>) b.get("regulatory");
        assertTrue(regulatory.stream().anyMatch(n -> n.contains("기능성화장품 심사 또는 보고")));
        assertTrue(regulatory.stream().anyMatch(n -> n.contains("착향제")));
        assertTrue(regulatory.stream().anyMatch(n -> n.contains("비건")));

    }
}
