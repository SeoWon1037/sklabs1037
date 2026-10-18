package com.sklabs1037.sklabs1037.news.service;

import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.news.dao.NewsSummaryDao;
import com.sklabs1037.sklabs1037.news.vo.NewsSummaryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 뉴스 요약 Service 구현
 *
 *   1. 시장 / 종목별로 최근 N시간 뉴스 제목을 모음 (T_NEWS)
 *   2. 제목 목록을 Claude에 보내 3줄 요약을 받음
 *   3. T_NEWS_SUMMARY 에 저장 (대상마다 1줄, 덮어쓰기)
 *
 * - 종목 요약은 제목에 종목명이 들어간 기사만 사용 (검색 결과에 섞인 다른 기사 제외)
 * - 뉴스가 너무 적으면(min-news 미만) 요약하지 않음
 * - 대상 하나가 실패해도 나머지는 계속 진행 (대상별 try/catch)
 */
@Slf4j
@Service
public class NewsSummaryServiceImpl implements INewsSummaryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String SYSTEM_PROMPT = """
            너는 한국 주식 뉴스 요약 담당이다.
            사용자가 주는 뉴스 제목 목록만 근거로, 지금 어떤 이야기가 오가는지 3줄로 요약한다.

            규칙
            - 정확히 3줄, 각 줄은 "- "로 시작하고 60자 이내
            - 여러 기사에 공통으로 나오는 내용을 우선
            - 제목에 없는 사실, 숫자, 전망을 지어내지 않는다
            - 매수/매도 권유나 투자 조언을 하지 않는다
            - 요약 3줄 외에 다른 말(인사, 제목, 설명)은 쓰지 않는다
            """;

    // 기사 제목에서 종목명 대신 자주 쓰는 다른 이름 (제목 필터에 같이 사용)
    private static final Map<String, List<String>> STOCK_ALIASES = Map.of(
            "NAVER", List.of("네이버"),
            "POSCO홀딩스", List.of("포스코"),
            "삼성바이오로직스", List.of("삼성바이오", "삼바"),
            "LG에너지솔루션", List.of("LG엔솔", "엔솔"),
            "한화에어로스페이스", List.of("한화에어로"),
            "SK하이닉스", List.of("하이닉스"),
            "기아", List.of("기아차")
    );

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private NewsSummaryDao newsSummaryDao;

    // 최근 몇 시간 뉴스로 요약할지
    @Value("${news.summary.hours:24}")
    private int hours;

    // 이보다 뉴스가 적으면 요약 안 함
    @Value("${news.summary.min-news:3}")
    private int minNews;

    // 요약에 넣을 최대 제목 수
    @Value("${news.summary.max-news:30}")
    private int maxNews;

    @Override
    public int summarizeAll() {

        if (!claudeApiClient.isConfigured()) {
            log.warn("Claude API 키가 없어 뉴스 요약을 건너뜀");
            return 0;
        }

        LocalDateTime fromAt = LocalDateTime.now(KST).minusHours(hours);

        int saved = 0;

        // 1. 시장 요약
        saved += summarize("MARKET", "MARKET", null, "국내 증시", fromAt);

        // 2. 종목별 요약
        for (NewsSummaryVO stock : newsSummaryDao.selectTargetStocks()) {
            saved += summarize("STOCK_" + stock.getStockId(), "STOCK", stock.getStockId(), stock.getStockName(), fromAt);
        }

        log.info("뉴스 요약 완료: {}건 저장", saved);

        return saved;

    }

    @Override
    public boolean hasRecentSummary(int withinHours) {

        return newsSummaryDao.countSummarySince(LocalDateTime.now(KST).minusHours(withinHours)) > 0;

    }

    @Override
    public NewsSummaryVO getSummary(Long stockId) {

        return newsSummaryDao.selectSummary(stockId == null ? "MARKET" : "STOCK_" + stockId);

    }

    // 대상 하나 요약
    private int summarize(String targetKey, String category, Long stockId, String targetName, LocalDateTime fromAt) {

        try {

            // 1. 최근 뉴스 제목
            NewsSummaryVO param = new NewsSummaryVO();
            param.setStockId(stockId);
            param.setFromAt(fromAt);
            param.setLimit(maxNews);

            // 종목 요약은 제목에 종목명(또는 다른 이름)이 들어간 기사만
            if (stockId != null) {
                List<String> keywords = new ArrayList<>();
                keywords.add(targetName);
                keywords.addAll(STOCK_ALIASES.getOrDefault(targetName, List.of()));
                param.setKeywords(keywords);
            }

            List<NewsSummaryVO> titles = newsSummaryDao.selectRecentTitles(param);

            if (titles.size() < minNews) {
                newsSummaryDao.deleteSummary(targetKey);   // 예전 요약이 계속 보이지 않게
                log.info("뉴스 요약 건너뜀: {} (뉴스 {}건)", targetName, titles.size());
                return 0;
            }

            // 2. Claude 요약
            StringBuilder userText = new StringBuilder();
            userText.append("[").append(targetName).append("] 최근 ").append(hours).append("시간 뉴스 제목 ")
                    .append(titles.size()).append("건\n");

            for (int i = 0; i < titles.size(); i++) {
                NewsSummaryVO t = titles.get(i);
                userText.append(i + 1).append(". ").append(t.getTitle());
                if (t.getPress() != null) {
                    userText.append(" (").append(t.getPress()).append(")");
                }
                userText.append("\n");
            }

            String summary = requestSummary(userText.toString());

            if (summary.isBlank()) {
                log.warn("뉴스 요약 결과 없음: {}", targetName);
                return 0;
            }

            // 3. 저장
            NewsSummaryVO vo = new NewsSummaryVO();
            vo.setTargetKey(targetKey);
            vo.setCategory(category);
            vo.setStockId(stockId);
            vo.setSummary(summary);
            vo.setNewsCount(titles.size());
            vo.setSummaryAt(LocalDateTime.now(KST));

            int saved = newsSummaryDao.upsertSummary(vo);

            log.info("뉴스 요약: {} (뉴스 {}건)", targetName, titles.size());

            return saved;

        } catch (Exception e) {

            log.warn("뉴스 요약 실패: {}", targetName, e);

            return 0;

        }

    }

    // Claude 호출 → 응답의 text 블록만 이어 붙임
    @SuppressWarnings("unchecked")
    private String requestSummary(String userText) {

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", userText);

        // 요약은 도구가 필요 없어서 tools 는 null
        Map<String, Object> response = claudeApiClient.createMessage(SYSTEM_PROMPT, null, List.of(message));

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

        if (content == null) {
            return "";
        }

        StringBuilder text = new StringBuilder();

        for (Map<String, Object> block : content) {
            if ("text".equals(block.get("type"))) {
                text.append(block.get("text"));
            }
        }

        return text.toString().trim();

    }
}