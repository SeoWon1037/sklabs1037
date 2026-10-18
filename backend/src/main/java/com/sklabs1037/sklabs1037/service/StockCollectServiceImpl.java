package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.client.TossApiClient;
import com.sklabs1037.sklabs1037.dao.StockCollectDao;
import com.sklabs1037.sklabs1037.vo.StockCollectVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 시세 수집 Service 구현 (데이터 파이프라인)
 *   추출(Extract)  : 토스 일봉 API 호출
 *   변환(Transform): 문자 → 숫자, timestamp → 날짜
 *   적재(Load)     : T_STOCK_PRICE에 UPSERT
 *
 * ※ @Transactional을 일부러 안 붙임
 *   - 외부 API 호출이 길어질 수 있어서, 한 종목이 실패해도 다른 종목은 저장되게 (한 줄씩 바로 저장)
 */
@Slf4j
@Service
public class StockCollectServiceImpl implements IStockCollectService {

    @Autowired
    private StockCollectDao stockCollectDao;

    @Autowired
    private TossApiClient tossApiClient;

    // 전체 종목 시세 수집
    @Override
    public int collectAll(int count) {

        // 토스 키가 없으면 (CI 등) 건너뛰기
        if (!tossApiClient.isConfigured()) {

            log.warn("토스 API 키가 없어 시세 수집을 건너뜁니다.");
            return 0;

        }

        List<StockCollectVO> stocks = stockCollectDao.selectTargetStocks();
        int saved = 0;

        for (StockCollectVO stock : stocks) {

            // 한 종목이 실패해도 나머지는 계속 수집
            try {

                saved += collectOne(stock, count);

            } catch (Exception e) {

                log.error("시세 수집 실패: {} {}", stock.getSymbol(), stock.getName(), e);

            }

        }

        log.info("시세 수집 완료: 종목 {}개, 저장 {}건", stocks.size(), saved);

        return saved;

    }

    // 시세가 비어 있는지
    @Override
    public boolean isEmpty() {

        return stockCollectDao.countPrices() == 0;

    }

    // ============================================
    // 한 종목 수집
    // ============================================
    private int collectOne(StockCollectVO stock, int count) {

        // 1. 추출: 토스 일봉 API
        List<Map<String, Object>> candles = tossApiClient.getDailyCandles(stock.getSymbol(), count);

        int saved = 0;

        for (Map<String, Object> candle : candles) {

            // 2. 변환: 문자("217000") → 숫자, "2026-10-16T00:00:00.000+09:00" → 2026-10-16
            StockCollectVO price = new StockCollectVO();
            price.setStockId(stock.getStockId());
            price.setTradeDate(OffsetDateTime.parse((String) candle.get("timestamp")).toLocalDate());
            price.setOpenPrice(toDecimal(candle.get("openPrice")));
            price.setHighPrice(toDecimal(candle.get("highPrice")));
            price.setLowPrice(toDecimal(candle.get("lowPrice")));
            price.setClosePrice(toDecimal(candle.get("closePrice")));
            price.setVolume(toDecimal(candle.get("volume")).longValue());

            // 3. 적재: UPSERT
            stockCollectDao.upsertStockPrice(price);
            saved++;

        }

        log.info("시세 수집: {} {} → {}건", stock.getSymbol(), stock.getName(), saved);

        return saved;

    }

    // 문자/숫자 어떤 형태로 와도 BigDecimal로 (토스는 문자로 줌)
    private BigDecimal toDecimal(Object value) {

        return (value == null) ? null : new BigDecimal(String.valueOf(value));

    }
}