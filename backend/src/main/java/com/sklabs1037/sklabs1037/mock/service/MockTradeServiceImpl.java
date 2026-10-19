package com.sklabs1037.sklabs1037.mock.service;

import com.sklabs1037.sklabs1037.mock.dao.MockTradeDao;
import com.sklabs1037.sklabs1037.mock.vo.MockTradeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 모의투자 Service 구현
 *
 * 주문 처리 (한 트랜잭션)
 *   1. 계좌 행을 잠금 (SELECT ... FOR UPDATE) → 같은 사람의 주문은 한 줄로 서서 하나씩 처리
 *   2. 현금 / 보유 수량 확인
 *   3. 보유 종목(수량·평균 단가), 현금, 거래 내역을 같이 저장
 *   → 중간에 하나라도 실패하면 전부 취소 (돈만 빠지고 주식이 안 들어오는 일 없음)
 *   → 버튼을 빠르게 두 번 눌러도 두 번째 주문은 첫 번째가 끝난 잔고로 다시 확인
 *   → 그래도 혹시 모를 경우를 위해 DB에도 CASH >= 0, QUANTITY > 0 CHECK 조건
 *
 * 평균 단가 (추가 매수할 때만 바뀜)
 *   (기존 수량 × 기존 평균 단가 + 이번 체결 금액) ÷ (기존 수량 + 이번 수량)
 * 실현손익 (매도할 때)
 *   (체결 가격 - 평균 단가) × 매도 수량
 */
@Service
public class MockTradeServiceImpl implements IMockTradeService {

    // 시작 자금 1,000만 원
    private static final BigDecimal INIT_CASH = new BigDecimal("10000000");

    // 한 번에 주문할 수 있는 최대 수량
    private static final int MAX_ORDER_QUANTITY = 100_000;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Autowired
    private MockTradeDao mockTradeDao;

    @Autowired
    private MockPriceService mockPriceService;

