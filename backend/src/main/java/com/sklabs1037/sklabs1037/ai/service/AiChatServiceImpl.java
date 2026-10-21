package com.sklabs1037.sklabs1037.ai.service;

import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.client.TossApiClient;
import com.sklabs1037.sklabs1037.ai.dao.AiChatDao;
import com.sklabs1037.sklabs1037.ai.vo.AiChatVO;
import com.sklabs1037.sklabs1037.ai.vo.AiScreenVO;
import com.sklabs1037.sklabs1037.ai.vo.AiStockVO;
import com.sklabs1037.sklabs1037.mock.service.IMockTradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
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
 *   - "왜 올랐/내렸어?" 같은 질문에 숫자만 답하던 문제
 *     → search_news 도구 추가 (30분마다 수집하는 T_NEWS를 RAG 검색 대상으로)
 *   - 모의투자 기능 추가 후 "내 포트폴리오 어때?"에 답하지 못하던 문제
 *     → get_my_mock_portfolio 도구 추가 (질문한 회원 본인의 계좌만 조회, 회원 번호는 AI가 아니라 서버 세션에서)
 *   - 종목이 3,900개로 늘어 "코스닥에서 5일 연속 오른 종목" 같은 조건 질문이 생김
 *     → screen_stocks 도구 추가 (AI는 정해진 조건 칸만 채우고, SQL은 서버에 고정 → 아무 SQL이나 실행될 위험 없음)
 */
@Slf4j
@Service
public class AiChatServiceImpl implements IAiChatService {

    // 도구 반복 최대 횟수 (무한 반복 + 비용 폭주 방지)
    private static final int MAX_TURNS = 5;

    // AI의 역할과 규칙 (기본 = 버전 0)
    // - 실제로 쓰는 프롬프트는 PromptStore가 정함: 관리자가 적용한 개선 버전이 있으면 그것, 없으면 이것
    static final String BASE_PROMPT = """
            너는 SKLabs의 한국 주식 분석 도우미야.
            규칙:
            - 주가, 날짜, 거래량 같은 숫자는 반드시 도구로 조회한 값만 사용해. 절대 지어내지 마.
            - 종목코드를 모르면 먼저 find_stock으로 찾아.
            - 여러 종목의 수익률 비교나 순위 질문은 get_return_ranking 한 번으로 답해. 수익률을 직접 계산하지 마.
            - "~한 종목 찾아줘"처럼 조건으로 종목을 고르는 질문(연속 상승·하락, 기간 수익률, 시가총액, 거래량 급증, 최고가 근처, 가격대)은 screen_stocks로 답해.
              조건을 도구 입력으로 옮기고, 결과는 "- 종목명(코드): 핵심 숫자" 한 줄씩. 조건에 맞는 전체 개수(matchCount)와 기준일도 알려줘.
              시가총액은 억 원 단위로 넣어 (1조 = 10000). 결과가 없으면 조건을 조금 넓혀 다시 찾아볼지 제안해.
            - 회사가 무슨 사업을 하는지 같은 질문은 search_stock_docs로 문서를 찾아 그 내용을 근거로 답해.
            - 최근 이슈, 주가가 오르거나 내린 이유, 뉴스 질문은 search_news로 최근 기사를 찾아 근거로 답해.
            - "내 포트폴리오", "내 모의투자", "내 계좌", "내 보유 종목" 질문은 get_my_mock_portfolio로 조회해서 답해.
              종목 비중이 한쪽에 몰렸는지, 수익/손실 종목, 현금 비중을 짚어주고 필요하면 다른 도구로 해당 종목 흐름·뉴스를 확인해.
              모의투자는 가상 자금 3,000만 원으로 하는 연습 계좌라는 점을 기억해.
            - 근거는 가장 중요한 3~4개만 골라 짧게 요약해. 비슷한 기사는 하나로 묶어.
            - 지원 종목은 코스피·코스닥 상장 주식과 ETF야. find_stock으로 찾아도 없으면 지원하지 않는다고 말해.
            - 시세 데이터의 기준 날짜를 함께 알려줘.
            - 투자 권유(사라/팔라)는 하지 말고, 데이터 기반 설명만 해. 답변 끝에 "투자 판단은 본인 책임입니다."를 붙여.
            - 한국어로 간결하게. 마크다운 표, 제목(#)은 쓰지 말고 짧은 문단과 "-" 목록만 사용해. 강조가 꼭 필요할 때만 **굵게**를 써.
            """;

