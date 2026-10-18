package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.client.TossApiClient;
import com.sklabs1037.sklabs1037.dao.AiChatDao;
import com.sklabs1037.sklabs1037.vo.AiChatVO;
import com.sklabs1037.sklabs1037.vo.AiStockVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * AI 주식 비서 Service 구현 (AI Agent)
 *
 * Agent 동작 방식
 *   1. Claude에게 질문 + 쓸 수 있는 도구 목록을 보냄
 *   2. Claude가 "이 도구 써줘(tool_use)"라고 하면 → 우리가 실행해서 결과를 돌려줌
 *   3. Claude가 최종 답변(end_turn)을 줄 때까지 반복 (최대 MAX_TURNS번)
 *   → 어떤 도구를 언제 쓸지는 Claude가 스스로 판단
 *
 * 피드백
 *   - 모든 질문/답변/사용 도구를 T_AI_CHAT_LOG에 기록
 *   - 사용자가 👍/👎를 누르면 RATING 저장 → 👎 답변을 모아 프롬프트/도구 개선에 활용
 *
 * 개선 이력
 *   - 수익률 비교 질문에서 종목별 시세를 전부 받아 AI가 직접 계산하던 문제
 *     → get_return_ranking 도구 추가 (계산은 SQL, AI는 설명만) : 도구 호출 6회 → 1회
 */
@Slf4j
@Service
public class AiChatServiceImpl implements IAiChatService {

    // 도구 반복 최대 횟수 (무한 반복 + 비용 폭주 방지)
    private static final int MAX_TURNS = 5;

    // AI의 역할과 규칙
    private static final String SYSTEM_PROMPT = """
            너는 SKLabs의 한국 주식 분석 도우미야.
            규칙:
            - 주가, 날짜, 거래량 같은 숫자는 반드시 도구로 조회한 값만 사용해. 절대 지어내지 마.
            - 종목코드를 모르면 먼저 find_stock으로 찾아.
            - 여러 종목의 수익률 비교나 순위 질문은 get_return_ranking 한 번으로 답해. 수익률을 직접 계산하지 마.
            - 회사가 무슨 사업을 하는지 같은 질문은 search_stock_docs로 문서를 찾아 그 내용을 근거로 답해.
            - 지원 종목은 DB에 있는 종목뿐이야. 없는 종목이면 지원하지 않는다고 말해.
            - 시세 데이터의 기준 날짜를 함께 알려줘.
            - 투자 권유(사라/팔라)는 하지 말고, 데이터 기반 설명만 해. 답변 끝에 "투자 판단은 본인 책임입니다."를 붙여.
            - 한국어로 간결하게. 마크다운 표나 제목(#)은 쓰지 말고, 짧은 문단과 "-" 목록만 사용해.
            """;

    // Claude에게 알려줄 도구 목록 (이름, 설명, 입력 형식)
    private static final List<Map<String, Object>> TOOLS = List.of(
            tool("find_stock",
                    "종목명 또는 6자리 종목코드로 지원 종목을 찾는다. query를 빈 문자열로 주면 지원 종목 전체 목록을 준다.",
                    Map.of("query", Map.of("type", "string", "description", "종목명(예: 삼성전자) 또는 종목코드(예: 005930)")),
                    List.of("query")),
            tool("get_current_price",
                    "토스증권 API로 종목의 최신 현재가를 조회한다.",
                    Map.of("symbol", Map.of("type", "string", "description", "6자리 종목코드")),
                    List.of("symbol")),
            tool("get_price_history",
                    "DB에 쌓인 일봉(시가/고가/저가/종가/거래량)을 최신 날짜부터 조회한다. 한 종목의 추세 분석에 사용.",
                    Map.of("symbol", Map.of("type", "string", "description", "6자리 종목코드"),
                            "days", Map.of("type", "integer", "description", "조회할 거래일 수 (1~100)")),
                    List.of("symbol", "days")),
            tool("get_return_ranking",
                    "지원하는 전 종목의 최근 N거래일 수익률(%)을 계산해 높은 순으로 준다. 시작일/종료일 종가 포함. 여러 종목 비교, 순위 질문에 사용.",
                    Map.of("days", Map.of("type", "integer", "description", "수익률 기간 (거래일 수, 1~99)")),
                    List.of("days")),
            tool("search_stock_docs",
                    "종목 설명 문서를 키워드로 검색한다. 회사의 사업 내용, 특징을 답할 때 근거로 사용.",
                    Map.of("query", Map.of("type", "string", "description", "핵심 단어만 공백으로 구분 (조사 빼고). 예: 'SK하이닉스 HBM'")),
                    List.of("query"))
    );

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private TossApiClient tossApiClient;

