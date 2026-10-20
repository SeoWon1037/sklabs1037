package com.sklabs1037.sklabs1037.league.service;

import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.league.dao.AiLeagueDao;
import com.sklabs1037.sklabs1037.league.vo.AiLeagueVO;
import com.sklabs1037.sklabs1037.mock.service.IMockTradeService;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import com.sklabs1037.sklabs1037.mock.vo.MockTradeVO;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * 🤖 AI vs 사람 투자 리그
 *
 * 평일 09:10 (AiLeagueScheduler → OpsJobs "AI_LEAGUE")
 *   AI 투자자마다
 *   1. 내 계좌(현금·보유 종목) + 후보 종목 60개(시총 상위, 5일·20일 수익률, 변동성) + 최근 뉴스 제목을 모아서
 *   2. Claude에게 성격(AiTraderStyle.persona)대로 판단하게 함 → decide_trades 도구로 정해진 모양의 주문만 받음
 *   3. 서버가 규칙으로 주문을 다듬음 (plan): 후보·보유 종목만, 하루 주문 수, 종목 수, 한 종목 최대 비중, 최소 현금
 *   4. 사람과 똑같은 모의투자 시장가 주문으로 체결 (특혜 없음)
 *   5. 매매 일지(시장 판단 + 일지 + 주문 결과)를 저장 → /league 화면에 공개
 *
 * - 하루 한 번만 (T_AI_TRADE_LOG UNIQUE(USER_ID, TRADE_DATE)), 다시 눌러도 건너뜀
 * - AI가 이상한 종목·수량을 말해도 서버 규칙을 통과한 주문만 나감
 */
@Slf4j
@Service
public class AiLeagueService {

    private static final int UNIVERSE_SIZE = 60;
    private static final int STOCK_NEWS = 40;
    private static final int MARKET_NEWS = 12;
    private static final int RECENT_LOGS = 18;

    // 리그 화면 캐시 (현재가 조회가 많아서 1분)
    private static final long CACHE_SECONDS = 60;

    private static final String TOOL_NAME = "decide_trades";

