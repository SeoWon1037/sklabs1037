package com.sklabs1037.sklabs1037.stock.service;

import com.sklabs1037.sklabs1037.client.TossApiClient;
import com.sklabs1037.sklabs1037.stock.dao.StockCollectDao;
import com.sklabs1037.sklabs1037.stock.vo.CollectResult;
import com.sklabs1037.sklabs1037.stock.vo.StockCollectVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 시세 수집 Service 구현 (데이터 파이프라인)
 *
 *   0. 종목 목록 : 토스 /api/v1/stocks/all → 코스피·코스닥 주식(보통주·우선주) + ETF 약 3,000개를 T_STOCK에 UPSERT
 *   Extract   : 토스증권 API 일봉 조회 (한 번에 한 종목)
 *   Transform : 문자열 가격 → BigDecimal, ISO 시간 → 날짜
 *   Load      : T_STOCK_PRICE UPSERT (다시 받아도 중복 없음) + T_STOCK 최신 종가 복사
 *   + 상장 주식 수(T_STOCK.SHARES_OUTSTANDING)도 200개씩 묶어서 갱신 → 시가총액 계산용
 *
 * - 종목 하나가 실패해도 나머지는 계속 수집 (종목별 try/catch)
 * - 일부러 @Transactional을 쓰지 않음 (한 종목 실패로 전체가 롤백되지 않게)
 * - 종목 사이에 stock.collect.delay-ms만큼 쉬어서 토스 호출 제한에 덜 걸리게 (걸리면 TossApiClient가 쉬고 재시도)
 *   → 3,000종목이면 10분 남짓
 * - DB 저장은 묶어서 (종목 하나의 일봉 전체 = SQL 1번, 종목 목록 500개 = SQL 1번)
 *   → DB가 멀리 있어도(로컬 PC → RDS) 왕복 횟수가 적어서 빠름
 */
@Slf4j
@Service
public class StockCollectServiceImpl implements IStockCollectService {

    // 받을 시장 / 종목 종류 (토스 securityType)
    private static final List<String> MARKETS = List.of("KOSPI", "KOSDAQ");
    private static final Set<String> SECURITY_TYPES = Set.of("STOCK", "ETF");

    // 진행 상황 로그 간격 (종목 수)
    private static final int PROGRESS_EVERY = 300;

    // 종목 목록 한 번에 저장할 개수
    private static final int MASTER_BATCH = 500;

    @Autowired
    private TossApiClient tossApiClient;

    @Autowired
    private StockCollectDao stockCollectDao;

    // 종목 사이 쉬는 시간 (밀리초)
    @Value("${stock.collect.delay-ms:150}")
    private long delayMs;

    // 처음 보는 종목을 열었을 때 받아올 일수
    @Value("${stock.collect.initial-count:200}")
    private int initialCount;

    // ============================================
    // 0. 종목 목록 동기화
    // ============================================
    @Override
    public int syncStockMaster() {

        if (!tossApiClient.isConfigured()) return 0;

        // 종목코드별로 모으기 (같은 SQL 안에 같은 종목코드가 두 번 있으면 안 됨)
        Map<String, StockCollectVO> bySymbol = new LinkedHashMap<>();

        for (String market : MARKETS) {

            for (Map<String, Object> row : tossApiClient.listStocks(market)) {

                String type = String.valueOf(row.get("securityType"));
                if (!SECURITY_TYPES.contains(type)) continue;   // 리츠·ETN·워런트 등은 제외

                String symbol = String.valueOf(row.get("symbol"));
                String name = String.valueOf(row.get("name"));
                if (symbol.isBlank() || "null".equals(symbol) || name.isBlank()) continue;

                StockCollectVO vo = new StockCollectVO();
                vo.setSymbol(symbol);
                vo.setName(name);
                vo.setMarket(market);
                vo.setSecurityType(type);

                bySymbol.put(symbol, vo);

            }

        }

        // 500개씩 한 번에 저장
        List<StockCollectVO> all = new ArrayList<>(bySymbol.values());
        int saved = 0;

        for (int from = 0; from < all.size(); from += MASTER_BATCH) {
            saved += stockCollectDao.upsertStockMasterBatch(all.subList(from, Math.min(from + MASTER_BATCH, all.size())));
        }

        log.info("종목 목록 동기화: {}종목", saved);

        return saved;

    }

