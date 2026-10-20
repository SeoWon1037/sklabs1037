package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.dao.BeautyLabDao;
import com.sklabs1037.sklabs1037.beauty.dao.RegulationDao;
import com.sklabs1037.sklabs1037.beauty.vo.BeautyLogVO;
import com.sklabs1037.sklabs1037.beauty.vo.RagChunkVO;
import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.client.VoyageClient;
import com.sklabs1037.sklabs1037.support.Fakes;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 규제 Q&A (RAG)
 * - 문서 쪼개기: 조문 경계에서 끊고, 너무 길면 나누되 앞으로 나아가야 함 (무한 반복 금지)
 * - 근거 문단이 없으면 Claude를 부르지 않음 (지어낸 답·비용 방지)
 * - 답에 실제로 인용한 번호만 "답에 사용"으로 표시
 */
class RegulationQaServiceTest {

    private final RegulationQaService service = new RegulationQaService();

    @Test
    void 조문_앞에서_끊고_길이를_넘지_않음() {

        StringBuilder law = new StringBuilder("화장품법 시행규칙\n");
        for (int i = 1; i <= 10; i++) {
            law.append("제").append(i).append("조(조문 ").append(i).append(") ");
            int n = i == 5 ? 60 : 3;   // 5조만 아주 긺
            law.append("이 조문은 화장품의 표시 및 광고에 관한 내용을 정한다. ".repeat(n)).append("\n");
        }

        List<RegulationQaService.Chunk> chunks = service.chunk(List.of(law.toString(), "   ", "둘째 쪽 내용입니다. 기능성화장품 심사 규정."));

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(c -> c.text().length() <= 720), "문단은 약 700자 이하");
        assertTrue(chunks.stream().anyMatch(c -> c.text().startsWith("제5조")), "조문 시작에서 끊김");
        assertEquals(3, chunks.get(chunks.size() - 1).page(), "빈 쪽은 건너뛰고 쪽 번호는 유지");

    }

    @Test
    void 긴_글자_하나도_끝까지_나눔() {

        String longText = "가".repeat(5000);   // 문장 부호·띄어쓰기 없음

        List<RegulationQaService.Chunk> chunks = service.chunk(List.of(longText));

        assertTrue(chunks.size() >= 8);
        assertTrue(chunks.size() < 20);

    }

    @Test
    void TXT_파일은_한_쪽으로() {

        List<String> pages = service.extractPages("notes.txt", "제1조(목적) 내용".getBytes());

        assertEquals(1, pages.size());
        assertEquals("제1조(목적) 내용", pages.get(0));

    }

    private RegulationQaService askService(List<RagChunkVO> hits, List<String> claudeAsked) {

        Map<String, Function<Object[], Object>> reg = new HashMap<>();
        reg.put("searchChunks", a -> hits);

        List<BeautyLogVO> saved = new ArrayList<>();
        BeautyLabDao beautyDao = BeautyTestData.dao(0, saved);

        RegulationQaService s = new RegulationQaService();
        Fakes.set(s, "regulationDao", Fakes.fake(RegulationDao.class, reg, null));
        Fakes.set(s, "beautyLabDao", beautyDao);
        Fakes.set(s, "jsonMapper", JsonMapper.builder().build());
        Fakes.set(s, "voyageClient", new VoyageClient() {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public synchronized List<float[]> embed(List<String> texts, String inputType) {
                assertEquals("query", inputType, "질문은 query 타입으로");
                return List.of(new float[VoyageClient.DIMENSION]);
            }
        });
        Fakes.set(s, "claudeApiClient", new ClaudeApiClient() {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public Map<String, Object> createMessage(String system, List<Map<String, Object>> tools, List<Map<String, Object>> messages,
                                                     Map<String, Object> toolChoice, int maxTokens) {
                claudeAsked.add(String.valueOf(messages.get(0).get("content")));
                return Map.of("content", List.of(Map.of("type", "text", "text", "심사 또는 보고가 필요해요 [2].")));
            }
        });

        return s;

    }

    private RagChunkVO hit(String title, String content, double score) {

        RagChunkVO c = new RagChunkVO();
        c.setTitle(title);
        c.setContent(content);
        c.setPage(3);
        c.setScore(score);
        return c;

    }

    @Test
    void 가까운_문단이_없으면_Claude를_부르지_않음() {

        List<String> asked = new ArrayList<>();
        RegulationQaService s = askService(List.of(hit("시행규칙", "관련 없는 문단", 0.1)), asked);

        Map<String, Object> r = s.ask(1L, "미백 기능성 절차는?");

        assertEquals(false, r.get("found"));
        assertTrue(asked.isEmpty());

    }

    @Test
    @SuppressWarnings("unchecked")
    void 답에_인용한_번호만_표시() {

        List<String> asked = new ArrayList<>();
        RegulationQaService s = askService(List.of(
                hit("시행규칙", "제9조(기능성화장품의 심사)", 0.71),
                hit("심사 규정", "제6조(제출자료의 면제)", 0.66)), asked);

        Map<String, Object> r = s.ask(1L, "미백 기능성 절차는?");

        assertEquals(true, r.get("found"));
        assertTrue(asked.get(0).contains("[1] 시행규칙 (3쪽)"), "발췌에 번호·문서·쪽을 붙여 보냄");

        List<Map<String, Object>> sources = (List<Map<String, Object>>) r.get("sources");
        assertEquals(false, sources.get(0).get("cited"));
        assertEquals(true, sources.get(1).get("cited"));

    }
}