    // ============================================
    // 내 계좌
    // ============================================
    @Override
    public Map<String, Object> getAccount(Long userId) {

        ensureAccount(userId);

        Map<Long, MockTradeVO> prices = mockPriceService.getPrices();
        MockTradeVO account = mockTradeDao.selectAccount(userId);

        // 1. 보유 종목별 평가
        List<Map<String, Object>> holdings = new ArrayList<>();
        BigDecimal stockValue = BigDecimal.ZERO;
        BigDecimal buyTotal = BigDecimal.ZERO;

        for (MockTradeVO h : mockTradeDao.selectHoldings(userId)) {

            MockTradeVO p = prices.get(h.getStockId());
            BigDecimal price = (p == null) ? h.getAvgPrice() : p.getPrice();   // 가격을 못 구하면 평균 단가로

            BigDecimal qty = BigDecimal.valueOf(h.getQuantity());
            BigDecimal buyAmount = h.getAvgPrice().multiply(qty).setScale(0, RoundingMode.HALF_UP);
            BigDecimal value = price.multiply(qty);
            BigDecimal profit = value.subtract(buyAmount);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stockId", h.getStockId());
            row.put("symbol", h.getSymbol());
            row.put("name", h.getName());
            row.put("quantity", h.getQuantity());
            row.put("avgPrice", h.getAvgPrice());
            row.put("price", price);
            row.put("priceSource", p == null ? "AVG" : p.getPriceSource());
            row.put("buyAmount", buyAmount);
            row.put("value", value);
            row.put("profit", profit);
            row.put("returnRate", rate(profit, buyAmount));
            holdings.add(row);

            stockValue = stockValue.add(value);
            buyTotal = buyTotal.add(buyAmount);

        }

        // 2. 계좌 합계
        BigDecimal totalAsset = account.getCash().add(stockValue);
        BigDecimal totalProfit = totalAsset.subtract(account.getInitCash());

        // 3. 종목 비중 (총자산 대비 %)
        for (Map<String, Object> row : holdings) {
            row.put("weight", rate((BigDecimal) row.get("value"), totalAsset));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cash", account.getCash());
        summary.put("initCash", account.getInitCash());
        summary.put("stockValue", stockValue);
        summary.put("buyTotal", buyTotal);
        summary.put("stockProfit", stockValue.subtract(buyTotal));   // 지금 들고 있는 종목의 평가손익
        summary.put("totalAsset", totalAsset);
        summary.put("profit", totalProfit);                          // 시작 자금 대비 전체 손익 (매도로 확정된 손익 포함)
        summary.put("returnRate", rate(totalProfit, account.getInitCash()));
        summary.put("resetCount", account.getResetCount());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("account", summary);
        result.put("holdings", holdings);
        result.put("stocks", stockList(prices));

        return result;

    }

    // ============================================
    // 거래 내역
    // ============================================
    @Override
    public Map<String, Object> getTrades(Long userId, int page, int size) {

        MockTradeVO param = new MockTradeVO();
        param.setUserId(userId);
        param.setLimit(size);
        param.setOffset((page - 1) * size);

        List<Map<String, Object>> trades = new ArrayList<>();

        for (MockTradeVO t : mockTradeDao.selectTrades(param)) {

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tradeId", t.getTradeId());
            row.put("symbol", t.getSymbol());
            row.put("name", t.getName());
            row.put("side", t.getSide());
            row.put("quantity", t.getQuantity());
            row.put("price", t.getPrice());
            row.put("amount", t.getAmount());
            row.put("profit", t.getProfit());
            row.put("priceSource", t.getPriceSource());
            row.put("insAt", t.getInsAt());
            trades.add(row);

        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trades", trades);
        result.put("total", mockTradeDao.countTrades(userId));
        result.put("page", page);
        result.put("size", size);

        return result;

    }

    // ============================================
    // 주문 (시장가)
    // ============================================
    @Override
    @Transactional
    public Map<String, Object> order(Long userId, Long stockId, String side, int quantity) {

        // 1. 입력 확인
        if (stockId == null) throw new IllegalArgumentException("종목을 선택해주세요.");
        if (!"BUY".equals(side) && !"SELL".equals(side)) throw new IllegalArgumentException("매수 / 매도를 선택해주세요.");
        if (quantity < 1) throw new IllegalArgumentException("수량은 1주 이상 입력해주세요.");
        if (quantity > MAX_ORDER_QUANTITY) throw new IllegalArgumentException("한 번에 " + String.format("%,d", MAX_ORDER_QUANTITY) + "주까지 주문할 수 있어요.");

        MockTradeVO stock = mockPriceService.getPrices().get(stockId);
        if (stock == null) throw new IllegalArgumentException("주문할 수 없는 종목입니다.");

        BigDecimal price = stock.getPrice();
        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal amount = price.multiply(qty);

        // 2. 계좌 잠금 (이 트랜잭션이 끝날 때까지 같은 계좌의 다른 주문은 대기)
        ensureAccount(userId);
        MockTradeVO account = mockTradeDao.selectAccountForUpdate(userId);

        MockTradeVO key = new MockTradeVO();
        key.setUserId(userId);
        key.setStockId(stockId);
        MockTradeVO holding = mockTradeDao.selectHolding(key);

        int holdingQty = (holding == null) ? 0 : holding.getQuantity();
        BigDecimal cash;
        BigDecimal profit = null;

        if ("BUY".equals(side)) {

            // 3-1. 매수: 현금 확인 → 평균 단가 다시 계산
            if (account.getCash().compareTo(amount) < 0) {

                long max = account.getCash().divide(price, 0, RoundingMode.DOWN).longValue();
                throw new IllegalArgumentException("현금이 부족해요. 지금 가격으로 최대 " + String.format("%,d", max) + "주까지 살 수 있어요.");

            }

            BigDecimal oldCost = (holding == null) ? BigDecimal.ZERO
                    : holding.getAvgPrice().multiply(BigDecimal.valueOf(holdingQty));
            int newQty = holdingQty + quantity;

            key.setQuantity(newQty);
            key.setAvgPrice(oldCost.add(amount).divide(BigDecimal.valueOf(newQty), 2, RoundingMode.HALF_UP));
            mockTradeDao.upsertHolding(key);

            cash = account.getCash().subtract(amount);

        } else {

            // 3-2. 매도: 보유 수량 확인 → 실현손익 계산 (평균 단가는 그대로)
            if (holdingQty < quantity) {

                throw new IllegalArgumentException(holdingQty == 0
                        ? "보유하지 않은 종목입니다."
                        : "보유 수량이 부족해요. (보유 " + String.format("%,d", holdingQty) + "주)");

            }

            profit = price.subtract(holding.getAvgPrice()).multiply(qty).setScale(0, RoundingMode.HALF_UP);

            if (holdingQty == quantity) {

                mockTradeDao.deleteHolding(key);   // 전량 매도

            } else {

                key.setQuantity(holdingQty - quantity);
                key.setAvgPrice(holding.getAvgPrice());
                mockTradeDao.upsertHolding(key);

            }

            cash = account.getCash().add(amount);

        }

        // 4. 현금 + 거래 내역 저장
        MockTradeVO cashParam = new MockTradeVO();
        cashParam.setUserId(userId);
        cashParam.setCash(cash);
        mockTradeDao.updateCash(cashParam);

        MockTradeVO trade = new MockTradeVO();
        trade.setUserId(userId);
        trade.setStockId(stockId);
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setAmount(amount);
        trade.setProfit(profit);
        trade.setPriceSource(stock.getPriceSource());
        mockTradeDao.insertTrade(trade);

        // 5. 체결 결과
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tradeId", trade.getTradeId());
        result.put("symbol", stock.getSymbol());
        result.put("name", stock.getName());
        result.put("side", side);
        result.put("quantity", quantity);
        result.put("price", price);
        result.put("amount", amount);
        result.put("profit", profit);
        result.put("priceSource", stock.getPriceSource());
        result.put("cash", cash);

        return result;

    }

    // ============================================
    // 초기화
    // ============================================
    @Override
    @Transactional
    public void reset(Long userId) {

        ensureAccount(userId);
        mockTradeDao.selectAccountForUpdate(userId);   // 주문과 겹치지 않게 잠금

        mockTradeDao.deleteAllHoldings(userId);
        mockTradeDao.deleteAllTrades(userId);
        mockTradeDao.resetAccount(userId);

    }

    // ============================================
    // 수익률 랭킹
    // ============================================
    @Override
    public Map<String, Object> getRanking(Long userId, int size) {

        Map<Long, MockTradeVO> prices = mockPriceService.getPrices();

        // 1. 회원별 주식 평가금액
        Map<Long, BigDecimal> stockValues = new HashMap<>();

        for (MockTradeVO h : mockTradeDao.selectRankingHoldings()) {

            MockTradeVO p = prices.get(h.getStockId());
            if (p == null) continue;

            BigDecimal value = p.getPrice().multiply(BigDecimal.valueOf(h.getQuantity()));
            stockValues.merge(h.getUserId(), value, BigDecimal::add);

        }

        // 2. 회원별 총자산 · 수익률
        List<Map<String, Object>> rows = new ArrayList<>();

        for (MockTradeVO a : mockTradeDao.selectRankingAccounts()) {

            BigDecimal totalAsset = a.getCash().add(stockValues.getOrDefault(a.getUserId(), BigDecimal.ZERO));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nickname", a.getNickname());
            row.put("totalAsset", totalAsset);
            row.put("returnRate", rate(totalAsset.subtract(a.getInitCash()), a.getInitCash()));
            row.put("tradeCount", a.getTradeCount());
            row.put("me", a.getUserId().equals(userId));
            rows.add(row);

        }

        // 3. 수익률 높은 순 (같으면 총자산 큰 순), 같은 수익률은 같은 순위
        rows.sort(Comparator
                .comparing((Map<String, Object> r) -> (BigDecimal) r.get("returnRate")).reversed()
                .thenComparing(r -> (BigDecimal) r.get("totalAsset"), Comparator.reverseOrder()));

        Map<String, Object> me = null;

        for (int i = 0; i < rows.size(); i++) {

            Map<String, Object> row = rows.get(i);
            boolean sameAsPrev = i > 0
                    && ((BigDecimal) row.get("returnRate")).compareTo((BigDecimal) rows.get(i - 1).get("returnRate")) == 0;

            row.put("rank", sameAsPrev ? rows.get(i - 1).get("rank") : i + 1);

            if (Boolean.TRUE.equals(row.get("me"))) me = row;

        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ranking", rows.subList(0, Math.min(size, rows.size())));
        result.put("me", me);                    // 나 (TOP N 밖이어도 내 순위를 보여주려고)
        result.put("participants", rows.size());

        return result;

    }

    // ============================================
    // 공통
    // ============================================

    // 계좌가 없으면 시작 자금으로 만들기 (이미 있으면 아무 일 없음)
    private void ensureAccount(Long userId) {

        MockTradeVO vo = new MockTradeVO();
        vo.setUserId(userId);
        vo.setInitCash(INIT_CASH);

        mockTradeDao.insertAccount(vo);

    }

    // 주문 화면 종목 목록
    private List<Map<String, Object>> stockList(Map<Long, MockTradeVO> prices) {

        List<Map<String, Object>> list = new ArrayList<>();

        for (MockTradeVO s : prices.values()) {

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stockId", s.getStockId());
            row.put("symbol", s.getSymbol());
            row.put("name", s.getName());
            row.put("price", s.getPrice());
            row.put("priceSource", s.getPriceSource());
            row.put("priceTime", s.getPriceTime());
            list.add(row);

        }

        return list;

    }

    // 비율 (%) 소수점 2자리, 기준이 0이면 0
    private BigDecimal rate(BigDecimal part, BigDecimal base) {

        if (base == null || base.signum() == 0) return BigDecimal.ZERO.setScale(2);

        return part.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);

    }
}
