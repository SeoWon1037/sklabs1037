package com.sklabs1037.sklabs1037.mock.service;

import com.sklabs1037.sklabs1037.mock.dao.MockTradeDao;
import com.sklabs1037.sklabs1037.mock.vo.MockTradeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * 모의투자 Service 구현
 *
 * 주문 처리 (한 트랜잭션)
 *   1. 계좌 행을 잠금 (SELECT ... FOR UPDATE) → 같은 사람의 주문·체결·취소는 한 줄로 서서 하나씩 처리
 *   2. 현금 / 보유 수량 확인
 *   3. 보유 종목(수량·평균 단가), 현금, 거래 내역을 같이 저장
 *   → 중간에 하나라도 실패하면 전부 취소 (돈만 빠지고 주식이 안 들어오는 일 없음)
 *   → 그래도 혹시 모를 경우를 위해 DB에도 CASH >= 0, QUANTITY > 0 CHECK 조건
 *
 * 시장가: 지금 가격으로 바로 체결
 * 지정가: 조건이 맞을 때까지 대기
 *   - 매수: (지정가 × 수량)만큼 현금을 미리 빼서 주문에 묶어둠 → 같은 돈으로 두 번 주문 못 함
 *           체결되면 (묶어둔 돈 - 실제 체결 금액)을 돌려줌, 취소·만료되면 전부 돌려줌
 *   - 매도: 주식은 그대로 두고, 대기 중인 매도 수량만큼은 다른 매도를 막음
 *   - 체결 가격: 현재가가 지정가보다 같거나 유리하면 현재가로 (매수는 현재가 ≤ 지정가, 매도는 현재가 ≥ 지정가)
 *   - 정규장(09:00~15:30)에 1분마다 MockOrderScheduler가 확인, 그 거래일 15:35에 미체결은 만료
 *
 * 일별 기록 (T_MOCK_SNAPSHOT)
 *   - 평일 15:40 거래한 적 있는 계좌의 총자산을 하루 한 줄 저장 → 수익률 그래프, 이번 주 랭킹 기준
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

    // 한 사람이 동시에 걸어둘 수 있는 지정가 주문 수
    private static final int MAX_PENDING_ORDERS = 20;

    // 지정가는 현재가 ±30% 안에서만 (실제 상·하한가 범위)
    private static final BigDecimal PRICE_BAND = new BigDecimal("0.30");

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

        MockTradeVO account = mockTradeDao.selectAccount(userId);
        List<MockTradeVO> pendingList = mockTradeDao.selectPendingOrders(userId);
        List<MockTradeVO> holdingList = mockTradeDao.selectHoldings(userId);
        List<Long> featuredIds = mockTradeDao.selectFeaturedStockIds();

        // 현재가는 이 화면에 필요한 종목만 (보유 + 미체결 + 주요 종목)
        Set<Long> ids = new LinkedHashSet<>(featuredIds);
        pendingList.forEach(o -> ids.add(o.getStockId()));
        holdingList.forEach(h -> ids.add(h.getStockId()));
        Map<Long, MockTradeVO> prices = mockPriceService.getPrices(ids);

        // 1. 미체결 주문 (종목별 대기 중인 매도 수량도 같이 계산)
        List<Map<String, Object>> pendingOrders = new ArrayList<>();
        Map<Long, Integer> pendingSell = new HashMap<>();
        BigDecimal pendingCash = BigDecimal.ZERO;

        for (MockTradeVO o : pendingList) {

            MockTradeVO p = prices.get(o.getStockId());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", o.getOrderId());
            row.put("stockId", o.getStockId());
            row.put("symbol", o.getSymbol());
            row.put("name", o.getName());
            row.put("side", o.getSide());
            row.put("quantity", o.getQuantity());
            row.put("limitPrice", o.getLimitPrice());
            row.put("reservedAmount", o.getReservedAmount());
            row.put("orderDate", o.getOrderDate().toString());
            row.put("price", p == null ? null : p.getPrice());
            row.put("insAt", o.getInsAt());
            pendingOrders.add(row);

            if ("SELL".equals(o.getSide())) pendingSell.merge(o.getStockId(), o.getQuantity(), Integer::sum);
            pendingCash = pendingCash.add(o.getReservedAmount());

        }

        // 2. 보유 종목별 평가
        List<Map<String, Object>> holdings = new ArrayList<>();
        BigDecimal stockValue = BigDecimal.ZERO;
        BigDecimal buyTotal = BigDecimal.ZERO;

        for (MockTradeVO h : holdingList) {

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
            row.put("sellable", h.getQuantity() - pendingSell.getOrDefault(h.getStockId(), 0));   // 매도 주문 걸린 수량 빼고
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

        // 3. 계좌 합계 (총자산 = 현금 + 매수 주문에 묶인 돈 + 주식 평가금액)
        BigDecimal totalAsset = account.getCash().add(pendingCash).add(stockValue);
        BigDecimal totalProfit = totalAsset.subtract(account.getInitCash());

        // 4. 종목 비중 (총자산 대비 %)
        for (Map<String, Object> row : holdings) {
            row.put("weight", rate((BigDecimal) row.get("value"), totalAsset));
        }

        ZonedDateTime now = MockMarketHours.now();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cash", account.getCash());                      // 주문 가능 현금
        summary.put("pendingCash", pendingCash);                     // 매수 주문에 묶인 돈
        summary.put("initCash", account.getInitCash());
        summary.put("stockValue", stockValue);
        summary.put("buyTotal", buyTotal);
        summary.put("stockProfit", stockValue.subtract(buyTotal));   // 지금 들고 있는 종목의 평가손익
        summary.put("totalAsset", totalAsset);
        summary.put("profit", totalProfit);                          // 시작 자금 대비 전체 손익 (매도로 확정된 손익 포함)
        summary.put("returnRate", rate(totalProfit, account.getInitCash()));
        summary.put("resetCount", account.getResetCount());

        Map<String, Object> market = new LinkedHashMap<>();
        market.put("open", MockMarketHours.isOpen(now));             // 지금 정규장인지
        market.put("tradingDay", MockMarketHours.tradingDay(now).toString());   // 지금 지정가 주문을 넣으면 유효한 거래일

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("account", summary);
        result.put("holdings", holdings);
        result.put("pendingOrders", pendingOrders);
        result.put("stocks", stockList(featuredIds, prices));   // 주문 칸 빠른 선택 (주요 종목)
        result.put("market", market);

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
            row.put("orderType", t.getOrderId() == null ? "MARKET" : "LIMIT");
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
    // 주문
    // ============================================
    @Override
    @Transactional
    public Map<String, Object> order(Long userId, MockTradeVO request) {

        // 1. 입력 확인
        Long stockId = request.getStockId();
        String side = request.getSide();
        int quantity = (request.getQuantity() == null) ? 0 : request.getQuantity();
        boolean limit = "LIMIT".equals(request.getOrderType());

        if (stockId == null) throw new IllegalArgumentException("종목을 선택해주세요.");
        if (!"BUY".equals(side) && !"SELL".equals(side)) throw new IllegalArgumentException("매수 / 매도를 선택해주세요.");
        if (quantity < 1) throw new IllegalArgumentException("수량은 1주 이상 입력해주세요.");
        if (quantity > MAX_ORDER_QUANTITY) throw new IllegalArgumentException("한 번에 " + won(MAX_ORDER_QUANTITY) + "주까지 주문할 수 있어요.");

        MockTradeVO stock = mockPriceService.getPrice(stockId);
        if (stock == null) throw new IllegalArgumentException("주문할 수 없는 종목입니다. (시세가 아직 없어요)");

        // 2. 계좌 잠금 (이 트랜잭션이 끝날 때까지 같은 계좌의 다른 주문·체결은 대기)
        ensureAccount(userId);
        MockTradeVO account = mockTradeDao.selectAccountForUpdate(userId);

        return limit
                ? placeLimitOrder(userId, account, stock, side, quantity, request.getLimitPrice())
                : placeMarketOrder(userId, account, stock, side, quantity);

    }

    // 시장가: 지금 가격으로 바로 체결
    private Map<String, Object> placeMarketOrder(Long userId, MockTradeVO account, MockTradeVO stock, String side, int quantity) {

        BigDecimal price = stock.getPrice();
        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));

        if ("BUY".equals(side)) {

            if (account.getCash().compareTo(amount) < 0) {

                long max = account.getCash().divide(price, 0, RoundingMode.DOWN).longValue();
                throw new IllegalArgumentException("현금이 부족해요. 지금 가격으로 최대 " + won(max) + "주까지 살 수 있어요.");

            }

        } else {

            checkSellable(userId, stock.getStockId(), quantity);

        }

        MockTradeVO trade = execute(userId, stock, side, quantity, price, account.getCash(), null);

        Map<String, Object> result = tradeResult(stock, trade);
        result.put("orderType", "MARKET");
        result.put("status", "FILLED");

        return result;

    }

    // 지정가: 돈/수량 묶고 대기 (정규장이고 조건이 이미 맞으면 바로 체결)
    private Map<String, Object> placeLimitOrder(Long userId, MockTradeVO account, MockTradeVO stock,
                                                String side, int quantity, BigDecimal limitPrice) {

        // 1. 지정가 확인 (호가 단위, 현재가 ±30%)
        if (limitPrice == null || limitPrice.signum() <= 0) throw new IllegalArgumentException("지정가를 입력해주세요.");

        limitPrice = limitPrice.setScale(0, RoundingMode.DOWN);
        BigDecimal tick = MockMarketHours.tickSize(limitPrice);

        if (limitPrice.remainder(tick).signum() != 0) {
            throw new IllegalArgumentException("이 가격대는 " + won(tick.longValue()) + "원 단위로 주문할 수 있어요.");
        }

        BigDecimal current = stock.getPrice();
        BigDecimal band = current.multiply(PRICE_BAND);

        if (limitPrice.compareTo(current.add(band)) > 0 || limitPrice.compareTo(current.subtract(band)) < 0) {
            throw new IllegalArgumentException("지정가는 현재가의 ±30% 안에서만 정할 수 있어요.");
        }

        if (mockTradeDao.countPendingOrders(userId) >= MAX_PENDING_ORDERS) {
            throw new IllegalArgumentException("미체결 주문은 " + MAX_PENDING_ORDERS + "개까지 걸 수 있어요. 기존 주문을 먼저 취소해주세요.");
        }

        // 2. 매수는 돈을 묶고, 매도는 팔 수 있는 수량 확인
        BigDecimal reserved = BigDecimal.ZERO;

        if ("BUY".equals(side)) {

            reserved = limitPrice.multiply(BigDecimal.valueOf(quantity));

            if (account.getCash().compareTo(reserved) < 0) {

                long max = account.getCash().divide(limitPrice, 0, RoundingMode.DOWN).longValue();
                throw new IllegalArgumentException("현금이 부족해요. 이 가격으로 최대 " + won(max) + "주까지 주문할 수 있어요.");

            }

            account.setCash(account.getCash().subtract(reserved));
            updateCash(userId, account.getCash());

        } else {

            checkSellable(userId, stock.getStockId(), quantity);

        }

        // 3. 주문 저장
        ZonedDateTime now = MockMarketHours.now();

        MockTradeVO order = new MockTradeVO();
        order.setUserId(userId);
        order.setStockId(stock.getStockId());
        order.setSide(side);
        order.setQuantity(quantity);
        order.setLimitPrice(limitPrice);
        order.setReservedAmount(reserved);
        order.setOrderDate(MockMarketHours.tradingDay(now));
        mockTradeDao.insertOrder(order);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderType", "LIMIT");
        result.put("orderId", order.getOrderId());
        result.put("symbol", stock.getSymbol());
        result.put("name", stock.getName());
        result.put("side", side);
        result.put("quantity", quantity);
        result.put("limitPrice", limitPrice);
        result.put("orderDate", order.getOrderDate().toString());

        // 4. 정규장인데 조건이 이미 맞으면 바로 체결 (실시간 가격일 때만)
        if (MockMarketHours.isOpen(now) && "LIVE".equals(stock.getPriceSource()) && crosses(side, current, limitPrice)) {

            order.setStatus("PENDING");
            MockTradeVO trade = fillLocked(account, order, stock);

            result.putAll(tradeResult(stock, trade));
            result.put("status", "FILLED");

        } else {

            result.put("status", "PENDING");
            result.put("cash", account.getCash());

        }

        return result;

    }

    // ============================================
    // 지정가 주문 취소 / 체결 / 만료
    // ============================================
    @Override
    @Transactional
    public void cancelOrder(Long userId, Long orderId) {

        MockTradeVO order = (orderId == null) ? null : mockTradeDao.selectOrder(orderId);

        if (order == null || !order.getUserId().equals(userId)) throw new IllegalArgumentException("주문을 찾을 수 없습니다.");

        if (!closeOrder(order.getUserId(), orderId, "CANCELED")) {
            throw new IllegalArgumentException("이미 체결됐거나 취소된 주문입니다.");
        }

    }

    @Override
    public List<Long> findFillableOrderIds() {

        List<MockTradeVO> orders = mockTradeDao.selectMatchableOrders(MockMarketHours.now().toLocalDate());
        if (orders.isEmpty()) return List.of();   // 대기 주문이 없으면 토스도 호출하지 않음

        Map<Long, MockTradeVO> prices = mockPriceService.getPrices(orders.stream().map(MockTradeVO::getStockId).toList());
        List<Long> ids = new ArrayList<>();

        for (MockTradeVO o : orders) {

            MockTradeVO p = prices.get(o.getStockId());

            // 실시간 가격일 때만 체결 (토스 실패로 종가를 쓰는 중이면 기다림)
            if (p != null && "LIVE".equals(p.getPriceSource()) && crosses(o.getSide(), p.getPrice(), o.getLimitPrice())) {
                ids.add(o.getOrderId());
            }

        }

        return ids;

    }

    @Override
    @Transactional
    public boolean fillOrder(Long orderId) {

        MockTradeVO first = mockTradeDao.selectOrder(orderId);
        if (first == null || !"PENDING".equals(first.getStatus())) return false;

        // 계좌 잠금 → 주문 상태 다시 확인 (그사이 취소됐을 수 있음)
        MockTradeVO account = mockTradeDao.selectAccountForUpdate(first.getUserId());
        MockTradeVO order = mockTradeDao.selectOrderForUpdate(orderId);
        if (order == null || !"PENDING".equals(order.getStatus())) return false;

        MockTradeVO stock = mockPriceService.getPrice(order.getStockId());
        if (stock == null || !"LIVE".equals(stock.getPriceSource())) return false;
        if (!crosses(order.getSide(), stock.getPrice(), order.getLimitPrice())) return false;

        fillLocked(account, order, stock);

        return true;

    }

    @Override
    public List<Long> findExpiredOrderIds(LocalDate upTo) {

        MockTradeVO param = new MockTradeVO();
        param.setOrderDate(upTo);

        return mockTradeDao.selectExpiredOrders(param).stream().map(MockTradeVO::getOrderId).toList();

    }

    @Override
    @Transactional
    public boolean expireOrder(Long orderId) {

        MockTradeVO order = mockTradeDao.selectOrder(orderId);

        return order != null && closeOrder(order.getUserId(), orderId, "EXPIRED");

    }

    // 지정가 체결 (계좌·주문이 잠긴 상태에서만 호출)
    private MockTradeVO fillLocked(MockTradeVO account, MockTradeVO order, MockTradeVO stock) {

        // 매수: 묶어둔 돈을 현금으로 되돌린 다음 실제 체결 금액만큼 뺌 (차액 환불)
        BigDecimal cashBefore = account.getCash().add(order.getReservedAmount());

        MockTradeVO trade = execute(order.getUserId(), stock, order.getSide(), order.getQuantity(),
                stock.getPrice(), cashBefore, order.getOrderId());

        account.setCash(trade.getCash());

        MockTradeVO status = new MockTradeVO();
        status.setOrderId(order.getOrderId());
        status.setStatus("FILLED");
        status.setTradeId(trade.getTradeId());
        mockTradeDao.updateOrderStatus(status);

        return trade;

    }

    // 취소 / 만료: 계좌 잠금 → 주문이 아직 대기 중이면 묶어둔 돈 돌려주고 상태 변경
    private boolean closeOrder(Long userId, Long orderId, String newStatus) {

        MockTradeVO account = mockTradeDao.selectAccountForUpdate(userId);
        MockTradeVO order = mockTradeDao.selectOrderForUpdate(orderId);

        if (order == null || !"PENDING".equals(order.getStatus())) return false;

        if (order.getReservedAmount().signum() > 0) {
            updateCash(userId, account.getCash().add(order.getReservedAmount()));
        }

        MockTradeVO status = new MockTradeVO();
        status.setOrderId(orderId);
        status.setStatus(newStatus);
        mockTradeDao.updateOrderStatus(status);

        return true;

    }

    // ============================================
    // 체결 공통: 보유 종목 · 현금 · 거래 내역 저장
    // - cashBefore: 이번 체결에 쓸 수 있는 현금 (지정가 매수면 묶어둔 돈 포함)
    // ============================================
    private MockTradeVO execute(Long userId, MockTradeVO stock, String side, int quantity,
                                BigDecimal price, BigDecimal cashBefore, Long orderId) {

        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal amount = price.multiply(qty);

        MockTradeVO key = new MockTradeVO();
        key.setUserId(userId);
        key.setStockId(stock.getStockId());
        MockTradeVO holding = mockTradeDao.selectHolding(key);

        int holdingQty = (holding == null) ? 0 : holding.getQuantity();
        BigDecimal cash;
        BigDecimal profit = null;

        if ("BUY".equals(side)) {

            // 평균 단가 다시 계산
            BigDecimal oldCost = (holding == null) ? BigDecimal.ZERO
                    : holding.getAvgPrice().multiply(BigDecimal.valueOf(holdingQty));
            int newQty = holdingQty + quantity;

            key.setQuantity(newQty);
            key.setAvgPrice(oldCost.add(amount).divide(BigDecimal.valueOf(newQty), 2, RoundingMode.HALF_UP));
            mockTradeDao.upsertHolding(key);

            cash = cashBefore.subtract(amount);

        } else {

            // 실현손익 (평균 단가는 그대로)
            if (holding == null || holdingQty < quantity) throw new IllegalStateException("보유 수량 부족: " + stock.getSymbol());

            profit = price.subtract(holding.getAvgPrice()).multiply(qty).setScale(0, RoundingMode.HALF_UP);

            if (holdingQty == quantity) {

                mockTradeDao.deleteHolding(key);   // 전량 매도

            } else {

                key.setQuantity(holdingQty - quantity);
                key.setAvgPrice(holding.getAvgPrice());
                mockTradeDao.upsertHolding(key);

            }

            cash = cashBefore.add(amount);

        }

        updateCash(userId, cash);

        MockTradeVO trade = new MockTradeVO();
        trade.setUserId(userId);
        trade.setStockId(stock.getStockId());
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setAmount(amount);
        trade.setProfit(profit);
        trade.setPriceSource(stock.getPriceSource());
        trade.setOrderId(orderId);
        mockTradeDao.insertTrade(trade);

        trade.setCash(cash);

        return trade;

    }

    // ============================================
    // 초기화
    // ============================================
    @Override
    @Transactional
    public void reset(Long userId) {

        ensureAccount(userId);
        mockTradeDao.selectAccountForUpdate(userId);   // 주문·체결과 겹치지 않게 잠금

        mockTradeDao.deleteAllOrders(userId);          // 거래 내역을 가리키고 있어서 먼저 삭제
        mockTradeDao.deleteAllHoldings(userId);
        mockTradeDao.deleteAllTrades(userId);
        mockTradeDao.deleteSnapshots(userId);          // 수익률 그래프도 처음부터
        mockTradeDao.resetAccount(userId);

    }

    // ============================================
    // 수익률 랭킹
    // ============================================
    @Override
    public Map<String, Object> getRanking(Long userId, int size, String period) {

        boolean week = "WEEK".equals(period);

        // 1. 회원별 총자산 · 수익률
        List<Map<String, Object>> rows = new ArrayList<>();

        for (MockTradeVO a : accountTotals()) {

            BigDecimal base = week ? a.getWeekBase() : a.getInitCash();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nickname", a.getNickname());
            row.put("totalAsset", a.getTotalAsset());
            row.put("returnRate", rate(a.getTotalAsset().subtract(base), base));
            row.put("tradeCount", a.getTradeCount());
            row.put("me", a.getUserId().equals(userId));
            rows.add(row);

        }

        // 2. 수익률 높은 순 (같으면 총자산 큰 순), 같은 수익률은 같은 순위
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
        result.put("period", week ? "WEEK" : "ALL");
        result.put("weekStart", MockMarketHours.weekStart(MockMarketHours.now().toLocalDate()).toString());
        result.put("ranking", rows.subList(0, Math.min(size, rows.size())));
        result.put("me", me);                    // 나 (TOP N 밖이어도 내 순위를 보여주려고)
        result.put("participants", rows.size());

        return result;

    }

    // 거래한 적 있는 계좌들의 지금 총자산 (= 현금 + 매수 주문에 묶인 돈 + 주식 평가금액)
    // - 랭킹(전체·이번 주)과 장 마감 기록에서 같이 씀
    private List<MockTradeVO> accountTotals() {

        List<MockTradeVO> holdings = mockTradeDao.selectRankingHoldings();
        Map<Long, MockTradeVO> prices = mockPriceService.getPrices(holdings.stream().map(MockTradeVO::getStockId).toList());

        // 회원별 주식 평가금액
        Map<Long, BigDecimal> stockValues = new HashMap<>();

        for (MockTradeVO h : holdings) {

            MockTradeVO p = prices.get(h.getStockId());
            if (p == null) continue;

            BigDecimal value = p.getPrice().multiply(BigDecimal.valueOf(h.getQuantity()));
            stockValues.merge(h.getUserId(), value, BigDecimal::add);

        }

        List<MockTradeVO> accounts = mockTradeDao.selectRankingAccounts(
                MockMarketHours.weekStart(MockMarketHours.now().toLocalDate()));

        for (MockTradeVO a : accounts) {

            BigDecimal pending = (a.getPendingCash() == null) ? BigDecimal.ZERO : a.getPendingCash();
            a.setTotalAsset(a.getCash().add(pending).add(stockValues.getOrDefault(a.getUserId(), BigDecimal.ZERO)));

        }

        return accounts;

    }

    // ============================================
    // 수익률 추이 (그래프)
    // ============================================
    @Override
    public Map<String, Object> getHistory(Long userId) {

        Map<String, Object> account = getAccount(userId);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) account.get("account");

        LocalDate today = MockMarketHours.now().toLocalDate();
        List<Map<String, Object>> points = new ArrayList<>();

        // 1. 장 마감 기록 (오늘 기록은 아래 "지금"으로 대신)
        for (MockTradeVO n : mockTradeDao.selectSnapshots(userId)) {

            if (!n.getSnapDate().isBefore(today)) continue;

            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", n.getSnapDate().toString());
            p.put("totalAsset", n.getTotalAsset());
            p.put("returnRate", n.getReturnRate());
            points.add(p);

        }

        // 2. 지금 (실시간 가격 기준)
        Map<String, Object> now = new LinkedHashMap<>();
        now.put("date", today.toString());
        now.put("totalAsset", summary.get("totalAsset"));
        now.put("returnRate", summary.get("returnRate"));
        now.put("live", true);
        points.add(now);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("initCash", summary.get("initCash"));
        result.put("points", points);

        return result;

    }

    // ============================================
    // 장 마감 기록 (스케줄러)
    // ============================================
    @Override
    public int snapshotAll(LocalDate day) {

        int saved = 0;

        for (MockTradeVO a : accountTotals()) {

            MockTradeVO snap = new MockTradeVO();
            snap.setUserId(a.getUserId());
            snap.setSnapDate(day);
            snap.setTotalAsset(a.getTotalAsset().setScale(0, RoundingMode.HALF_UP));
            snap.setReturnRate(rate(a.getTotalAsset().subtract(a.getInitCash()), a.getInitCash()));

            saved += mockTradeDao.upsertSnapshot(snap);

        }

        return saved;

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

    // 팔 수 있는 수량 확인 (보유 수량 - 이미 걸어둔 매도 주문 수량)
    private void checkSellable(Long userId, Long stockId, int quantity) {

        MockTradeVO key = new MockTradeVO();
        key.setUserId(userId);
        key.setStockId(stockId);

        MockTradeVO holding = mockTradeDao.selectHolding(key);
        int holdingQty = (holding == null) ? 0 : holding.getQuantity();
        int pending = mockTradeDao.sumPendingSellQuantity(key);
        int sellable = holdingQty - pending;

        if (holdingQty == 0) throw new IllegalArgumentException("보유하지 않은 종목입니다.");

        if (sellable < quantity) {
            throw new IllegalArgumentException(pending > 0
                    ? "팔 수 있는 수량이 부족해요. (보유 " + won(holdingQty) + "주 중 " + won(pending) + "주는 매도 주문 대기 중)"
                    : "보유 수량이 부족해요. (보유 " + won(holdingQty) + "주)");
        }

    }

    // 지정가 체결 조건 (매수: 현재가 ≤ 지정가 / 매도: 현재가 ≥ 지정가)
    private boolean crosses(String side, BigDecimal price, BigDecimal limitPrice) {

        return "BUY".equals(side) ? price.compareTo(limitPrice) <= 0 : price.compareTo(limitPrice) >= 0;

    }

    private void updateCash(Long userId, BigDecimal cash) {

        MockTradeVO vo = new MockTradeVO();
        vo.setUserId(userId);
        vo.setCash(cash);

        mockTradeDao.updateCash(vo);

    }

    // 체결 결과 (화면 안내용)
    private Map<String, Object> tradeResult(MockTradeVO stock, MockTradeVO trade) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tradeId", trade.getTradeId());
        result.put("symbol", stock.getSymbol());
        result.put("name", stock.getName());
        result.put("side", trade.getSide());
        result.put("quantity", trade.getQuantity());
        result.put("price", trade.getPrice());
        result.put("amount", trade.getAmount());
        result.put("profit", trade.getProfit());
        result.put("priceSource", trade.getPriceSource());
        result.put("cash", trade.getCash());

        return result;

    }

    // 주문 화면 종목 목록
    private List<Map<String, Object>> stockList(List<Long> stockIds, Map<Long, MockTradeVO> prices) {

        List<Map<String, Object>> list = new ArrayList<>();

        for (Long id : stockIds) {

            MockTradeVO s = prices.get(id);
            if (s == null) continue;

            list.add(quoteRow(s));

        }

        return list;

    }

    // 종목 하나의 현재가 정보 (주문 칸)
    private Map<String, Object> quoteRow(MockTradeVO s) {

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stockId", s.getStockId());
        row.put("symbol", s.getSymbol());
        row.put("name", s.getName());
        row.put("price", s.getPrice());
        row.put("priceSource", s.getPriceSource());
        row.put("priceTime", s.getPriceTime());

        return row;

    }

    @Override
    public Map<String, Object> getQuote(String symbol) {

        Long stockId = (symbol == null) ? null : mockTradeDao.selectStockIdBySymbol(symbol);
        MockTradeVO s = mockPriceService.getPrice(stockId);

        return (s == null) ? null : quoteRow(s);

    }

    // 비율 (%) 소수점 2자리, 기준이 0이면 0
    private BigDecimal rate(BigDecimal part, BigDecimal base) {

        if (base == null || base.signum() == 0) return BigDecimal.ZERO.setScale(2);

        return part.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);

    }

    // 1,234 형식
    private String won(long value) {

        return String.format("%,d", value);

    }
}