    // screen_stocks 정렬 (정해진 값만 SQL로, TOOLS보다 먼저 만들어져야 함)
    private static final Set<String> SCREEN_SORTS = Set.of(
            "return_desc", "return_asc", "change_desc", "change_asc", "market_cap_desc",
            "volume_ratio_desc", "up_streak_desc", "down_streak_desc");

    // Claude에게 알려줄 도구 목록 (이름, 설명, 입력 형식)
    private static final List<Map<String, Object>> TOOLS = List.of(
            tool("find_stock",
                    "종목명 또는 6자리 종목코드로 코스피·코스닥 종목(ETF 포함 약 3,000개)을 찾는다. 최대 20개. query를 빈 문자열로 주면 주요 종목 목록을 준다.",
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
                    "코스피·코스닥 주식(ETF 제외)의 최근 N거래일 수익률(%) 상위 15개와 하위 15개를 높은 순으로 준다. 시작일/종료일 종가 포함. 순위 질문에 사용.",
                    Map.of("days", Map.of("type", "integer", "description", "수익률 기간 (거래일 수, 1~99)")),
                    List.of("days")),
            tool("screen_stocks",
                    "코스피·코스닥 종목(약 3,900개)을 조건으로 골라준다(스크리너). 조건은 모두 선택이고 함께 쓰면 AND. 최근 거래일 종가 기준. 최대 30개 + 조건에 맞는 전체 개수(matchCount).",
                    screenProperties(),
                    List.of()),
            tool("search_stock_docs",
                    "종목 설명 문서를 키워드로 검색한다. 회사의 사업 내용, 특징을 답할 때 근거로 사용.",
                    Map.of("query", Map.of("type", "string", "description", "핵심 단어만 공백으로 구분 (조사 빼고). 예: 'SK하이닉스 HBM'")),
                    List.of("query")),
            tool("search_news",
                    "30분마다 수집되는 최근 뉴스(제목, 언론사, 발행 시각)를 키워드로 검색해 최신순으로 최대 10건 준다. 최근 이슈, 주가 변동 이유를 답할 때 근거로 사용.",
                    Map.of("query", Map.of("type", "string", "description", "핵심 단어만 공백으로 구분. 예: '삼성전자', '코스피 외국인'"),
                            "days", Map.of("type", "integer", "description", "최근 며칠 기사인지 (1~7, 기본 3)")),
                    List.of("query")),
            tool("get_my_mock_portfolio",
                    "질문한 사용자 본인의 모의투자 계좌를 조회한다. 현금, 총자산, 시작 자금 대비 수익률, 보유 종목별 수량·평균 단가·현재가·평가손익·비중(%), 체결 대기 중인 지정가 주문, 최근 거래 5건을 준다.",
                    Map.of(),
                    List.of())
    );

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private PromptStore promptStore;

    @Autowired
    private TossApiClient tossApiClient;

    @Autowired
    private AiChatDao aiChatDao;

    @Autowired
    private IMockTradeService mockTradeService;

    // 도구 결과를 JSON 문자열로 바꿀 때 사용 (Spring Boot가 만들어둔 것 재사용)
    @Autowired
    private JsonMapper jsonMapper;