    // ============================================
    // 1. 정기 수집 (평일 20:30)
    // ============================================
    @Override
    public CollectResult collectAll(int count) {

        if (!tossApiClient.isConfigured()) {
            throw new IllegalStateException("토스 API 키가 없어 시세 수집을 할 수 없습니다.");
        }

        // 새로 상장한 종목도 같이 받게 목록부터 (실패해도 기존 종목은 수집)
        try {
            syncStockMaster();
        } catch (Exception e) {
            log.warn("종목 목록 동기화 실패: {}", e.getMessage());
        }

        CollectResult result = collectStocks(stockCollectDao.selectTargetStocks(), count);

        // 상장 주식 수는 실패해도 시세 수집 결과에 영향 없게 따로
        try {
            updateSharesOutstanding();
        } catch (Exception e) {
            log.warn("상장 주식 수 갱신 실패: {}", e.getMessage());
        }

        return result;

    }

    @Override
    public boolean isEmpty() {

        return stockCollectDao.countPrices() == 0;

    }

    // ============================================
    // 2. 이력 보충 (서버 시작 시, 새 종목)
    // ============================================
    @Override
    public CollectResult collectMissingHistory(int minRows, int count) {

        if (!tossApiClient.isConfigured()) {
            log.warn("토스 API 키가 없어 시세 이력 보충을 건너뜀");
            return CollectResult.EMPTY;
        }

        List<StockCollectVO> targets = stockCollectDao.selectStocksNeedingHistory(minRows);

        if (targets.isEmpty()) {
            log.info("시세 이력 보충: 대상 없음");
            return CollectResult.EMPTY;
        }

        log.info("시세 이력 보충 대상: {}종목 ({}일치)", targets.size(), count);

        return collectStocks(targets, count);

    }

    // ============================================
    // 3. 한 종목 바로 받기 (아직 시세가 없는 종목을 화면에서 열었을 때)
    // ============================================
    @Override
    public int collectHistoryNow(Long stockId, String symbol) {

        if (!tossApiClient.isConfigured()) return 0;

        StockCollectVO stock = new StockCollectVO();
        stock.setStockId(stockId);
        stock.setSymbol(symbol);

        int n = collectOne(stock, initialCount);
        stockCollectDao.updateLatestPrice(stockId);

        log.info("시세 즉시 수집: {} → {}건", symbol, n);

        return n;

    }

    // ============================================
    // 4. 상장 주식 수 (200종목씩 묶어서)
    // ============================================
    @Override
    public int updateSharesOutstanding() {

        if (!tossApiClient.isConfigured()) return 0;

        List<StockCollectVO> stocks = stockCollectDao.selectTargetStocks();
        int updated = 0;

        for (int from = 0; from < stocks.size(); from += TossApiClient.MAX_SYMBOLS) {

            List<StockCollectVO> chunk = stocks.subList(from, Math.min(from + TossApiClient.MAX_SYMBOLS, stocks.size()));

            // 1. 200종목 한 번에 조회 → 종목코드별 상장 주식 수
            String symbols = String.join(",", chunk.stream().map(StockCollectVO::getSymbol).toList());
            Map<String, Long> shares = new HashMap<>();

            try {

                for (Map<String, Object> row : tossApiClient.getStocks(symbols)) {

                    Object value = row.get("sharesOutstanding");
                    if (value == null) continue;

                    shares.put(String.valueOf(row.get("symbol")), new BigDecimal(String.valueOf(value)).longValue());

                }

            } catch (Exception e) {

                log.warn("상장 주식 수 조회 실패 ({}~{}번째): {}", from, from + chunk.size(), e.getMessage());
                continue;

            }

            // 2. 200개 한 번에 저장
            List<StockCollectVO> rows = new ArrayList<>();

            for (StockCollectVO stock : chunk) {

                Long n = shares.get(stock.getSymbol());
                if (n == null || n <= 0) continue;

                stock.setSharesOutstanding(n);
                rows.add(stock);

            }

            if (!rows.isEmpty()) updated += stockCollectDao.updateSharesBatch(rows);

            pause();

        }

        log.info("상장 주식 수 갱신: {}종목", updated);

        return updated;

    }

