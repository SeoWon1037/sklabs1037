package com.sklabs1037.sklabs1037.stock.service;

import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.stock.dao.StockBriefDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 🧾 종목 한 장 요약 (종목 상세 맨 위)
 *
 * - 재료: 최근 7일 이 종목 뉴스 제목 · 토론방 분위기(오를 것/내릴 것, 최근 글) · 가격 흐름(서버가 계산한 1일/5일/20일 등락)
 * - Claude가 write_brief 도구로 📰 뉴스 · 💬 토론 분위기 · 📈 가격 흐름 한 줄씩 (투자 판단 · 전망 금지)
 * - 종목마다 3시간 저장 → 그 사이엔 누구나 저장된 요약을 봄 / 새로 만들기는 로그인 · 하루 전체 300번까지
 */
@Slf4j
@Service
public class StockBriefService {

    static final int FRESH_MINUTES = 180;
    private static final int DAILY_CAP = 300;
    private static final int NEWS_DAYS = 7;

    private static final String SYSTEM = """
            너는 주식 종목 정보를 한눈에 정리하는 요약가다. 주어진 재료만 보고 세 줄로 정리한다.

            규칙
            - news: 최근 기사 제목들에서 공통된 이야기를 한 문장으로. 기사가 없으면 "최근 7일 관련 기사가 없어요."
            - board: 토론방 숫자와 글로 본 분위기를 한 문장으로. 글이 없으면 "아직 토론 글이 거의 없어요."
            - price: 서버가 준 등락 숫자를 인용해 가격 흐름을 한 문장으로.
            - 각 줄 60자 이내, 존댓말(~해요체). 사라/팔아라, 목표가, 앞으로 오를지 내릴지 전망은 절대 쓰지 않는다.
            - 재료에 없는 사실을 지어내지 않는다. 토론 글의 주장은 "~라는 의견이 많아요"처럼 의견으로만 전한다.
            - 반드시 write_brief 도구로만 답한다.
            """;

