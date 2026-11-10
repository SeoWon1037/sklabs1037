package com.sklabs1037.sklabs1037.whatif.service;

import com.sklabs1037.sklabs1037.client.TossApiClient;
import com.sklabs1037.sklabs1037.ops.service.UsageStatService;
import com.sklabs1037.sklabs1037.whatif.dao.WhatIfDao;
import com.sklabs1037.sklabs1037.whatif.service.WhatIfCalc.Point;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ⏳ "그때 샀더라면" — 종목 · 날짜 · 금액을 넣으면 지금 얼마가 됐을지 (로그인 없이 누구나)
 *
 * 시세는 어디서
 * - 우리 DB(T_STOCK_PRICE)에는 최근 200거래일쯤만 있음 → 더 옛날은 토스증권 일봉을 길게(최대 약 5년) 한 번 받아 씀
 * - 받은 긴 시세는 DB에 넣지 않고 메모리에만 6시간 둠 (종목 화면 차트 · 다른 계산에 영향을 주지 않게)
 *   최근 값은 요청마다 DB에서 새로 읽어 합침 (WhatIfService.merge)
 * - 토스 호출은 하루 300번까지, 실패하거나 한도를 넘으면 DB에 있는 만큼만으로 계산하고 "가장 이른 날"을 알려줌
 *
 * 계산과 검증은 WhatIfCalc (순수 계산) — 가격 기준이 바뀐 날이 끼면 답을 내지 않음
 * 비교: 같은 날 같은 돈을 KODEX 200에 넣었다면 (계산이 안 되면 비교만 생략)
 */
@Slf4j
@Service
public class WhatIfService {

    static final String BENCH_SYMBOL = "069500";       // KODEX 200
    static final int[] HISTORY_COUNTS = {1300, 600};    // 약 5년 → 안 되면 약 2년 반
    static final int DAILY_FETCH_LIMIT = 300;
    static final long CACHE_MS = 6 * 3600_000L;
    static final long RETRY_MS = 30 * 60_000L;
    static final int CACHE_MAX = 300;
    static final int MAX_YEARS = 10;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private WhatIfDao whatIfDao;

    @Autowired
    private TossApiClient tossApiClient;

    @Autowired
    private UsageStatService usageStatService;

    // 토스에서 받은 긴 시세 (until: 이 시각까지 다시 받지 않음)
    private record Cached(List<Point> points, long until) {
    }

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final AtomicInteger fetchedToday = new AtomicInteger();
    private volatile LocalDate fetchDay = LocalDate.now(KST);

    public Map<String, Object> calc(String rawSymbol, String rawFrom, long amount) {

        return calc(rawSymbol, rawFrom, amount, true);

    }

    // count: 이용 통계에 셀지 (링크 미리보기를 만들 때는 세지 않음)
    public Map<String, Object> calc(String rawSymbol, String rawFrom, long amount, boolean count) {

        WhatIfCalc.checkAmount(amount);
        String symbol = symbol(rawSymbol);
        LocalDate today = LocalDate.now(KST);
        LocalDate from = date(rawFrom, today);

        Map<String, Object> stock = whatIfDao.selectStock(symbol);
        if (stock == null) throw new NoSuchElementException("없는 종목이에요. 종목을 다시 골라주세요.");

        List<Point> points = history(symbol, num(stock.get("id")), from);
        Map<String, Object> out = new LinkedHashMap<>(WhatIfCalc.calc(points, from, amount));

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("symbol", stock.get("symbol"));
        s.put("name", stock.get("name"));
        s.put("market", stock.get("market"));
        out.put("stock", s);
        out.put("bench", symbol.equals(BENCH_SYMBOL) ? null : bench(LocalDate.parse(String.valueOf(out.get("buyDate"))), amount));

        if (count) usageStatService.event("whatif");
        return out;

    }

    // 같은 날 같은 돈을 KODEX 200에 넣었다면 { name, value, rate, buyDate } (계산할 수 없으면 null)
    private Map<String, Object> bench(LocalDate buyDate, long amount) {

        try {
            Map<String, Object> stock = whatIfDao.selectStock(BENCH_SYMBOL);
            if (stock == null) return null;
            Map<String, Object> r = WhatIfCalc.calc(history(BENCH_SYMBOL, num(stock.get("id")), buyDate), buyDate, amount);
            if (Boolean.TRUE.equals(r.get("clamped"))) return null;   // 같은 날부터 비교할 수 없으면 생략

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", stock.get("name"));
            out.put("value", r.get("value"));
            out.put("rate", r.get("rate"));
            out.put("buyDate", r.get("buyDate"));
            return out;
        } catch (RuntimeException e) {
            return null;
        }

    }