    // ============================================
    // 6. 놓친 수집 따라잡기
    // ============================================
    @Override
    public CollectResult collectStale(int count) {

        if (!tossApiClient.isConfigured()) return CollectResult.EMPTY;

        LocalDate expected = lastClosedTradingDay();
        List<StockCollectVO> targets = stockCollectDao.selectStaleStocks(expected);

        if (targets.isEmpty()) {
            log.info("놓친 시세 수집: 없음 (기준 {})", expected);
            return CollectResult.EMPTY;
        }

        log.info("놓친 시세 수집: {}종목 (기준 {}, 최근 {}일치)", targets.size(), expected, count);

        return collectStocks(targets, count);

    }

    // 종목 화면에서 연 종목: 같은 종목은 10분에 한 번만 확인
    private final Map<Long, java.time.Instant> refreshedAt = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public boolean refreshIfStale(Long stockId, String symbol, LocalDate lastDate) {

        if (!tossApiClient.isConfigured() || stockId == null) return false;
        if (lastDate != null && !lastDate.isBefore(lastClosedTradingDay())) return false;

        java.time.Instant now = java.time.Instant.now();
        java.time.Instant before = refreshedAt.get(stockId);
        if (before != null && now.isBefore(before.plusSeconds(600))) return false;
        refreshedAt.put(stockId, now);

        StockCollectVO stock = new StockCollectVO();
        stock.setStockId(stockId);
        stock.setSymbol(symbol);

        int n = collectOne(stock, 5);
        stockCollectDao.updateLatestPrice(stockId);

        log.info("종목 화면에서 최신 시세 보충: {} → {}건", symbol, n);

        return n > 0;

    }

    // 장 마감(15:30) 기준 가장 최근 거래일 (주말 제외, 공휴일은 모름 → 공휴일엔 받아도 새 데이터가 없을 뿐)
    private LocalDate lastClosedTradingDay() {

        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        LocalDate day = now.toLocalDate();

        if (now.toLocalTime().isBefore(java.time.LocalTime.of(15, 40))) day = day.minusDays(1);

        while (day.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || day.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            day = day.minusDays(1);
        }

        return day;

    }

    // ============================================
    // 5. 종가가 없는 종목에 현재가 임시로 (200개씩)
    // ============================================
    @Override
    public int fillMissingPrices() {

        if (!tossApiClient.isConfigured()) return 0;

        List<StockCollectVO> stocks = stockCollectDao.selectStocksWithoutPrice();
        int filled = 0;

        for (int from = 0; from < stocks.size(); from += TossApiClient.MAX_SYMBOLS) {

            List<StockCollectVO> chunk = stocks.subList(from, Math.min(from + TossApiClient.MAX_SYMBOLS, stocks.size()));
            Map<String, StockCollectVO> bySymbol = new HashMap<>();
            chunk.forEach(s -> bySymbol.put(s.getSymbol(), s));

            List<StockCollectVO> rows = new ArrayList<>();

            try {

                String symbols = String.join(",", bySymbol.keySet());

                for (Map<String, Object> row : tossApiClient.getPrices(symbols)) {

                    StockCollectVO stock = bySymbol.get(String.valueOf(row.get("symbol")));
                    Object price = row.get("lastPrice");
                    if (stock == null || price == null) continue;

                    BigDecimal value = new BigDecimal(String.valueOf(price));
                    if (value.signum() <= 0) continue;

                    StockCollectVO vo = new StockCollectVO();
                    vo.setStockId(stock.getStockId());
                    vo.setClosePrice(value);
                    vo.setTradeDate(toDate(row.get("timestamp")));
                    rows.add(vo);

                }

            } catch (Exception e) {

                log.warn("현재가 임시 채우기 실패 ({}~{}번째): {}", from, from + chunk.size(), e.getMessage());
                continue;

            }

            if (!rows.isEmpty()) filled += stockCollectDao.fillMissingLastPrice(rows);

            pause();

        }

        log.info("현재가 임시 채우기: {}종목", filled);

        return filled;

    }

