package com.sklabs1037.sklabs1037.mock.service;

import com.sklabs1037.sklabs1037.client.TossApiClient;
import com.sklabs1037.sklabs1037.mock.dao.MockTradeDao;
import com.sklabs1037.sklabs1037.mock.vo.MockTradeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * 모의투자 현재가 Service
 * - 토스 현재가 API를 한 번에 전 종목 호출 (symbols=005930,000660,...)
 * - 10초 동안은 같은 가격을 재사용 → 화면 새로고침, 랭킹 조회가 많아도 토스 호출은 10초에 1번
 * - 토스 호출이 실패하면(키 없음, 허용 IP 문제 등) DB의 최근 종가로 대신 → 주문은 계속 가능, 화면에 "종가 기준" 표시
 *
 * ※ 돌려주는 객체는 여러 요청이 같이 쓰는 캐시라서 꺼내 쓰는 쪽에서 값을 바꾸면 안 됨 (읽기만)
 */
@Slf4j
@Service
public class MockPriceService {

    // 현재가 재사용 시간 (초)
    private static final long CACHE_SECONDS = 10;

    @Autowired
    private TossApiClient tossApiClient;

    @Autowired
    private MockTradeDao mockTradeDao;

    // 종목 ID → 종목 + 현재가
    private Map<Long, MockTradeVO> cache = Map.of();
    private Instant cachedAt = Instant.EPOCH;

    // 전 종목 현재가 (가격이 하나도 없는 종목은 빠짐 = 주문 불가)
    public synchronized Map<Long, MockTradeVO> getPrices() {

        if (!cache.isEmpty() && Instant.now().isBefore(cachedAt.plusSeconds(CACHE_SECONDS))) {

            return cache;

        }

        List<MockTradeVO> stocks = mockTradeDao.selectStocks();
        Map<String, Map<String, Object>> live = fetchLivePrices(stocks);

        Map<Long, MockTradeVO> result = new LinkedHashMap<>();

        for (MockTradeVO s : stocks) {

            Map<String, Object> row = live.get(s.getSymbol());
            BigDecimal lastPrice = (row == null) ? null : toDecimal(row.get("lastPrice"));

            if (lastPrice != null && lastPrice.signum() > 0) {

                // 1순위: 토스 현재가
                s.setPrice(lastPrice.setScale(0, RoundingMode.HALF_UP));
                s.setPriceSource("LIVE");
                s.setPriceTime(row.get("timestamp") == null ? null : String.valueOf(row.get("timestamp")));

            } else if (s.getClosePrice() != null) {

                // 2순위: DB 최근 종가
                s.setPrice(s.getClosePrice().setScale(0, RoundingMode.HALF_UP));
                s.setPriceSource("CLOSE");
                s.setPriceTime(s.getTradeDate() == null ? null : s.getTradeDate().toString());

            } else {

                continue;   // 가격이 없으면 주문 목록에서 제외

            }

            result.put(s.getStockId(), s);

        }

        cache = Collections.unmodifiableMap(result);
        cachedAt = Instant.now();

        return cache;

    }

    // 토스 현재가 한 번에 조회 → 종목코드별로 (실패하면 빈 Map → 종가 사용)
    private Map<String, Map<String, Object>> fetchLivePrices(List<MockTradeVO> stocks) {

        if (stocks.isEmpty() || !tossApiClient.isConfigured()) return Map.of();

        Map<String, Map<String, Object>> bySymbol = new HashMap<>();

        try {

            String symbols = String.join(",", stocks.stream().map(MockTradeVO::getSymbol).toList());

            for (Map<String, Object> row : tossApiClient.getPrices(symbols)) {

                String symbol = String.valueOf(row.get("symbol"));

                // "A005930" 처럼 와도 뒤 6자리로 맞춤
                if (symbol.length() > 6) symbol = symbol.substring(symbol.length() - 6);

                bySymbol.put(symbol, row);

            }

        } catch (Exception e) {

            log.warn("모의투자 현재가 조회 실패 → 최근 종가로 대신합니다: {}", e.getMessage());

        }

        return bySymbol;

    }

    // 문자/숫자 어떤 형태로 와도 BigDecimal로 (토스는 문자로 줌)
    private BigDecimal toDecimal(Object value) {

        try {

            return (value == null) ? null : new BigDecimal(String.valueOf(value));

        } catch (NumberFormatException e) {

            return null;

        }

    }
}