    // ============================================
    // 시세 모으기: 메모리 → (필요하면) 토스 긴 일봉 → DB
    // ============================================
    List<Point> history(String symbol, Long stockId, LocalDate from) {

        List<Point> db = fromDb(stockId);   // 최근 값은 언제나 DB에서 새로 (매일 채워지는 값)

        long now = System.currentTimeMillis();
        Cached hit = cache.get(symbol);
        if (hit != null && now < hit.until()) return merge(hit.points(), db);

        boolean covered = !db.isEmpty() && !db.get(0).date().isAfter(from);
        if (covered) return db;   // DB만으로 되는 기간은 토스를 부르지 않음

        List<Point> api = fromApi(symbol);
        if (cache.size() >= CACHE_MAX) cache.clear();
        cache.put(symbol, new Cached(api, now + (api.isEmpty() ? RETRY_MS : CACHE_MS)));   // 못 받았으면 30분 뒤에 다시
        return merge(api, db);

    }

    private List<Point> fromDb(Long stockId) {

        List<Point> out = new ArrayList<>();
        for (Map<String, Object> r : whatIfDao.selectCloses(stockId)) {
            out.add(new Point(LocalDate.parse(String.valueOf(r.get("day"))), new BigDecimal(String.valueOf(r.get("close")))));
        }
        return WhatIfCalc.clean(out);

    }

    // 토스 일봉을 길게 (실패 · 한도 초과 · 키 없음이면 빈 목록)
    private List<Point> fromApi(String symbol) {

        if (!tossApiClient.isConfigured() || !takeFetch()) return List.of();

        for (int count : HISTORY_COUNTS) {
            try {
                List<Point> out = toPoints(tossApiClient.getDailyCandles(symbol, count));
                if (!out.isEmpty()) return out;
            } catch (RuntimeException e) {
                log.info("긴 시세 받기 실패 ({} · {}개): {}", symbol, count, e.getMessage());
            }
        }
        return List.of();

    }

    // 토스 일봉 → 날짜 · 종가 (읽을 수 없는 줄은 건너뜀)
    static List<Point> toPoints(List<Map<String, Object>> candles) {

        List<Point> out = new ArrayList<>();
        if (candles == null) return out;
        for (Map<String, Object> c : candles) {
            try {
                out.add(new Point(OffsetDateTime.parse(String.valueOf(c.get("timestamp"))).toLocalDate(),
                        new BigDecimal(String.valueOf(c.get("closePrice")))));
            } catch (RuntimeException e) {
                // 날짜나 가격이 이상한 줄
            }
        }
        return WhatIfCalc.clean(out);

    }

    /**
     * 긴 시세(토스) + DB 시세 합치기
     * - 긴 시세의 마지막 날보다 앞선 날짜: 긴 시세 값 (한 번에 받은 값이라 가격 기준이 서로 같음)
     * - 긴 시세의 마지막 날과 그 뒤: DB 값 (받던 순간이 장중이면 마지막 봉은 종가가 아니라서)
     */
    static List<Point> merge(List<Point> api, List<Point> db) {

        if (api == null || api.isEmpty()) return WhatIfCalc.clean(db);
        LocalDate apiLast = api.get(api.size() - 1).date();

        List<Point> all = new ArrayList<>();
        for (Point p : db) if (p.date().isBefore(apiLast)) all.add(p);
        for (Point p : api) all.add(p);                                    // 같은 날짜면 뒤에 넣은 긴 시세가 이김
        for (Point p : db) if (!p.date().isBefore(apiLast)) all.add(p);    // 마지막 날부터는 DB가 이김
        return WhatIfCalc.clean(all);

    }

    private boolean takeFetch() {

        LocalDate today = LocalDate.now(KST);
        if (!today.equals(fetchDay)) {
            fetchDay = today;
            fetchedToday.set(0);
        }
        return fetchedToday.incrementAndGet() <= DAILY_FETCH_LIMIT;

    }

    // ============================================
    // 입력 검사
    // ============================================
    static String symbol(String raw) {

        String s = raw == null ? "" : raw.strip().toUpperCase();
        if (!s.matches("[0-9A-Z]{6}")) throw new IllegalArgumentException("종목을 골라주세요.");
        return s;

    }

    static LocalDate date(String raw, LocalDate today) {

        LocalDate d;
        try {
            d = LocalDate.parse(raw == null ? "" : raw.strip());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("날짜를 골라주세요.");
        }
        if (!d.isBefore(today)) throw new IllegalArgumentException("오늘보다 이전 날짜를 골라주세요.");
        if (d.isBefore(today.minusYears(MAX_YEARS))) throw new IllegalArgumentException("최근 " + MAX_YEARS + "년 안의 날짜를 골라주세요.");
        return d;

    }

    private static Long num(Object o) {

        return o instanceof Number n ? n.longValue() : null;

    }
}