    // ============================================
    // 공통
    // ============================================

    // 여러 종목 수집 (종목 사이에 쉬면서)
    private CollectResult collectStocks(List<StockCollectVO> stocks, int count) {

        int saved = 0;
        int failed = 0;
        String firstError = null;

        for (int i = 0; i < stocks.size(); i++) {

            StockCollectVO stock = stocks.get(i);

            try {

                saved += collectOne(stock, count);
                stockCollectDao.updateLatestPrice(stock.getStockId());

            } catch (Exception e) {

                failed++;
                if (firstError == null) firstError = stock.getSymbol() + " " + e.getMessage();

                // 실패가 많으면 로그가 넘치니 처음 몇 개만 자세히
                if (failed <= 5) log.error("시세 수집 실패: {} {}", stock.getSymbol(), stock.getName(), e);
                else log.warn("시세 수집 실패: {} {} ({})", stock.getSymbol(), stock.getName(), e.getMessage());

            }

            if ((i + 1) % PROGRESS_EVERY == 0) {
                log.info("시세 수집 진행: {}/{}종목", i + 1, stocks.size());
            }

            pause();

        }

        log.info("시세 수집 완료: 종목 {}개, 저장 {}건, 실패 {}종목", stocks.size(), saved, failed);

        return new CollectResult(stocks.size(), saved, failed, firstError);

    }

    // 종목 하나 수집
    private int collectOne(StockCollectVO stock, int count) {

        List<Map<String, Object>> candles = tossApiClient.getDailyCandles(stock.getSymbol(), count);
        if (candles == null) return 0;

        // 날짜별로 모아서 SQL 1번에 저장
        Map<LocalDate, StockCollectVO> byDate = new LinkedHashMap<>();

        for (Map<String, Object> c : candles) {

            StockCollectVO vo = new StockCollectVO();
            vo.setStockId(stock.getStockId());
            vo.setTradeDate(OffsetDateTime.parse(String.valueOf(c.get("timestamp"))).toLocalDate());
            vo.setOpenPrice(toDecimal(c.get("openPrice")));
            vo.setHighPrice(toDecimal(c.get("highPrice")));
            vo.setLowPrice(toDecimal(c.get("lowPrice")));
            vo.setClosePrice(toDecimal(c.get("closePrice")));
            vo.setVolume(c.get("volume") == null ? 0L : toDecimal(c.get("volume")).longValue());

            byDate.put(vo.getTradeDate(), vo);

        }

        if (byDate.isEmpty()) return 0;

        stockCollectDao.upsertStockPriceBatch(new ArrayList<>(byDate.values()));

        return byDate.size();

    }

    // 토스 호출 사이 쉬기
    private void pause() {

        if (delayMs <= 0) return;

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

    // 토스 시각 → 날짜 (없거나 이상하면 오늘)
    private LocalDate toDate(Object timestamp) {

        try {
            return OffsetDateTime.parse(String.valueOf(timestamp)).toLocalDate();
        } catch (Exception e) {
            return LocalDate.now(ZoneId.of("Asia/Seoul"));
        }

    }

    // "217000" → 217000 (토스 API는 가격을 문자열로 줌)
    private BigDecimal toDecimal(Object value) {

        return new BigDecimal(String.valueOf(value));

    }
}