    @Autowired
    private AiChatDao aiChatDao;

    // 도구 결과를 JSON 문자열로 바꿀 때 사용 (Spring Boot가 만들어둔 것 재사용)
    @Autowired
    private JsonMapper jsonMapper;

    // ============================================
    // Agent 반복
    // ============================================
    @Override
    @SuppressWarnings("unchecked")
    public AiChatVO chat(AiChatVO vo) throws Exception {

        // 대화 기록 (질문 → 도구 요청 → 도구 결과 → ... 순서로 쌓임)
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", vo.getMessage()));

        List<String> toolsUsed = new ArrayList<>();

        for (int turn = 0; turn < MAX_TURNS; turn++) {

            // 1. Claude 호출
            Map<String, Object> response = claudeApiClient.createMessage(SYSTEM_PROMPT, TOOLS, messages);
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

            // Claude의 응답도 대화 기록에 추가 (다음 호출 때 "내가 이 도구를 요청했었지"를 알 수 있게)
            messages.add(Map.of("role", "assistant", "content", content));

            // 2. 도구 요청이 아니면 → 최종 답변
            if (!"tool_use".equals(response.get("stop_reason"))) {

                return finish(vo, extractText(content), toolsUsed);

            }

            // 3. 요청받은 도구들 실행 → 결과를 대화 기록에 추가
            List<Map<String, Object>> toolResults = new ArrayList<>();

            for (Map<String, Object> block : content) {

                if (!"tool_use".equals(block.get("type"))) continue;

                String toolName = (String) block.get("name");
                Map<String, Object> input = (Map<String, Object>) block.get("input");

                log.info("AI 도구 호출: {} {}", toolName, input);
                toolsUsed.add(describeTool(toolName, input));

                Map<String, Object> result = new HashMap<>();
                result.put("type", "tool_result");
                result.put("tool_use_id", block.get("id"));

                try {

                    result.put("content", jsonMapper.writeValueAsString(runTool(toolName, input)));

                } catch (Exception e) {

                    // 도구 실패는 Claude에게 알려서 스스로 다른 방법을 찾게
                    log.warn("AI 도구 실패: {}", toolName, e);
                    result.put("content", "도구 실행 실패: " + e.getMessage());
                    result.put("is_error", true);

                }

                toolResults.add(result);

            }

            messages.add(Map.of("role", "user", "content", toolResults));

        }

        // 최대 횟수 안에 답을 못 끝낸 경우
        return finish(vo, "질문이 복잡해서 답변을 끝내지 못했어요. 조금 더 구체적으로 질문해주세요.", toolsUsed);

    }

    // ============================================
    // 피드백 저장
    // ============================================
    @Override
    public boolean saveRating(AiChatVO vo) {

        return aiChatDao.updateRating(vo) > 0;

    }

    // ============================================
    // 답변 마무리: 결과 담기 + 대화 기록 저장
    // ============================================
    private AiChatVO finish(AiChatVO vo, String answer, List<String> toolsUsed) {

        vo.setAnswer(answer);
        vo.setToolsUsed(toolsUsed);
        vo.setToolsText(String.join(", ", toolsUsed));

        // 기록 저장이 실패해도 답변은 보여줘야 하니까 따로 처리 (chatId가 없으면 화면에서 피드백 버튼만 숨김)
        try {

            aiChatDao.insertChatLog(vo);

        } catch (Exception e) {

            log.warn("AI 대화 기록 저장 실패", e);

        }

        return vo;

    }