    // ============================================
    // Agent 반복
    // ============================================
    @Override
    public AiChatVO chat(AiChatVO vo) throws Exception {

        PromptStore.Active prompt = promptStore.active();
        vo.setPromptVersion(prompt.version());

        AgentResult r = runAgent(prompt.content(), vo.getMessage(), vo.getUserId());

        return finish(vo, r.answer(), r.toolsUsed());

    }

    // 🔁 프롬프트 개선 채점용: 주어진 프롬프트로 답만 받기 (기록 저장 안 함)
    @Override
    public Map<String, Object> answerWith(String systemPrompt, String question, Long userId) {

        AgentResult r = runAgent(systemPrompt, question, userId);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answer", r.answer());
        m.put("tools", String.join(", ", r.toolsUsed()));

        return m;

    }

    record AgentResult(String answer, List<String> toolsUsed) {
    }

    // 질문 하나를 Claude + 도구 반복으로 답하기
    @SuppressWarnings("unchecked")
    private AgentResult runAgent(String systemPrompt, String question, Long userId) {

        // 대화 기록 (질문 → 도구 요청 → 도구 결과 → ... 순서로 쌓임)
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", question));

        List<String> toolsUsed = new ArrayList<>();

        for (int turn = 0; turn < MAX_TURNS; turn++) {

            // 1. Claude 호출
            Map<String, Object> response = claudeApiClient.createMessage(systemPrompt, TOOLS, messages);
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

            // Claude의 응답도 대화 기록에 추가 (다음 호출 때 "내가 이 도구를 요청했었지"를 알 수 있게)
            messages.add(Map.of("role", "assistant", "content", content));

            // 2. 도구 요청이 아니면 → 최종 답변
            if (!"tool_use".equals(response.get("stop_reason"))) {

                String answer = extractText(content);

                // 답변 길이 제한에 걸려 중간에 끊긴 경우 → 끊긴 걸 그대로 보여주지 않고 안내 문구 추가
                if ("max_tokens".equals(response.get("stop_reason"))) {
                    log.warn("AI 답변 길이 초과로 잘림: {}", question);
                    answer += "\n\n(답변이 길어 일부가 생략되었습니다. 궁금한 부분을 나눠서 질문해주세요.)";
                }

                return new AgentResult(answer, toolsUsed);

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

                    result.put("content", jsonMapper.writeValueAsString(runTool(toolName, input, userId)));

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
        return new AgentResult("질문이 복잡해서 답변을 끝내지 못했어요. 조금 더 구체적으로 질문해주세요.", toolsUsed);

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
    // userId: 질문한 회원 (내 포트폴리오 조회용, AI가 고를 수 없게 서버 세션 값만 사용)
    private Object runTool(String toolName, Map<String, Object> input, Long userId) {

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

                int days = intValue(input.get("days"), 20, 1, 100);

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

                int days = intValue(input.get("days"), 20, 1, 99);   // DB에 100일치

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

                List<String> keywords = splitKeywords(input.get("query"));
                if (keywords.isEmpty()) return List.of();

                AiStockVO param = new AiStockVO();
                param.setKeywords(keywords);

                List<Map<String, Object>> list = new ArrayList<>();
                for (AiStockVO d : aiChatDao.searchDocs(param)) {
                    list.add(Map.of("symbol", d.getSymbol(), "name", d.getName(), "title", d.getTitle(), "content", d.getContent()));
                }

                return list;

            }

            case "search_news": {

                List<String> keywords = splitKeywords(input.get("query"));
                if (keywords.isEmpty()) return List.of();

                AiStockVO param = new AiStockVO();
                param.setKeywords(keywords);
                param.setDays(intValue(input.get("days"), 3, 1, 7));

                List<Map<String, Object>> list = new ArrayList<>();
                for (AiStockVO n : aiChatDao.searchNews(param)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("title", n.getTitle());
                    row.put("press", n.getPress());
                    row.put("publishedAt", n.getPubDate().toString());
                    row.put("stock", n.getName());
                    list.add(row);
                }

                return list;

            }

            case "get_my_mock_portfolio": {

                if (userId == null) throw new IllegalArgumentException("로그인 정보가 없습니다.");

                Map<String, Object> account = mockTradeService.getAccount(userId);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("account", account.get("account"));
                result.put("holdings", account.get("holdings"));
                result.put("pendingOrders", account.get("pendingOrders"));
                result.put("recentTrades", mockTradeService.getTrades(userId, 1, 5).get("trades"));
                result.put("note", "가격 출처 LIVE=토스 현재가, CLOSE=최근 종가. 금액 단위는 원, 비율 단위는 %.");

                return result;

            }

            case "screen_stocks": {

                AiScreenVO param = new AiScreenVO();
                String market = str(input.get("market")).toUpperCase();
                param.setMarket(Set.of("STOCK", "KOSPI", "KOSDAQ", "ETF", "ALL").contains(market) ? market : "STOCK");
                param.setDays(intValue(input.get("days"), 20, 1, 60));
                param.setMinReturn(decimal(input.get("min_return"), -100, 1000));
                param.setMaxReturn(decimal(input.get("max_return"), -100, 1000));
                param.setMinChange(decimal(input.get("min_change"), -30, 30));
                param.setMaxChange(decimal(input.get("max_change"), -30, 30));
                param.setMinCapEok(decimal(input.get("min_market_cap_eok"), 0, 100_000_000));
                param.setMaxCapEok(decimal(input.get("max_market_cap_eok"), 0, 100_000_000));
                param.setMinPrice(decimal(input.get("min_price"), 0, 100_000_000));
                param.setMaxPrice(decimal(input.get("max_price"), 0, 100_000_000));
                param.setMinUpStreak(input.get("min_up_streak") == null ? null : intValue(input.get("min_up_streak"), 1, 1, 20));
                param.setMinDownStreak(input.get("min_down_streak") == null ? null : intValue(input.get("min_down_streak"), 1, 1, 20));
                param.setMinVolumeRatio(decimal(input.get("min_volume_ratio"), 0, 100));
                param.setMaxFromHigh(decimal(input.get("max_from_high"), 0, 100));
                String sort = str(input.get("sort"));
                param.setSort(SCREEN_SORTS.contains(sort) ? sort : "return_desc");
                param.setLimit(intValue(input.get("limit"), 15, 1, 30));

                List<AiScreenVO> rows = aiChatDao.selectScreen(param);

                List<Map<String, Object>> list = new ArrayList<>();
                for (AiScreenVO r : rows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("symbol", r.getSymbol());
                    row.put("name", r.getName());
                    row.put("market", "ETF".equals(r.getSecurityType()) ? "ETF" : r.getStockMarket());
                    row.put("close", r.getClosePrice());
                    row.put("returnRate", r.getReturnRate());
                    row.put("changeRate", r.getChangeRate());
                    row.put("marketCapEok", r.getMarketCapEok());
                    row.put("upStreak", r.getUpStreak());
                    row.put("downStreak", r.getDownStreak());
                    row.put("volumeRatio", r.getVolumeRatio());
                    row.put("fromHigh", r.getFromHigh());
                    list.add(row);
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("matchCount", rows.isEmpty() ? 0 : rows.get(0).getMatchCount());
                result.put("baseDate", rows.isEmpty() ? null : String.valueOf(rows.get(0).getEndDate()));
                result.put("periodStart", rows.isEmpty() ? null : String.valueOf(rows.get(0).getStartDate()));
                result.put("periodDays", param.getDays());
                result.put("stocks", list);

                return result;

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
            case "search_news"        -> "뉴스 검색 (" + str(input.get("query")) + ")";
            case "get_my_mock_portfolio" -> "내 모의투자 계좌 조회";
            case "screen_stocks"      -> "조건 검색 (" + describeScreen(input) + ")";
            default                   -> toolName;
        };

    }

    // screen_stocks 입력 형식
    private static Map<String, Object> screenProperties() {

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("market", Map.of("type", "string", "enum", List.of("STOCK", "KOSPI", "KOSDAQ", "ETF", "ALL"),
                "description", "STOCK=코스피+코스닥 주식(기본), ALL=주식+ETF"));
        p.put("days", Map.of("type", "integer", "description", "수익률·평균 거래량·최고가를 볼 기간 (거래일 1~60, 기본 20)"));
        p.put("min_return", Map.of("type", "number", "description", "기간 수익률 % 이상 (예: 10)"));
        p.put("max_return", Map.of("type", "number", "description", "기간 수익률 % 이하 (예: -10 → 10% 넘게 하락)"));
        p.put("min_change", Map.of("type", "number", "description", "최근 하루 등락률 % 이상"));
        p.put("max_change", Map.of("type", "number", "description", "최근 하루 등락률 % 이하"));
        p.put("min_market_cap_eok", Map.of("type", "number", "description", "시가총액 하한 (억 원, 1조=10000)"));
        p.put("max_market_cap_eok", Map.of("type", "number", "description", "시가총액 상한 (억 원)"));
        p.put("min_price", Map.of("type", "number", "description", "종가 하한 (원)"));
        p.put("max_price", Map.of("type", "number", "description", "종가 상한 (원)"));
        p.put("min_up_streak", Map.of("type", "integer", "description", "최근 연속 상승 일수 이상 (1~20)"));
        p.put("min_down_streak", Map.of("type", "integer", "description", "최근 연속 하락 일수 이상 (1~20)"));
        p.put("min_volume_ratio", Map.of("type", "number", "description", "최근 거래량 ÷ 기간 평균 거래량 이상 (거래량 급증, 예: 2)"));
        p.put("max_from_high", Map.of("type", "number", "description", "기간 최고 종가에서 몇 % 이내 (최고가 근처, 예: 3)"));
        p.put("sort", Map.of("type", "string", "enum", List.copyOf(new java.util.TreeSet<>(SCREEN_SORTS)),
                "description", "정렬 (기본 return_desc)"));
        p.put("limit", Map.of("type", "integer", "description", "보여줄 개수 (1~30, 기본 15)"));

        return p;

    }

    // 도구 사용 기록용 조건 요약 ("KOSDAQ, 20일, 연속상승≥3")
    private String describeScreen(Map<String, Object> input) {

        List<String> parts = new ArrayList<>();
        parts.add(str(input.getOrDefault("market", "STOCK")));
        if (input.get("days") != null) parts.add(input.get("days") + "일");
        input.forEach((k, v) -> {
            if (v != null && !Set.of("market", "days", "sort", "limit").contains(k)) parts.add(k + "=" + v);
        });

        return String.join(", ", parts);

    }

    // 숫자 입력 (없으면 null, min~max로 제한)
    private BigDecimal decimal(Object value, double min, double max) {

        double d;

        if (value instanceof Number n) {
            d = n.doubleValue();
        } else if (value instanceof String text && !text.isBlank()) {
            try {
                d = Double.parseDouble(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }

        if (Double.isNaN(d) || Double.isInfinite(d)) return null;
        d = Math.max(min, Math.min(d, max));

        return BigDecimal.valueOf(d);

    }

    // 검색어를 단어로 쪼개기 (2글자 이상, 최대 5개)
    private List<String> splitKeywords(Object query) {

        return Arrays.stream(str(query).trim().split("\\s+"))
                .filter(k -> k.length() >= 2)
                .limit(5)
                .toList();

    }

    // 숫자 입력 꺼내기 (없으면 기본값, min~max로 제한)
    private int intValue(Object value, int defaultValue, int min, int max) {

        int n = (value instanceof Number num) ? num.intValue() : defaultValue;

        return Math.max(min, Math.min(n, max));

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