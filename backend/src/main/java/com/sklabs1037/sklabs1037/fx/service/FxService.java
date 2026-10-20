package com.sklabs1037.sklabs1037.fx.service;

import com.sklabs1037.sklabs1037.client.FrankfurterClient;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 💱 오늘의 환율 (메인 "오늘의 환율" 탭)
 *
 * - 달러 기준 한 번에 받아서 원화로 바꿈: 1 엔 = (달러→원) / (달러→엔)
 * - 엔은 100엔 기준 (은행 고시와 같게)
 * - 30분 캐시, 외부 API가 실패하면 마지막으로 받은 값을 그대로 (stale 표시)
 */
@Slf4j
@Service
public class FxService {

    private static final long TTL_MS = 30 * 60 * 1000L;
    private static final int HISTORY_DAYS = 10;

    /** 보여줄 통화 */
    record Currency(String code, String flag, String name, int unit) {
    }

    static final List<Currency> CURRENCIES = List.of(
            new Currency("USD", "🇺🇸", "미국 달러", 1),
            new Currency("JPY", "🇯🇵", "일본 엔", 100),
            new Currency("EUR", "🇪🇺", "유로", 1),
            new Currency("CNY", "🇨🇳", "중국 위안", 1),
            new Currency("GBP", "🇬🇧", "영국 파운드", 1)
    );

    @Autowired
    private FrankfurterClient frankfurterClient;

    private volatile Map<String, Object> cache;
    private volatile long cachedAt;

    public Map<String, Object> today() {

        long now = System.currentTimeMillis();
        Map<String, Object> c = cache;
        if (c != null && now - cachedAt < TTL_MS) return c;

        synchronized (this) {

            if (cache != null && System.currentTimeMillis() - cachedAt < TTL_MS) return cache;

            try {

                List<String> quotes = new ArrayList<>(List.of("KRW"));
                CURRENCIES.stream().map(Currency::code).filter(x -> !"USD".equals(x)).forEach(quotes::add);

                List<Map<String, Object>> rows = frankfurterClient.series("USD", quotes,
                        MockMarketHours.now().toLocalDate().minusDays(HISTORY_DAYS * 2L));

                Map<String, Object> result = compute(rows);
                result.put("stale", false);
                cache = result;
                cachedAt = System.currentTimeMillis();
                return result;

            } catch (Exception e) {

                log.warn("환율 불러오기 실패: {}", e.getMessage());
                if (cache == null) throw new IllegalStateException("환율을 불러오지 못했어요.");

                Map<String, Object> stale = new LinkedHashMap<>(cache);
                stale.put("stale", true);
                cachedAt = System.currentTimeMillis() - TTL_MS + 5 * 60 * 1000L;   // 5분 뒤 다시 시도
                cache = stale;
                return stale;

            }

        }

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================

    // [{date, quote, rate}] (달러 기준) → 통화마다 원화 환율 · 전날 대비 · 최근 흐름
    static Map<String, Object> compute(List<Map<String, Object>> rows) {

        // 날짜 → (통화 → 달러 기준 환율)
        TreeMap<String, Map<String, Double>> byDate = new TreeMap<>();
        for (Map<String, Object> r : rows) {
            if (r.get("date") == null || r.get("quote") == null || !(r.get("rate") instanceof Number n)) continue;
            byDate.computeIfAbsent(String.valueOf(r.get("date")), k -> new HashMap<>()).put(String.valueOf(r.get("quote")), n.doubleValue());
        }

        // 원화 환율이 있는 날만
        List<String> dates = byDate.keySet().stream().filter(d -> byDate.get(d).get("KRW") != null).toList();
        if (dates.isEmpty()) throw new IllegalStateException("환율 데이터가 비어 있어요.");
        if (dates.size() > HISTORY_DAYS) dates = dates.subList(dates.size() - HISTORY_DAYS, dates.size());

        List<Map<String, Object>> rates = new ArrayList<>();

        for (Currency c : CURRENCIES) {

            List<Double> history = new ArrayList<>();
            for (String d : dates) {
                Double v = krw(byDate.get(d), c);
                if (v != null) history.add(round(v, 2));
            }
            if (history.isEmpty()) continue;

            double rate = history.get(history.size() - 1);
            Double prev = history.size() >= 2 ? history.get(history.size() - 2) : null;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", c.code());
            m.put("flag", c.flag());
            m.put("name", c.name());
            m.put("unit", c.unit());
            m.put("rate", rate);
            m.put("change", prev == null ? null : round(rate - prev, 2));
            m.put("changeRate", prev == null || prev == 0 ? null : round((rate - prev) / prev * 100, 2));
            m.put("history", history);
            rates.add(m);

        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", dates.get(dates.size() - 1));
        result.put("rates", rates);
        result.put("source", "Frankfurter (유럽중앙은행 등 고시 환율)");
        return result;

    }

    // 그 날 1(또는 100) 단위 = 몇 원
    private static Double krw(Map<String, Double> day, Currency c) {

        Double usdKrw = day.get("KRW");
        if (usdKrw == null) return null;
        if ("USD".equals(c.code())) return usdKrw * c.unit();

        Double usdX = day.get(c.code());
        if (usdX == null || usdX == 0) return null;
        return usdKrw / usdX * c.unit();

    }

    private static double round(double v, int scale) {

        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();

    }
}
