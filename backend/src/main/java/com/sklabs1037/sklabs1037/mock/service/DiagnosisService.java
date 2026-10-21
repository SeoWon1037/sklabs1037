package com.sklabs1037.sklabs1037.mock.service;

import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.mock.dao.DiagnosisDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * 🩺 포트폴리오 AI 진단 (모의투자 화면)
 *
 * 1. 서버가 숫자를 계산하고 규칙으로 점검 (분산 · 현금 비중 · 매도 승률 · 손익비 · 거래 빈도) → 좋음/주의/위험
 * 2. Claude가 write_diagnosis 도구로 한 줄 요약 · 관찰 3개 · 해볼 습관 2개
 *    - 종목 추천, "사라/팔아라" 금지 (습관 · 위험 관리 관점만)
 * 3. 하루 3번까지, 마지막 결과는 저장해서 다시 열어도 보임
 */
@Slf4j
@Service
public class DiagnosisService {

    private static final int DAILY_LIMIT = 3;
    private static final long WEEK_MS = 7L * 24 * 3600 * 1000;

    private static final String SYSTEM = """
            너는 SKLabs 모의투자(가상 3,000만 원)의 포트폴리오 코치다.
            서버가 계산한 숫자와 점검 결과, 보유 종목·최근 거래를 보고 투자 "습관"을 진단한다.

            규칙
            - 주어진 숫자만 근거로 말한다. 없는 사실, 미래 주가 전망을 지어내지 않는다.
            - 특정 종목을 사라/팔아라/추천하지 않는다. 비중 조절, 분산, 손절 기준, 기록 습관 같은 일반 원칙으로만 말한다.
            - 업종은 회사 이름으로 확실히 아는 경우에만 언급한다.
            - 존댓말(~해요체), 짧고 구체적으로. 숫자를 인용한다.
            - summary 1~2문장. observations 3개(title 20자 이내, detail 2문장 이내, tone은 good 또는 warn). habits 2개(이번 주에 해볼 구체적 행동).
            - 반드시 write_diagnosis 도구로만 답한다.
            """;

