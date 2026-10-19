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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 현재가 Service (모의투자 · 가격 알림 공통)
 * - 전 종목(약 3,000개)을 매번 조회하지 않고, 지금 필요한 종목만 조회
 *   (보유 종목, 미체결 주문, 알림, 주문 칸에서 고른 종목, 주요 종목)
 * - 토스 현재가는 한 번에 최대 200종목 → 200개씩 묶어서 호출
 * - 종목마다 10초 동안은 같은 가격을 재사용 → 화면 새로고침, 랭킹 조회가 많아도 토스 호출은 10초에 1번
 * - 토스 호출이 실패하면(키 없음, 허용 IP 문제 등) T_STOCK에 복사해 둔 최근 종가로 대신 → 화면에 "종가 기준" 표시
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

    // 종목 ID → (종목 + 현재가, 받은 시각)
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(MockTradeVO stock, Instant at) {
        boolean fresh() {
            return Instant.now().isBefore(at.plusSeconds(CACHE_SECONDS));
        }
    }

    // 종목 하나 (가격이 없으면 null = 주문 불가)
    public MockTradeVO getPrice(Long stockId) {

        if (stockId == null) return null;

        return getPrices(List.of(stockId)).get(stockId);

    }

    // 여러 종목 (가격이 하나도 없는 종목은 빠짐)
    public synchronized Map<Long, MockTradeVO> getPrices(Collection<Long> stockIds) {

        Map<Long, MockTradeVO> result = new LinkedHashMap<>();
        List<Long> stale = new ArrayList<>();

        for (Long id : new LinkedHashSet<>(stockIds)) {

            if (id == null) continue;

            Cached c = cache.get(id);

            if (c != null && c.fresh()) result.put(id, c.stock());
            else stale.add(id);

        }

        if (stale.isEmpty()) return result;

        // 오래됐거나 처음 보는 종목만 새로
        List<MockTradeVO> stocks = mockTradeDao.selectStocksByIds(stale);
        Map<String, Map<String, Object>> live = fetchLivePrices(stocks);
        Instant now = Instant.now();

        for (MockTradeVO s : stocks) {

            Map<String, Object> row = live.get(s.getSymbol());
            BigDecimal lastPrice = (row == null) ? null : toDecimal(row.get("lastPrice"));

            if (lastPrice != null && lastPrice.signum() > 0) {

                // 1순위: 토스 현재가
                s.setPrice(lastPrice.setScale(0, RoundingMode.HALF_UP));
                s.setPriceSource("LIVE");
                s.setPriceTime(row.get("timestamp") == null ? null : String.valueOf(row.get("timestamp")));

            } else if (s.getClosePrice() != null) {

                // 2순위: 최근 종가
                s.setPrice(s.getClosePrice().setScale(0, RoundingMode.HALF_UP));
                s.setPriceSource("CLOSE");
                s.setPriceTime(s.getTradeDate() == null ? null : s.getTradeDate().toString());

            } else {

                continue;   // 가격이 없으면 주문 불가

            }

            cache.put(s.getStockId(), new Cached(s, now));
            result.put(s.getStockId(), s);

        }

        return result;

    }

    // 토스 현재가 200종목씩 조회 → 종목코드별로 (실패하면 빈 Map → 종가 사용)
    private Map<String, Map<String, Object>> fetchLivePrices(List<MockTradeVO> stocks) {

        if (stocks.isEmpty() || !tossApiClient.isConfigured()) return Map.of();

        Map<String, Map<String, Object>> bySymbol = new HashMap<>();

        for (int from = 0; from < stocks.size(); from += TossApiClient.MAX_SYMBOLS) {

            List<MockTradeVO> chunk = stocks.subList(from, Math.min(from + TossApiClient.MAX_SYMBOLS, stocks.size()));

            try {

                String symbols = String.join(",", chunk.stream().map(MockTradeVO::getSymbol).toList());

                for (Map<String, Object> row : tossApiClient.getPrices(symbols)) {
                    bySymbol.put(String.valueOf(row.get("symbol")), row);
                }

            } catch (Exception e) {

                log.warn("현재가 조회 실패 → 최근 종가로 대신합니다: {}", e.getMessage());

            }

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
