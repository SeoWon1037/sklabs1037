package com.sklabs1037.sklabs1037.whatif.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ⏳ "그때 샀더라면" 계산 (DB · 외부 호출 없는 순수 계산 → 테스트로 검증)
 *
 * 계산 방법
 * - 산 날: 고른 날짜 또는 그 뒤 첫 거래일의 종가 (고른 날짜가 가진 기록보다 이르면 가장 이른 거래일로 당기고 clamped = true)
 * - 지금: 가장 최근 거래일의 종가
 * - 지금 가치 = 넣은 돈 × 지금 종가 ÷ 산 날 종가 (소수점 주식까지 샀다고 가정, 원 단위 반올림)
 * - 배당 · 세금 · 수수료는 넣지 않음
 *
 * 틀린 답을 내지 않기 위한 장치
 * - 국내 주식은 하루에 ±30%까지만 움직일 수 있음 → 산 날 뒤로 이웃한 두 거래일의 종가가 그보다 크게 벌어지면
 *   액면분할 · 병합 · 감자처럼 "가격 기준이 바뀐 날"로 보고 계산하지 않음 (Broken 예외에 그 날짜와 다시 해볼 날짜를 담음)
 */
public final class WhatIfCalc {

    public static final long MIN_AMOUNT = 10_000L;
    public static final long MAX_AMOUNT = 10_000_000_000L;
    public static final int CHART_POINTS = 120;

    // 하루 가격제한폭 ±30% + 호가 단위 반올림 여유
    static final BigDecimal MAX_UP = new BigDecimal("1.305");
    static final BigDecimal MAX_DOWN = new BigDecimal("0.695");

    private WhatIfCalc() {
    }

    public record Point(LocalDate date, BigDecimal close) {
    }

    // 가격 기준이 바뀐 날이 끼어 있어 계산할 수 없음 (retryFrom: 이 날부터는 계산 가능)
    public static class Broken extends IllegalArgumentException {

        private final LocalDate breakDate;
        private final LocalDate retryFrom;

        public Broken(LocalDate breakDate, LocalDate retryFrom) {
            super("이 기간에는 액면분할 · 병합처럼 가격 기준이 바뀐 날(" + breakDate + ")이 있어서 정확히 계산할 수 없어요. " + retryFrom + " 이후 날짜로 다시 해보세요.");
            this.breakDate = breakDate;
            this.retryFrom = retryFrom;
        }

        public LocalDate getBreakDate() {
            return breakDate;
        }

        public LocalDate getRetryFrom() {
            return retryFrom;
        }
    }

    // 날짜순 정리: 같은 날짜는 뒤에 온 것, 종가가 없거나 0 이하면 버림
    public static List<Point> clean(List<Point> raw) {

        TreeMap<LocalDate, BigDecimal> byDate = new TreeMap<>();
        if (raw != null) {
            for (Point p : raw) {
                if (p == null || p.date() == null || p.close() == null || p.close().signum() <= 0) continue;
                byDate.put(p.date(), p.close());
            }
        }
        List<Point> out = new ArrayList<>(byDate.size());
        byDate.forEach((d, c) -> out.add(new Point(d, c)));
        return out;

    }

    public static void checkAmount(long amount) {

        if (amount < MIN_AMOUNT) throw new IllegalArgumentException("금액은 1만 원 이상으로 넣어주세요.");
        if (amount > MAX_AMOUNT) throw new IllegalArgumentException("금액은 100억 원까지 넣을 수 있어요.");

    }

    /**
     * @param points 날짜순 종가 (clean을 거친 것)
     * @param from   사려고 고른 날짜
     * @param amount 넣은 돈 (원)
     * @return 화면에 그대로 줄 결과
     */
    public static Map<String, Object> calc(List<Point> points, LocalDate from, long amount) {

        checkAmount(amount);
        if (from == null) throw new IllegalArgumentException("날짜를 골라주세요.");
        if (points == null || points.size() < 2) throw new IllegalArgumentException("이 종목은 아직 시세 기록이 부족해요.");

        int last = points.size() - 1;
        int buy = -1;
        for (int i = 0; i <= last; i++) {
            if (!points.get(i).date().isBefore(from)) {
                buy = i;
                break;
            }
        }
        if (buy < 0 || buy >= last) throw new IllegalArgumentException("그 날짜 뒤로는 거래일이 하루도 지나지 않았어요. 더 이른 날짜를 골라주세요.");

        // 가격 기준이 바뀐 날 찾기 (가장 최근 것 기준으로 "이 날부터는 가능"을 알려줌)
        for (int i = last; i > buy; i--) {
            BigDecimal ratio = points.get(i).close().divide(points.get(i - 1).close(), 6, RoundingMode.HALF_UP);
            if (ratio.compareTo(MAX_UP) > 0 || ratio.compareTo(MAX_DOWN) < 0) {
                throw new Broken(points.get(i).date(), points.get(i).date());
            }
        }

        Point b = points.get(buy);
        Point n = points.get(last);
        BigDecimal money = BigDecimal.valueOf(amount);

        long value = valueAt(money, b.close(), n.close());
        BigDecimal rate = n.close().divide(b.close(), 10, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

        // 가장 높았던 날 · 가장 낮았던 날 (산 날 포함)
        Point peak = b, trough = b;
        for (int i = buy; i <= last; i++) {
            Point p = points.get(i);
            if (p.close().compareTo(peak.close()) > 0) peak = p;
            if (p.close().compareTo(trough.close()) < 0) trough = p;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("amount", amount);
        out.put("askedDate", from.toString());
        out.put("buyDate", b.date().toString());
        out.put("buyPrice", b.close());
        out.put("nowDate", n.date().toString());
        out.put("nowPrice", n.close());
        out.put("value", value);
        out.put("profit", value - amount);
        out.put("rate", rate);
        out.put("days", ChronoUnit.DAYS.between(b.date(), n.date()));
        out.put("clamped", buy == 0 && from.isBefore(b.date()));
        out.put("firstDate", points.get(0).date().toString());
        out.put("peak", spot(peak, money, b.close()));
        out.put("trough", spot(trough, money, b.close()));
        out.put("series", series(points, buy, last, money, b.close()));
        return out;

    }

    // 넣은 돈 × 그날 종가 ÷ 산 날 종가 (원 단위 반올림)
    static long valueAt(BigDecimal money, BigDecimal buyClose, BigDecimal close) {

        return money.multiply(close).divide(buyClose, 0, RoundingMode.HALF_UP).longValueExact();

    }

    private static Map<String, Object> spot(Point p, BigDecimal money, BigDecimal buyClose) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", p.date().toString());
        m.put("price", p.close());
        m.put("value", valueAt(money, buyClose, p.close()));
        return m;

    }

    // 차트용: 처음과 끝은 꼭 넣고 120개 안쪽으로 고르게 뽑음 [{ d, v }]
    static List<Map<String, Object>> series(List<Point> points, int buy, int last, BigDecimal money, BigDecimal buyClose) {

        int n = last - buy + 1;
        int step = Math.max(1, (int) Math.ceil(n / (double) CHART_POINTS));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = buy; i <= last; i += step) out.add(seriesPoint(points.get(i), money, buyClose));
        if ((last - buy) % step != 0) out.add(seriesPoint(points.get(last), money, buyClose));
        return out;

    }

    private static Map<String, Object> seriesPoint(Point p, BigDecimal money, BigDecimal buyClose) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("d", p.date().toString());
        m.put("v", valueAt(money, buyClose, p.close()));
        return m;

    }
}