    private static final Map<String, Object> DECIDE_TOOL = Map.of(
            "name", TOOL_NAME,
            "description", "오늘 장 시작 후 낼 모의투자 주문과 매매 일지를 정한다. 아무것도 안 사고팔아도 된다(orders 빈 배열).",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "marketView", Map.of("type", "string", "description", "오늘 시장 판단 한 줄 (80자 이내)"),
                            "diary", Map.of("type", "string",
                                    "description", "매매 일지. 1인칭, 3~5문장, 왜 이 주문을 냈는지(또는 왜 쉬는지) 숫자 근거와 함께. 400자 이내."),
                            "orders", Map.of(
                                    "type", "array",
                                    "maxItems", 6,
                                    "items", Map.of(
                                            "type", "object",
                                            "properties", Map.of(
                                                    "symbol", Map.of("type", "string", "description", "6자리 종목코드 (후보 목록 또는 보유 종목)"),
                                                    "side", Map.of("type", "string", "enum", List.of("BUY", "SELL")),
                                                    "targetWeightPct", Map.of("type", "number",
                                                            "description", "BUY일 때: 이 종목을 총자산의 몇 %까지 채울지"),
                                                    "sellPct", Map.of("type", "number",
                                                            "description", "SELL일 때: 보유 수량의 몇 %를 팔지 (100 = 전부)"),
                                                    "reason", Map.of("type", "string", "description", "이 주문의 이유 (60자 이내)")),
                                            "required", List.of("symbol", "side", "reason")))),
                    "required", List.of("marketView", "diary", "orders")));

    @Autowired
    private AiLeagueDao aiLeagueDao;

    @Autowired
    private IMockTradeService mockTradeService;

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private JsonMapper jsonMapper;

    private volatile Map<String, Object> cache;
    private volatile Instant cachedAt = Instant.EPOCH;

    // ============================================
    // 매일 매매 (스케줄러 / 관리자 "지금 실행")
    // ============================================
    public JobOutcome runAll() {

        LocalDate today = MockMarketHours.now().toLocalDate();

        if (today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return JobOutcome.of(0, 0, "주말이라 쉬어요");
        }

        if (!claudeApiClient.isConfigured()) throw new IllegalStateException("AI 키가 설정되지 않았습니다.");

        List<AiLeagueVO> traders = aiLeagueDao.selectTraders();
        if (traders.isEmpty()) return JobOutcome.of(0, 0, "AI 투자자가 없어요 (sql/ai-league.sql 확인)");

        List<AiLeagueVO> universe = aiLeagueDao.selectUniverse(UNIVERSE_SIZE);
        if (universe.isEmpty()) throw new IllegalStateException("후보 종목 시세가 없습니다.");

        List<AiLeagueVO> stockNews = safe(() -> aiLeagueDao.selectStockNews(STOCK_NEWS));
        List<AiLeagueVO> marketNews = safe(() -> aiLeagueDao.selectMarketNews(MARKET_NEWS));

        List<String> notes = new ArrayList<>();
        int failed = 0;

        for (AiLeagueVO t : traders) {

            AiLeagueVO key = new AiLeagueVO();
            key.setUserId(t.getUserId());
            key.setTradeDate(today);

            if (aiLeagueDao.countLog(key) > 0) {
                notes.add(t.getNickname() + " 오늘 이미 매매");
                continue;
            }

            try {

                notes.add(t.getNickname() + " " + runOne(t, today, universe, stockNews, marketNews));

            } catch (Exception e) {

                failed++;
                log.warn("AI 리그 매매 실패: {} ({})", t.getNickname(), e.getMessage());
                notes.add(t.getNickname() + " 실패: " + e.getMessage());

                AiLeagueVO fail = new AiLeagueVO();
                fail.setUserId(t.getUserId());
                fail.setTradeDate(today);
                fail.setStatus("FAIL");
                fail.setMessage(cut(String.valueOf(e.getMessage()), 480));
                aiLeagueDao.insertLog(fail);

            }

        }

        cachedAt = Instant.EPOCH;   // 화면 캐시 비우기

        return JobOutcome.of(traders.size(), failed, String.join(" / ", notes));

    }

    // AI 한 명: 판단 → 규칙으로 다듬기 → 체결 → 일지 저장
    @SuppressWarnings("unchecked")
    private String runOne(AiLeagueVO trader, LocalDate today, List<AiLeagueVO> universe,
                          List<AiLeagueVO> stockNews, List<AiLeagueVO> marketNews) {

        AiTraderStyle style = AiTraderStyle.of(trader.getCode());
        if (style == null) throw new IllegalStateException("모르는 AI 성격: " + trader.getCode());

        Map<String, Object> account = mockTradeService.getAccount(trader.getUserId());
        Map<String, Object> summary = (Map<String, Object>) account.get("account");
        List<Map<String, Object>> holdings = (List<Map<String, Object>>) account.get("holdings");

        // 1. AI 판단
        String prompt = buildPrompt(trader, style, today, summary, holdings, universe, stockNews, marketNews);

        Map<String, Object> response = claudeApiClient.createMessage(systemPrompt(style), List.of(DECIDE_TOOL),
                List.of(Map.of("role", "user", "content", prompt)),
                Map.of("type", "tool", "name", TOOL_NAME), 2000);

        Map<String, Object> decision = toolInput(response);

        // 2. 규칙으로 다듬기
        List<Planned> plan = plan(style, num(summary.get("totalAsset")), num(summary.get("cash")), holdings, universe,
                decision.get("orders") instanceof List<?> l ? (List<Object>) l : List.of());

        // 3. 체결 (매도 먼저 → 현금 확보 후 매수)
        int filled = 0;

        for (Planned p : plan) {

            if (!"PLANNED".equals(p.status)) continue;

            try {

                MockTradeVO order = new MockTradeVO();
                order.setStockId(p.stockId);
                order.setSide(p.side);
                order.setQuantity(p.quantity);
                order.setOrderType("MARKET");

                Map<String, Object> r = mockTradeService.order(trader.getUserId(), order);
                p.price = num(r.get("price"));
                p.status = "OK";
                filled++;

            } catch (Exception e) {

                p.status = "FAIL";
                p.note = cut(String.valueOf(e.getMessage()), 120);

            }

        }

        // 4. 일지 저장
        AiLeagueVO logVo = new AiLeagueVO();
        logVo.setUserId(trader.getUserId());
        logVo.setTradeDate(today);
        logVo.setStatus(filled > 0 ? "DONE" : "HOLD");
        logVo.setMarketView(cut(str(decision.get("marketView")), 290));
        logVo.setDiary(cut(str(decision.get("diary")), 990));
        logVo.setOrdersJson(jsonMapper.writeValueAsString(plan.stream().map(Planned::toMap).toList()));
        logVo.setTotalAsset(num(summary.get("totalAsset")).setScale(0, RoundingMode.HALF_UP));
        aiLeagueDao.insertLog(logVo);

        return "주문 " + filled + "건";

    }

    // ============================================
    // 규칙 (AI가 뭐라고 하든 서버가 지킴)
    // ============================================

    // 실제로 낼 주문 하나 (또는 건너뛴 이유)
    static final class Planned {

        Long stockId;
        String symbol;
        String name;
        String side;
        int quantity;
        BigDecimal price;          // 계획 때는 예상가, 체결 후 실제 체결가
        String reason;
        String status;             // PLANNED → OK / FAIL, 또는 SKIP
        String note;

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("symbol", symbol);
            m.put("name", name);
            m.put("side", side);
            m.put("quantity", quantity);
            m.put("price", price);
            m.put("reason", reason);
            m.put("status", status);
            m.put("note", note);
            return m;
        }
    }

    /**
     * AI 주문 → 실제 주문 (순수 계산, 테스트 가능)
     * - 하루 주문 수, 중복 종목 정리
     * - SELL: 보유 종목만, sellPct(기본 100%)만큼 (최소 1주)
     * - BUY : 후보·보유 종목만, 목표 비중은 성격별 최대 비중까지, 새 종목은 최대 종목 수까지,
     *         최소 현금 비중은 남기고, 이미 목표 비중이면 건너뜀
     * - 매도 → 매수 순서 (매도로 생길 현금을 매수 계산에 넣음)
     */
    static List<Planned> plan(AiTraderStyle style, BigDecimal totalAsset, BigDecimal cash,
                              List<Map<String, Object>> holdings, List<AiLeagueVO> universe, List<Object> aiOrders) {

        Map<String, AiLeagueVO> bySymbol = new HashMap<>();
        for (AiLeagueVO u : universe) bySymbol.put(u.getSymbol(), u);

        Map<String, Map<String, Object>> held = new HashMap<>();
        for (Map<String, Object> h : holdings) held.put(String.valueOf(h.get("symbol")), h);

        List<Planned> sells = new ArrayList<>();
        List<Planned> buys = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        int count = 0;

        for (Object o : aiOrders) {

            if (!(o instanceof Map<?, ?> m)) continue;

            Planned p = new Planned();
            p.symbol = str(m.get("symbol")).trim();
            p.side = str(m.get("side")).trim().toUpperCase(Locale.ROOT);
            p.reason = cut(str(m.get("reason")), 100);

            if (++count > style.maxOrders) {
                skip(p, "하루 주문 " + style.maxOrders + "건 초과");
                buys.add(p);
                continue;
            }

            if (!seen.add(p.symbol)) {
                skip(p, "같은 종목 중복 주문");
                buys.add(p);
                continue;
            }

            Map<String, Object> h = held.get(p.symbol);
            AiLeagueVO u = bySymbol.get(p.symbol);

            if ("SELL".equals(p.side)) {

                if (h == null) {
                    p.name = u == null ? p.symbol : u.getName();
                    skip(p, "보유하지 않은 종목");
                    sells.add(p);
                    continue;
                }

                p.stockId = ((Number) h.get("stockId")).longValue();
                p.name = str(h.get("name"));
                p.price = num(h.get("price"));

                int sellable = ((Number) h.getOrDefault("sellable", h.get("quantity"))).intValue();
                double pct = clamp(m.get("sellPct"), 1, 100, 100);
                p.quantity = Math.min(sellable, Math.max(1, (int) Math.ceil(sellable * pct / 100)));

                if (sellable < 1) skip(p, "팔 수 있는 수량 없음");
                else p.status = "PLANNED";

                sells.add(p);

            } else if ("BUY".equals(p.side)) {

                if (u == null && h == null) {
                    skip(p, "후보 종목 밖");
                    buys.add(p);
                    continue;
                }

                p.stockId = u != null ? u.getStockId() : ((Number) h.get("stockId")).longValue();
                p.name = u != null ? u.getName() : str(h.get("name"));
                p.price = h != null && h.get("price") != null ? num(h.get("price")) : u.getClosePrice();
                p.status = "PLANNED";
                p.note = String.valueOf(clamp(m.get("targetWeightPct"), 1, style.maxWeightPct, style.maxWeightPct));   // 잠깐 목표 비중 보관

                buys.add(p);

            } else {

                skip(p, "매수/매도 구분 오류");
                buys.add(p);

            }

        }

        // 매도로 생길 현금, 매도 후 남는 종목 수
        BigDecimal cashAfter = cash;
        Set<String> remaining = new HashSet<>(held.keySet());

        for (Planned s : sells) {

            if (!"PLANNED".equals(s.status)) continue;

            cashAfter = cashAfter.add(s.price.multiply(BigDecimal.valueOf(s.quantity)));

            int heldQty = ((Number) held.get(s.symbol).get("quantity")).intValue();
            if (s.quantity >= heldQty) remaining.remove(s.symbol);

        }

        BigDecimal minCash = totalAsset.multiply(BigDecimal.valueOf(style.minCashPct)).divide(BigDecimal.valueOf(100), 0, RoundingMode.UP);

        for (Planned b : buys) {

            if (!"PLANNED".equals(b.status)) continue;

            double weight = Double.parseDouble(b.note);
            b.note = null;

            if (b.price == null || b.price.signum() <= 0) {
                skip(b, "가격 정보 없음");
                continue;
            }

            boolean newPosition = !remaining.contains(b.symbol);

            if (newPosition && remaining.size() >= style.maxPositions) {
                skip(b, "보유 종목 " + style.maxPositions + "개 한도");
                continue;
            }

            Map<String, Object> h = held.get(b.symbol);
            BigDecimal current = h == null || !remaining.contains(b.symbol) ? BigDecimal.ZERO : num(h.get("value"));
            BigDecimal target = totalAsset.multiply(BigDecimal.valueOf(weight)).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
            BigDecimal need = target.subtract(current);
            BigDecimal spendable = cashAfter.subtract(minCash);
            BigDecimal budget = need.min(spendable);

            // 체결가가 예상가보다 조금 높아도 현금이 모자라지 않게 1% 여유
            int qty = budget.multiply(BigDecimal.valueOf(0.99)).divide(b.price, 0, RoundingMode.DOWN).intValue();

            if (need.compareTo(b.price) < 0) {
                skip(b, "이미 목표 비중(" + fmt(weight) + "%) 근처");
            } else if (qty < 1) {
                skip(b, "현금 부족 (최소 현금 " + style.minCashPct + "% 유지)");
            } else {
                b.quantity = qty;
                b.note = "목표 비중 " + fmt(weight) + "%";
                cashAfter = cashAfter.subtract(b.price.multiply(BigDecimal.valueOf(qty)));
                remaining.add(b.symbol);
            }

        }

        List<Planned> all = new ArrayList<>(sells);
        all.addAll(buys);

        return all;

    }

    // ============================================
    // 리그 화면 (누구나)
    // ============================================
    @SuppressWarnings("unchecked")
    public Map<String, Object> getLeague() {

        Map<String, Object> cached = cache;
        if (cached != null && Instant.now().isBefore(cachedAt.plusSeconds(CACHE_SECONDS))) return cached;

        synchronized (this) {

            if (cache != null && Instant.now().isBefore(cachedAt.plusSeconds(CACHE_SECONDS))) return cache;

            Map<String, Object> ranking = mockTradeService.getRanking(null, 100_000, "ALL");
            List<Map<String, Object>> rows = (List<Map<String, Object>>) ranking.get("ranking");

            List<Map<String, Object>> humans = rows.stream().filter(r -> !Boolean.TRUE.equals(r.get("ai"))).toList();
            Map<String, Map<String, Object>> aiRows = new HashMap<>();
            for (Map<String, Object> r : rows) if (r.get("aiCode") != null) aiRows.put(String.valueOf(r.get("aiCode")), r);

            // ===== AI 투자자별 =====
            List<Map<String, Object>> traders = new ArrayList<>();

            for (AiLeagueVO t : aiLeagueDao.selectTraders()) {

                AiTraderStyle style = AiTraderStyle.of(t.getCode());
                Map<String, Object> history = mockTradeService.getHistory(t.getUserId());
                Map<String, Object> account = mockTradeService.getAccount(t.getUserId());
                Map<String, Object> summary = (Map<String, Object>) account.get("account");
                Map<String, Object> rank = aiRows.get(t.getCode());

                List<Map<String, Object>> holdings = new ArrayList<>();
                for (Map<String, Object> h : (List<Map<String, Object>>) account.get("holdings")) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("symbol", h.get("symbol"));
                    m.put("name", h.get("name"));
                    m.put("weight", h.get("weight"));
                    m.put("returnRate", h.get("returnRate"));
                    holdings.add(m);
                }
                holdings.sort(Comparator.comparing((Map<String, Object> m) -> num(m.get("weight"))).reversed());

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", t.getCode());
                row.put("nickname", t.getNickname());
                row.put("label", style == null ? t.getCode() : style.label);
                row.put("persona", style == null ? "" : style.persona);
                row.put("rules", style == null ? null : Map.of(
                        "maxPositions", style.maxPositions, "maxWeightPct", style.maxWeightPct,
                        "minCashPct", style.minCashPct, "maxOrders", style.maxOrders));
                row.put("totalAsset", summary.get("totalAsset"));
                row.put("returnRate", summary.get("returnRate"));
                row.put("cashWeight", pct(num(summary.get("cash")), num(summary.get("totalAsset"))));
                row.put("rank", rank == null ? null : rank.get("rank"));
                row.put("holdings", holdings);
                row.put("points", history.get("points"));
                traders.add(row);

            }

            // ===== 사람 =====
            Map<String, Object> human = new LinkedHashMap<>();
            human.put("participants", humans.size());
            human.put("avgReturn", avg(humans.stream().map(r -> num(r.get("returnRate"))).toList()));
            human.put("best", humans.isEmpty() ? null : Map.of(
                    "nickname", humans.get(0).get("nickname"),
                    "returnRate", humans.get(0).get("returnRate"),
                    "rank", humans.get(0).get("rank")));

            // ===== 최근 일지 =====
            List<Map<String, Object>> logs = new ArrayList<>();

            for (AiLeagueVO l : aiLeagueDao.selectRecentLogs(RECENT_LOGS)) {

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("logId", l.getLogId());
                m.put("code", l.getCode());
                m.put("nickname", l.getNickname());
                m.put("tradeDate", l.getTradeDate().toString());
                m.put("status", l.getStatus());
                m.put("marketView", l.getMarketView());
                m.put("diary", l.getDiary());
                m.put("orders", parseOrders(l.getOrdersJson()));
                logs.add(m);

            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("traders", traders);
            result.put("aiAvgReturn", avg(traders.stream().map(r -> num(r.get("returnRate"))).toList()));
            result.put("human", human);
            result.put("participants", ranking.get("participants"));
            result.put("logs", logs);
            result.put("updatedAt", MockMarketHours.now().toString());

            cache = result;
            cachedAt = Instant.now();

            return result;

        }

    }

    // ============================================
    // 프롬프트
    // ============================================
    private static String systemPrompt(AiTraderStyle style) {

        return """
                너는 한국 주식 모의투자 리그에 참가한 AI 투자자 '%s'다. 사람 참가자들과 수익률로 경쟁한다.
                투자 철학: %s

                규칙
                - 주어진 후보 종목(시가총액 상위)과 지금 보유 종목 안에서만 고른다. 목록에 없는 종목코드는 쓰지 않는다.
                - 숫자 근거(수익률·변동성·비중·뉴스 제목)로 판단하고, 근거가 약하면 아무것도 하지 않는다.
                - 서버 규칙: 보유 종목 최대 %d개, 한 종목 최대 %d%%, 현금 최소 %d%% 유지, 하루 주문 최대 %d건. 넘는 주문은 서버가 버린다.
                - 일지는 투자 권유가 아니라 이 AI의 판단 기록이다. "무조건", "확실" 같은 말은 쓰지 않는다.
                - 반드시 decide_trades 도구로만 답한다.
                """.formatted(style.label, style.persona, style.maxPositions, style.maxWeightPct, style.minCashPct, style.maxOrders);

    }

    private static String buildPrompt(AiLeagueVO trader, AiTraderStyle style, LocalDate today,
                                      Map<String, Object> summary, List<Map<String, Object>> holdings,
                                      List<AiLeagueVO> universe, List<AiLeagueVO> stockNews, List<AiLeagueVO> marketNews) {

        StringBuilder sb = new StringBuilder();

        sb.append("오늘: ").append(today).append(" (장 시작 직후, 시장가로 체결)\n\n");

        sb.append("## 내 계좌\n");
        sb.append("총자산 ").append(won(summary.get("totalAsset")))
                .append(" / 현금 ").append(won(summary.get("cash")))
                .append(" / 시작 대비 ").append(summary.get("returnRate")).append("%\n");

        if (holdings.isEmpty()) {
            sb.append("보유 종목 없음 (첫 매매)\n");
        } else {
            sb.append("종목코드 | 이름 | 수량 | 평단 | 현재가 | 수익률 | 비중\n");
            for (Map<String, Object> h : holdings) {
                sb.append(h.get("symbol")).append(" | ").append(h.get("name")).append(" | ").append(h.get("quantity"))
                        .append(" | ").append(won(h.get("avgPrice"))).append(" | ").append(won(h.get("price")))
                        .append(" | ").append(h.get("returnRate")).append("% | ").append(h.get("weight")).append("%\n");
            }
        }

        sb.append("\n## 후보 종목 (시가총액 순)\n종목코드 | 이름 | 시장 | 종가 | 5일 | 20일 | 변동성(연) | 시총(억)\n");
        for (AiLeagueVO u : universe) {
            sb.append(u.getSymbol()).append(" | ").append(u.getName()).append(" | ").append(u.getMarket())
                    .append(" | ").append(won(u.getClosePrice())).append(" | ").append(pctText(u.getRet5()))
                    .append(" | ").append(pctText(u.getRet20())).append(" | ").append(u.getVolAnnual() == null ? "-" : u.getVolAnnual() + "%")
                    .append(" | ").append(u.getCapEok()).append('\n');
        }

        int stockLimit = style.newsHeavy ? stockNews.size() : Math.min(12, stockNews.size());
        if (stockLimit > 0) {
            sb.append("\n## 최근 종목 뉴스 제목\n");
            for (AiLeagueVO n : stockNews.subList(0, stockLimit)) {
                sb.append("- [").append(n.getName()).append(' ').append(n.getSymbol()).append("] ").append(n.getTitle())
                        .append(" (").append(n.getPubAt()).append(")\n");
            }
        }

        if (!marketNews.isEmpty()) {
            sb.append("\n## 최근 시장 뉴스 제목\n");
            for (AiLeagueVO n : marketNews) sb.append("- ").append(n.getTitle()).append(" (").append(n.getPubAt()).append(")\n");
        }

        sb.append("\n").append(trader.getNickname()).append("로서 오늘의 주문과 매매 일지를 정해줘.");

        return sb.toString();

    }

    // ============================================
    // 작은 도구들
    // ============================================
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolInput(Map<String, Object> response) {

        Object content = response == null ? null : response.get("content");

        if (content instanceof List<?> list) {
            for (Object block : list) {
                if (block instanceof Map<?, ?> b && "tool_use".equals(b.get("type")) && TOOL_NAME.equals(b.get("name"))
                        && b.get("input") instanceof Map<?, ?> input) {
                    return (Map<String, Object>) input;
                }
            }
        }

        throw new IllegalStateException("AI가 주문 양식으로 답하지 않았습니다.");

    }

    private List<Map<String, Object>> parseOrders(String json) {

        if (json == null || json.isBlank()) return List.of();

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = jsonMapper.readValue(json, List.class);
            return list;
        } catch (Exception e) {
            return List.of();
        }

    }

    private static <T> List<T> safe(java.util.function.Supplier<List<T>> s) {

        try {
            return s.get();
        } catch (Exception e) {
            log.warn("AI 리그 자료 조회 실패: {}", e.getMessage());
            return List.of();
        }

    }

    private static void skip(Planned p, String why) {

        p.status = "SKIP";
        p.note = why;

    }

    private static double clamp(Object value, double min, double max, double fallback) {

        double v;
        try {
            v = value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            v = fallback;
        }
        if (Double.isNaN(v)) v = fallback;

        return Math.max(min, Math.min(max, v));

    }

    static BigDecimal num(Object o) {

        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());

        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }

    }

    private static BigDecimal pct(BigDecimal part, BigDecimal total) {

        return total.signum() == 0 ? BigDecimal.ZERO : part.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);

    }

    private static BigDecimal avg(List<BigDecimal> list) {

        if (list.isEmpty()) return null;

        return list.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);

    }

    private static String str(Object o) {

        return o == null ? "" : String.valueOf(o);

    }

    private static String cut(String s, int max) {

        if (s == null) return null;

        return s.length() <= max ? s : s.substring(0, max);

    }

    private static String won(Object o) {

        return String.format("%,d원", num(o).setScale(0, RoundingMode.HALF_UP).longValue());

    }

    private static String pctText(BigDecimal v) {

        return v == null ? "-" : (v.signum() > 0 ? "+" : "") + v + "%";

    }

    private static String fmt(double v) {

        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();

    }
}
