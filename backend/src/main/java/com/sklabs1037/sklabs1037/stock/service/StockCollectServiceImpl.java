package com.sklabs1037.sklabs1037.stock.service;

import com.sklabs1037.sklabs1037.client.TossApiClient;
import com.sklabs1037.sklabs1037.stock.dao.StockCollectDao;
import com.sklabs1037.sklabs1037.stock.vo.StockCollectVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 시세 수집 Service 구현 (데이터 파이프라인)
 *
 *   Extract   : 토스증권 API 일봉 조회
 *   Transform : 문자열 가격 → BigDecimal, ISO 시간 → 날짜
 *   Load      : T_STOCK_PRICE UPSERT (다시 받아도 중복 없음)
 *
 * - 종목 하나가 실패해도 나머지는 계속 수집 (종목별 try/catch)
 * - 일부러 @Transactional을 쓰지 않음 (한 종목 실패로 전체가 롤백되지 않게)
 */
@Slf4j
@Service
public class StockCollectServiceImpl implements IStockCollectService {

    @Autowired
    private TossApiClient tossApiClient;

    @Autowired
    private StockCollectDao stockCollectDao;

    @Override
    public int collectAll(int count) {

        if (!tossApiClient.isConfigured()) {
            log.warn("토스 API 키가 없어 시세 수집을 건너뜀");
            return 0;
        }

        return collectStocks(stockCollectDao.selectTargetStocks(), count);

    }

    @Override
    public boolean isEmpty() {

        return stockCollectDao.countPrices() == 0;

    }

    @Override
    public int collectMissingHistory(int minRows, int count) {

        if (!tossApiClient.isConfigured()) {
            log.warn("토스 API 키가 없어 시세 이력 보충을 건너뜀");
            return 0;
        }

        List<StockCollectVO> targets = stockCollectDao.selectStocksNeedingHistory(minRows);

        if (targets.isEmpty()) {
            log.info("시세 이력 보충: 대상 없음");
            return 0;
        }

        log.info("시세 이력 보충 대상: {}종목 ({}일치)", targets.size(), count);

        return collectStocks(targets, count);

    }

    // 여러 종목 수집
    private int collectStocks(List<StockCollectVO> stocks, int count) {

        int saved = 0;

        for (StockCollectVO stock : stocks) {

            try {

                int n = collectOne(stock, count);
                saved += n;
                log.info("시세 수집: {} {} → {}건", stock.getSymbol(), stock.getName(), n);

            } catch (Exception e) {

                log.error("시세 수집 실패: {} {}", stock.getSymbol(), stock.getName(), e);

            }

        }

        log.info("시세 수집 완료: 종목 {}개, 저장 {}건", stocks.size(), saved);

        return saved;

    }

    // 종목 하나 수집
    private int collectOne(StockCollectVO stock, int count) {

        List<Map<String, Object>> candles = tossApiClient.getDailyCandles(stock.getSymbol(), count);
        int saved = 0;

        for (Map<String, Object> c : candles) {

            StockCollectVO vo = new StockCollectVO();
            vo.setStockId(stock.getStockId());
            vo.setTradeDate(OffsetDateTime.parse(String.valueOf(c.get("timestamp"))).toLocalDate());
            vo.setOpenPrice(toDecimal(c.get("openPrice")));
            vo.setHighPrice(toDecimal(c.get("highPrice")));
            vo.setLowPrice(toDecimal(c.get("lowPrice")));
            vo.setClosePrice(toDecimal(c.get("closePrice")));
            vo.setVolume(toDecimal(c.get("volume")).longValue());

            stockCollectDao.upsertStockPrice(vo);
            saved++;

        }

        return saved;

    }

    // "217000" → 217000 (토스 API는 가격을 문자열로 줌)
    private BigDecimal toDecimal(Object value) {

        return new BigDecimal(String.valueOf(value));

    }
}