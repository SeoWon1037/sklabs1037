package com.sklabs1037.sklabs1037.league.service;

import com.sklabs1037.sklabs1037.league.vo.AiLeagueVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 주문 → 실제 주문 규칙 (AI가 뭐라고 하든 서버가 지키는 것)
 * - 후보·보유 종목만, 하루 주문 수, 보유 종목 수, 한 종목 최대 비중, 최소 현금, 매도 후 매수
 */
class AiLeaguePlanTest {

    private static AiLeagueVO stock(long id, String symbol, String name, long price) {
        AiLeagueVO u = new AiLeagueVO();
        u.setStockId(id);
        u.setSymbol(symbol);
        u.setName(name);
        u.setClosePrice(BigDecimal.valueOf(price));
        return u;
    }

    private static final List<AiLeagueVO> UNIVERSE = List.of(
            stock(1, "005930", "삼성전자", 100_000),
            stock(2, "000660", "SK하이닉스", 500_000),
            stock(3, "035420", "NAVER", 200_000),
            stock(4, "035720", "카카오", 40_000));

    private static Map<String, Object> holding(long id, String symbol, int qty, long price) {
        Map<String, Object> h = new HashMap<>();
        h.put("stockId", id);
        h.put("symbol", symbol);
        h.put("name", symbol);
        h.put("quantity", qty);
        h.put("sellable", qty);
        h.put("price", BigDecimal.valueOf(price));
        h.put("value", BigDecimal.valueOf(price * qty));
        return h;
    }

    private static Map<String, Object> buy(String symbol, double weight) {
        return Map.of("symbol", symbol, "side", "BUY", "targetWeightPct", weight, "reason", "테스트");
    }

    private static Map<String, Object> sell(String symbol, double pct) {
        return Map.of("symbol", symbol, "side", "SELL", "sellPct", pct, "reason", "테스트");
    }

    private static AiLeagueService.Planned find(List<AiLeagueService.Planned> plan, String symbol) {
        return plan.stream().filter(p -> symbol.equals(p.symbol)).findFirst().orElseThrow();
    }

    @Test
    void 목표_비중은_성격별_최대_비중까지만() {

        // 1,000만 원, 현금 전부 → 삼성전자 80% 요청이어도 MOMENTUM 최대 25% → 250만 원 × 0.99 ÷ 10만 = 24주
        List<AiLeagueService.Planned> plan = AiLeagueService.plan(AiTraderStyle.MOMENTUM,
                BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(10_000_000), List.of(), UNIVERSE, List.of(buy("005930", 80)));

        AiLeagueService.Planned p = find(plan, "005930");
        assertEquals("PLANNED", p.status);
        assertEquals(24, p.quantity);

    }

    @Test
    void 후보_밖_종목과_가지지_않은_종목_매도는_버린다() {

        List<AiLeagueService.Planned> plan = AiLeagueService.plan(AiTraderStyle.NEWS,
                BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(10_000_000), List.of(), UNIVERSE,
                List.of(buy("999999", 10), sell("005930", 100)));

        assertEquals("SKIP", find(plan, "999999").status);
        assertEquals("SKIP", find(plan, "005930").status);

    }

    @Test
    void 최소_현금은_남긴다() {

        // STEADY 최소 현금 10%: 현금 120만 원, 총자산 1,000만 → 쓸 수 있는 돈 20만 원 → 카카오 4주(16만) 까지
        List<AiLeagueService.Planned> plan = AiLeagueService.plan(AiTraderStyle.STEADY,
                BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(1_200_000), List.of(), UNIVERSE, List.of(buy("035720", 15)));

        assertEquals(4, find(plan, "035720").quantity);

    }

    @Test
    void 매도한_돈으로_매수하고_매도는_먼저() {

        // 현금 0, 삼성전자 100주(1,000만) 보유 → 절반 팔고 NAVER 20% 매수
        List<Map<String, Object>> holdings = List.of(holding(1, "005930", 100, 100_000));
        List<AiLeagueService.Planned> plan = AiLeagueService.plan(AiTraderStyle.NEWS,
                BigDecimal.valueOf(10_000_000), BigDecimal.ZERO, holdings, UNIVERSE,
                List.of(buy("035420", 20), sell("005930", 50)));

        assertEquals("SELL", plan.get(0).side);
        assertEquals(50, plan.get(0).quantity);
        assertEquals("PLANNED", find(plan, "035420").status);
        assertEquals(9, find(plan, "035420").quantity);   // 200만 × 0.99 ÷ 20만 = 9주

    }

    @Test
    void 하루_주문_수와_종목_수_한도() {

        List<Object> orders = new ArrayList<>();
        for (AiLeagueVO u : UNIVERSE) orders.add(buy(u.getSymbol(), 10));
        orders.add(buy("005930", 10));   // 중복

        // STEADY 하루 4건 → 5번째(중복)는 건너뜀
        List<AiLeagueService.Planned> plan = AiLeagueService.plan(AiTraderStyle.STEADY,
                BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(10_000_000), List.of(), UNIVERSE, orders);

        assertEquals(4, plan.stream().filter(p -> "PLANNED".equals(p.status)).count());
        assertEquals(1, plan.stream().filter(p -> "SKIP".equals(p.status)).count());

        // MOMENTUM 최대 5종목: 이미 5종목 보유 → 새 종목 매수는 건너뜀, 가진 종목 추가 매수는 됨
        List<Map<String, Object>> five = List.of(holding(11, "A1", 1, 1000), holding(12, "A2", 1, 1000), holding(13, "A3", 1, 1000),
                holding(14, "A4", 1, 1000), holding(1, "005930", 1, 100_000));
        List<AiLeagueService.Planned> full = AiLeagueService.plan(AiTraderStyle.MOMENTUM,
                BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(9_000_000), five, UNIVERSE,
                List.of(buy("035420", 10), buy("005930", 10)));

        assertEquals("SKIP", find(full, "035420").status);
        assertEquals("PLANNED", find(full, "005930").status);

    }
}