    private static final Map<String, Object> TOOL = Map.of(
            "name", "write_diagnosis",
            "description", "포트폴리오 습관 진단",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "summary", Map.of("type", "string"),
                            "observations", Map.of("type", "array", "minItems", 3, "maxItems", 3, "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "title", Map.of("type", "string"),
                                            "detail", Map.of("type", "string"),
                                            "tone", Map.of("type", "string", "enum", List.of("good", "warn"))),
                                    "required", List.of("title", "detail", "tone"))),
                            "habits", Map.of("type", "array", "minItems", 2, "maxItems", 2, "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of("title", Map.of("type", "string"), "detail", Map.of("type", "string")),
                                    "required", List.of("title", "detail")))),
                    "required", List.of("summary", "observations", "habits")));

    @Autowired
    private IMockTradeService mockTradeService;

    @Autowired
    private DiagnosisDao diagnosisDao;

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private JsonMapper jsonMapper;

    @SuppressWarnings("unchecked")
    public Map<String, Object> last(Long userId) {

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> row = diagnosisDao.selectLast(userId);

        if (row != null) {
            result.put("stats", jsonMapper.readValue(String.valueOf(row.get("statsJson")), LinkedHashMap.class));
            result.put("result", jsonMapper.readValue(String.valueOf(row.get("resultJson")), LinkedHashMap.class));
            result.put("createdAt", row.get("insAt"));
        }

        result.put("remaining", Math.max(0, DAILY_LIMIT - diagnosisDao.countToday(userId)));
        result.put("aiReady", claudeApiClient.isConfigured());
        return result;

    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> diagnose(Long userId) {

        if (!claudeApiClient.isConfigured()) throw new IllegalStateException("AI 키가 설정되지 않았어요.");
        if (diagnosisDao.countToday(userId) >= DAILY_LIMIT) throw new IllegalArgumentException("진단은 하루 " + DAILY_LIMIT + "번까지 받을 수 있어요.");

        Map<String, Object> accountAll = mockTradeService.getAccount(userId);
        Map<String, Object> account = (Map<String, Object>) accountAll.get("account");
        List<Map<String, Object>> holdings = (List<Map<String, Object>>) accountAll.get("holdings");
        List<Map<String, Object>> trades = (List<Map<String, Object>>) mockTradeService.getTrades(userId, 1, 100).get("trades");

        if (trades == null || trades.isEmpty()) throw new IllegalArgumentException("먼저 모의투자로 한 번 이상 거래해 보세요.");

        Map<String, Object> stats = stats(account, holdings, trades, System.currentTimeMillis());

        StringBuilder sb = new StringBuilder("## 서버 계산 숫자\n").append(jsonMapper.writeValueAsString(stats)).append("\n\n## 보유 종목\n");
        for (Map<String, Object> h : holdings) {
            sb.append("- ").append(h.get("name")).append(" 비중 ").append(h.get("weight")).append("% 수익률 ").append(h.get("returnRate")).append("%\n");
        }
        sb.append("\n## 최근 거래 (최신순, 최대 30개)\n");
        for (Map<String, Object> t : trades.stream().limit(30).toList()) {
            sb.append("- ").append(t.get("side")).append(" ").append(t.get("name")).append(" ").append(t.get("quantity")).append("주")
                    .append(t.get("profit") == null ? "" : " 실현손익 " + t.get("profit") + "원").append("\n");
        }

        Map<String, Object> response = claudeApiClient.createMessage(SYSTEM, List.of(TOOL),
                List.of(Map.of("role", "user", "content", sb.toString())), Map.of("type", "tool", "name", "write_diagnosis"), 1500);

        Map<String, Object> input = Map.of();
        if (response != null && response.get("content") instanceof List<?> blocks) {
            for (Object b : blocks) {
                if (b instanceof Map<?, ?> m && "tool_use".equals(m.get("type")) && m.get("input") instanceof Map<?, ?> in) input = (Map<String, Object>) in;
            }
        }

        Map<String, Object> result = clean(input);
        diagnosisDao.insertDiagnosis(userId, jsonMapper.writeValueAsString(stats), jsonMapper.writeValueAsString(result));

        return last(userId);

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================
    static Map<String, Object> stats(Map<String, Object> account, List<Map<String, Object>> holdings,
                                     List<Map<String, Object>> trades, long nowMs) {

        double total = d(account.get("totalAsset"));
        double cash = d(account.get("cash")) + d(account.get("pendingCash"));

        Map<String, Object> top = holdings.stream().max(Comparator.comparingDouble(h -> d(h.get("weight")))).orElse(null);
        double topWeight = top == null ? 0 : d(top.get("weight"));
        double cashRatio = total > 0 ? round(cash / total * 100) : 100;

        int sells = 0;
        int wins = 0;
        double winSum = 0;
        double lossSum = 0;
        int losses = 0;
        int week = 0;
        Set<String> names = new HashSet<>();

        for (Map<String, Object> t : trades) {
            names.add(String.valueOf(t.get("name")));
            if (t.get("insAt") instanceof Number at && nowMs - at.longValue() <= WEEK_MS) week++;
            if (!"SELL".equals(t.get("side")) || t.get("profit") == null) continue;
            sells++;
            double p = d(t.get("profit"));
            if (p > 0) {
                wins++;
                winSum += p;
            } else if (p < 0) {
                losses++;
                lossSum += -p;
            }
        }

        Double winRate = sells > 0 ? round(wins * 100.0 / sells) : null;
        Double avgWin = wins > 0 ? (double) Math.round(winSum / wins) : null;
        Double avgLoss = losses > 0 ? (double) Math.round(lossSum / losses) : null;

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("returnRate", account.get("returnRate"));
        s.put("holdingCount", holdings.size());
        s.put("topName", top == null ? null : top.get("name"));
        s.put("topWeight", round(topWeight));
        s.put("cashRatio", cashRatio);
        s.put("tradeCount", trades.size());
        s.put("tradesLast7d", week);
        s.put("stocksTraded", names.size());
        s.put("sellCount", sells);
        s.put("winRate", winRate);
        s.put("avgWin", avgWin);
        s.put("avgLoss", avgLoss);

        // 규칙 점검 (좋음 good / 주의 warn / 위험 bad / 참고 info)
        List<Map<String, Object>> checks = new ArrayList<>();

        if (holdings.isEmpty()) {
            checks.add(check("SPREAD", "분산", "보유 없음", "info", "지금은 현금만 들고 있어요"));
        } else if (topWeight >= 60) {
            checks.add(check("SPREAD", "분산", top.get("name") + " " + round(topWeight) + "%", "bad", "한 종목이 60% 넘게 차지해요"));
        } else if (topWeight >= 40) {
            checks.add(check("SPREAD", "분산", top.get("name") + " " + round(topWeight) + "%", "warn", "한 종목 비중이 40%를 넘어요"));
        } else {
            checks.add(check("SPREAD", "분산", holdings.size() + "종목 · 최대 " + round(topWeight) + "%", "good", "한 종목에 몰려 있지 않아요"));
        }

        if (cashRatio < 5) checks.add(check("CASH", "현금 비중", cashRatio + "%", "warn", "기회가 와도 살 돈이 거의 없어요"));
        else if (cashRatio > 80) checks.add(check("CASH", "현금 비중", cashRatio + "%", "info", "대부분 현금으로 쉬고 있어요"));
        else checks.add(check("CASH", "현금 비중", cashRatio + "%", "good", "여유 현금이 적당해요"));

        if (sells < 3) checks.add(check("WIN", "매도 승률", sells + "번 매도", "info", "매도 3번 이상부터 계산해요"));
        else checks.add(check("WIN", "매도 승률", winRate + "%", winRate >= 50 ? "good" : "warn", wins + "번 이익 / " + sells + "번 매도"));

        if (avgWin != null && avgLoss != null) {
            boolean bad = avgLoss > avgWin * 1.5;
            checks.add(check("RATIO", "손익비", String.format("%,.0f원 : %,.0f원", avgWin, avgLoss), bad ? "warn" : "good",
                    bad ? "평균 손실이 평균 이익보다 1.5배 넘게 커요" : "이익과 손실 크기가 균형 잡혀 있어요"));
        }

        checks.add(week > 20
                ? check("FREQ", "거래 빈도", "7일 " + week + "번", "warn", "거래가 잦아요. 한 번 더 생각하고 사고팔아요")
                : check("FREQ", "거래 빈도", "7일 " + week + "번", "good", "거래 속도가 차분해요"));

        s.put("checks", checks);
        return s;

    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> clean(Map<String, Object> input) {

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", text(input.get("summary"), 200, "지금 포트폴리오를 숫자로 점검해 봤어요."));

        List<Map<String, Object>> obs = new ArrayList<>();
        if (input.get("observations") instanceof List<?> l) {
            for (Object o : l) {
                if (!(o instanceof Map<?, ?> m) || obs.size() >= 3) continue;
                String title = text(m.get("title"), 30, "");
                if (title.isEmpty()) continue;
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("title", title);
                x.put("detail", text(m.get("detail"), 200, ""));
                x.put("tone", "good".equals(m.get("tone")) ? "good" : "warn");
                obs.add(x);
            }
        }
        out.put("observations", obs);

        List<Map<String, Object>> habits = new ArrayList<>();
        if (input.get("habits") instanceof List<?> l) {
            for (Object o : l) {
                if (!(o instanceof Map<?, ?> m) || habits.size() >= 2) continue;
                String title = text(m.get("title"), 30, "");
                if (title.isEmpty()) continue;
                habits.add(Map.of("title", title, "detail", text(m.get("detail"), 200, "")));
            }
        }
        if (habits.isEmpty()) habits.add(Map.of("title", "매매 전 이유 한 줄 적기", "detail", "사고팔기 전에 왜 그런지 한 줄로 적어 두면 나중에 돌아보기 좋아요."));
        out.put("habits", habits);

        return out;

    }

    private static Map<String, Object> check(String key, String label, String value, String level, String hint) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("value", value);
        m.put("level", level);
        m.put("hint", hint);
        return m;

    }

    private static double d(Object o) {

        return o instanceof Number n ? n.doubleValue() : 0;

    }

    private static double round(double v) {

        return Math.round(v * 10) / 10.0;

    }

    private static String text(Object value, int max, String fallback) {

        String s = value == null ? "" : String.valueOf(value).strip();
        if (s.isEmpty()) return fallback;
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;

    }
}