    // ============================================
    // 도구 실행
    // ============================================
    private Object runTool(String toolName, Map<String, Object> input) {

        switch (toolName) {

            case "find_stock": {

                AiStockVO param = new AiStockVO();
                param.setQuery(str(input.get("query")).trim());

                List<Map<String, Object>> list = new ArrayList<>();
                for (AiStockVO s : aiChatDao.selectStocks(param)) {
                    list.add(Map.of("symbol", s.getSymbol(), "name", s.getName(), "market", String.valueOf(s.getMarket())));
                }

                return list;

            }

            case "get_current_price": {

                return tossApiClient.getPrices(str(input.get("symbol")));

            }

            case "get_price_history": {

                int days = (input.get("days") instanceof Number n) ? n.intValue() : 20;
                days = Math.max(1, Math.min(days, 100));   // 1~100일로 제한

                AiStockVO param = new AiStockVO();
                param.setSymbol(str(input.get("symbol")));
                param.setDays(days);

                List<Map<String, Object>> list = new ArrayList<>();
                for (AiStockVO p : aiChatDao.selectPriceHistory(param)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", p.getTradeDate().toString());
                    row.put("open", p.getOpenPrice());
                    row.put("high", p.getHighPrice());
                    row.put("low", p.getLowPrice());
                    row.put("close", p.getClosePrice());
                    row.put("volume", p.getVolume());
                    list.add(row);
                }

                return list;

            }

            case "get_return_ranking": {

                int days = (input.get("days") instanceof Number n) ? n.intValue() : 20;
                days = Math.max(1, Math.min(days, 99));   // 1~99거래일로 제한 (DB에 100일치)

                AiStockVO param = new AiStockVO();
                param.setDays(days);

                List<Map<String, Object>> list = new ArrayList<>();
                for (AiStockVO r : aiChatDao.selectReturnRanking(param)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("symbol", r.getSymbol());
                    row.put("name", r.getName());
                    row.put("startDate", r.getStartDate().toString());
                    row.put("startPrice", r.getStartPrice());
                    row.put("endDate", r.getEndDate().toString());
                    row.put("endPrice", r.getEndPrice());
                    row.put("returnRate", r.getReturnRate());
                    list.add(row);
                }

                return list;

            }

            case "search_stock_docs": {

                // 검색어를 단어로 쪼개기 (2글자 이상, 최대 5개)
                List<String> keywords = Arrays.stream(str(input.get("query")).split("\\s+"))
                        .filter(k -> k.length() >= 2)
                        .limit(5)
                        .toList();

                if (keywords.isEmpty()) return List.of();

                AiStockVO param = new AiStockVO();
                param.setKeywords(keywords);

                List<Map<String, Object>> list = new ArrayList<>();
                for (AiStockVO d : aiChatDao.searchDocs(param)) {
                    list.add(Map.of("symbol", d.getSymbol(), "name", d.getName(), "title", d.getTitle(), "content", d.getContent()));
                }

                return list;

            }

            default:
                throw new IllegalArgumentException("알 수 없는 도구: " + toolName);

        }

    }

    // ============================================
    // 공통
    // ============================================

    // 도구 정의 만들기 (Claude가 이해하는 형식)
    private static Map<String, Object> tool(String name, String description,
                                            Map<String, Object> properties, List<String> required) {

        return Map.of(
                "name", name,
                "description", description,
                "input_schema", Map.of("type", "object", "properties", properties, "required", required)
        );

    }

    // 화면에 보여줄 도구 사용 기록 ("시세 이력 조회 (005930, 30일)")
    private String describeTool(String toolName, Map<String, Object> input) {

        return switch (toolName) {
            case "find_stock"         -> "종목 찾기 (" + str(input.get("query")) + ")";
            case "get_current_price"  -> "현재가 조회 (" + str(input.get("symbol")) + ")";
            case "get_price_history"  -> "시세 이력 조회 (" + str(input.get("symbol")) + ", " + input.get("days") + "일)";
            case "get_return_ranking" -> "수익률 순위 (" + input.get("days") + "거래일)";
            case "search_stock_docs"  -> "문서 검색 (" + str(input.get("query")) + ")";
            default                   -> toolName;
        };

    }

    // 응답에서 글자(text) 부분만 모으기
    private String extractText(List<Map<String, Object>> content) {

        StringBuilder sb = new StringBuilder();

        for (Map<String, Object> block : content) {
            if ("text".equals(block.get("type"))) {
                sb.append(block.get("text"));
            }
        }

        return sb.toString().trim();

    }

    // null 안전 문자열 변환
    private String str(Object value) {

        return (value == null) ? "" : String.valueOf(value);

    }
}