    private static final Map<String, Object> TOOL = Map.of(
            "name", "write_brief",
            "description", "종목 세 줄 요약",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "news", Map.of("type", "string"),
                            "board", Map.of("type", "string"),
                            "price", Map.of("type", "string")),
                    "required", List.of("news", "board", "price")));

    @Autowired
    private StockBriefDao stockBriefDao;

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private JsonMapper jsonMapper;

    private final Set<Long> making = ConcurrentHashMap.newKeySet();
    private final AtomicInteger todayCount = new AtomicInteger();
    private volatile LocalDate countDay = LocalDate.now();

    // 저장된 요약 (없거나 오래됐으면 brief = null 또는 fresh = false)
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(Long stockId) {

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> row = stockBriefDao.selectBrief(stockId);

        if (row != null) {
            Map<String, Object> brief = jsonMapper.readValue(String.valueOf(row.get("briefJson")), LinkedHashMap.class);
            brief.put("insAt", row.get("insAt"));
            result.put("brief", brief);
            result.put("fresh", ((Number) row.get("ageMinutes")).intValue() < FRESH_MINUTES);
        } else {
            result.put("brief", null);
            result.put("fresh", false);
        }

        result.put("aiReady", claudeApiClient.isConfigured());
        return result;

    }

    // 새로 만들기 (3시간 안에 만든 게 있으면 그대로)
    public Map<String, Object> make(Long stockId) {

        Map<String, Object> now = get(stockId);
        if (Boolean.TRUE.equals(now.get("fresh"))) return now;

        if (!claudeApiClient.isConfigured()) throw new IllegalStateException("AI 키가 설정되지 않았어요.");

        Map<String, Object> stock = stockBriefDao.selectStock(stockId);
        if (stock == null) throw new IllegalArgumentException("종목을 찾을 수 없어요.");

        if (!LocalDate.now().equals(countDay)) {
            countDay = LocalDate.now();
            todayCount.set(0);
        }
        if (todayCount.get() >= DAILY_CAP) throw new IllegalStateException("오늘 요약을 너무 많이 만들었어요. 내일 다시 시도해주세요.");

        if (!making.add(stockId)) throw new IllegalStateException("지금 다른 분이 이 종목 요약을 만들고 있어요. 잠시 뒤 새로고침해 주세요.");

        try {

            todayCount.incrementAndGet();

            List<Map<String, Object>> closes = new ArrayList<>(stockBriefDao.selectCloses(stockId, 25));
            Collections.reverse(closes);   // 오래된 → 최신
            Map<String, Object> price = priceStats(closes.stream().map(c -> ((Number) c.get("close")).doubleValue()).toList());
            price.put("date", closes.isEmpty() ? null : closes.get(closes.size() - 1).get("date"));

            List<Map<String, Object>> news = stockBriefDao.selectNews(stockId, NEWS_DAYS, 15);
            Map<String, Object> board = stockBriefDao.selectBoardCounts(stockId, NEWS_DAYS);
            List<String> posts = stockBriefDao.selectRecentPosts(stockId, 10);

            StringBuilder sb = new StringBuilder("종목: ").append(stock.get("name")).append(" (").append(stock.get("symbol")).append(")\n\n");
            sb.append("## 최근 7일 기사 제목\n");
            if (news.isEmpty()) sb.append("(없음)\n");
            for (Map<String, Object> n : news) sb.append("- ").append(n.get("title")).append(" (").append(n.get("press")).append(")\n");
            sb.append("\n## 토론방 최근 7일: 글 ").append(board.get("posts")).append("개, 오를 것 ").append(board.get("up"))
                    .append(", 내릴 것 ").append(board.get("down")).append("\n최근 글:\n");
            if (posts.isEmpty()) sb.append("(없음)\n");
            for (String p : posts) sb.append("- ").append(p.length() > 120 ? p.substring(0, 120) : p).append("\n");
            sb.append("\n## 가격 흐름 (서버 계산, 종가 기준)\n").append(jsonMapper.writeValueAsString(price));

            Map<String, Object> response = claudeApiClient.createMessage(SYSTEM, List.of(TOOL),
                    List.of(Map.of("role", "user", "content", sb.toString())), Map.of("type", "tool", "name", "write_brief"), 600);

            Map<?, ?> input = Map.of();
            if (response != null && response.get("content") instanceof List<?> blocks) {
                for (Object b : blocks) {
                    if (b instanceof Map<?, ?> m && "tool_use".equals(m.get("type")) && m.get("input") instanceof Map<?, ?> in) input = in;
                }
            }

            Map<String, Object> brief = new LinkedHashMap<>();
            brief.put("lines", lines(input, news.isEmpty(), posts.isEmpty()));
            brief.put("news", news.stream().limit(3).map(n -> Map.of(
                    "title", String.valueOf(n.get("title")),
                    "press", Objects.toString(n.get("press"), ""),
                    "link", Objects.toString(n.get("link"), ""))).toList());
            brief.put("board", board);
            brief.put("price", price);

            stockBriefDao.upsertBrief(stockId, jsonMapper.writeValueAsString(brief));
            return get(stockId);

        } finally {

            making.remove(stockId);

        }

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================

    // 종가(오래된 → 최신) → 1일 · 5일 · 20일 등락률, 20일 최고·최저, 20일 범위 안 위치(0~100)
    static Map<String, Object> priceStats(List<Double> closes) {

        Map<String, Object> m = new LinkedHashMap<>();
        int n = closes.size();
        if (n == 0) return m;

        double last = closes.get(n - 1);
        m.put("close", last);
        m.put("change1d", change(closes, 1));
        m.put("change5d", change(closes, 5));
        m.put("change20d", change(closes, 20));

        List<Double> window = closes.subList(Math.max(0, n - 20), n);
        double high = Collections.max(window);
        double low = Collections.min(window);
        m.put("high20", high);
        m.put("low20", low);
        m.put("position20", high == low ? null : Math.round((last - low) / (high - low) * 100));
        return m;

    }

    private static Double change(List<Double> closes, int days) {

        int n = closes.size();
        if (n <= days) return null;
        double base = closes.get(n - 1 - days);
        if (base == 0) return null;
        return Math.round((closes.get(n - 1) - base) / base * 10000) / 100.0;

    }

    static List<Map<String, Object>> lines(Map<?, ?> input, boolean noNews, boolean noPosts) {

        return List.of(
                line("news", "📰", "뉴스", noNews ? "최근 7일 관련 기사가 없어요." : text(input.get("news"), "최근 기사를 정리하지 못했어요.")),
                line("board", "💬", "토론 분위기", noPosts ? "아직 토론 글이 거의 없어요." : text(input.get("board"), "토론 분위기를 정리하지 못했어요.")),
                line("price", "📈", "가격 흐름", text(input.get("price"), "가격 흐름을 정리하지 못했어요.")));

    }

    private static Map<String, Object> line(String key, String icon, String label, String text) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("icon", icon);
        m.put("label", label);
        m.put("text", text);
        return m;

    }

    private static String text(Object v, String fallback) {

        String s = v == null ? "" : String.valueOf(v).strip();
        if (s.isEmpty()) return fallback;
        return s.length() > 90 ? s.substring(0, 89) + "…" : s;

    }
}
