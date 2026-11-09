package com.sklabs1037.sklabs1037.calendar.service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * 📅 증시 일정 (DB 없이 코드 안의 표 + 규칙, 순수 계산이라 테스트 가능)
 *
 * 1. 정해진 날 (아래 표, 발표되면 여기에 추가)
 *    - 한국거래소 휴장일 · 첫 거래일 늦은 개장
 *    - 🇺🇸 FOMC 금리 결정: 미국 동부 오후 2시 발표 → 한국 시간으로 바꿔서 (다음 날 새벽 3시 또는 4시)
 *    - 🇰🇷 한국은행 금융통화위원회 기준금리 결정 (오전 9시대 발표)
 * 2. 규칙으로 계산
 *    - 선물·옵션 만기일: 매달 둘째 목요일 (휴장일이면 그 전 거래일)
 *      3·6·9·12월은 동시만기일(네 마녀의 날) → 변동성이 커질 수 있음
 *
 * 출처: 연준 FOMC 일정(2027년은 잠정), 한국은행 금통위 일정 발표(2026.10.29), 한국거래소 휴장일 공지
 */
public final class MarketCalendar {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneId NY = ZoneId.of("America/New_York");

    public enum Kind { HOLIDAY, FOMC, BOK, EXPIRY, OPEN }

    public record Event(LocalDate date, Kind kind, String title, String detail, String time, boolean big) {
    }

    // 한국거래소 휴장일 (주말 제외, 2027년 설·추석 등은 거래소가 12월에 발표하면 추가)
    static final Map<LocalDate, String> KRX_HOLIDAYS = new TreeMap<>(Map.ofEntries(
            Map.entry(LocalDate.of(2026, 1, 1), "신정"),
            Map.entry(LocalDate.of(2026, 2, 16), "설날 연휴"),
            Map.entry(LocalDate.of(2026, 2, 17), "설날"),
            Map.entry(LocalDate.of(2026, 2, 18), "설날 연휴"),
            Map.entry(LocalDate.of(2026, 3, 2), "삼일절 대체공휴일"),
            Map.entry(LocalDate.of(2026, 5, 1), "근로자의 날"),
            Map.entry(LocalDate.of(2026, 5, 5), "어린이날"),
            Map.entry(LocalDate.of(2026, 5, 25), "부처님오신날 대체공휴일"),
            Map.entry(LocalDate.of(2026, 6, 3), "전국동시지방선거"),
            Map.entry(LocalDate.of(2026, 7, 17), "제헌절"),
            Map.entry(LocalDate.of(2026, 8, 17), "광복절 대체공휴일"),
            Map.entry(LocalDate.of(2026, 9, 24), "추석 연휴"),
            Map.entry(LocalDate.of(2026, 9, 25), "추석"),
            Map.entry(LocalDate.of(2026, 10, 5), "개천절 대체공휴일"),
            Map.entry(LocalDate.of(2026, 10, 9), "한글날"),
            Map.entry(LocalDate.of(2026, 12, 25), "성탄절"),
            Map.entry(LocalDate.of(2026, 12, 31), "연말 휴장"),
            Map.entry(LocalDate.of(2027, 1, 1), "신정")));

    // 새해 첫 거래일 (개장식 때문에 오전 10시 개장)
    static final List<LocalDate> LATE_OPEN = List.of(LocalDate.of(2026, 1, 2), LocalDate.of(2027, 1, 4));

    // FOMC 결정일 (미국 날짜, 2027년은 연준 잠정 일정)
    static final List<LocalDate> FOMC = List.of(
            LocalDate.of(2026, 1, 28), LocalDate.of(2026, 3, 18), LocalDate.of(2026, 4, 29), LocalDate.of(2026, 6, 17),
            LocalDate.of(2026, 7, 29), LocalDate.of(2026, 9, 16), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 9),
            LocalDate.of(2027, 1, 27), LocalDate.of(2027, 3, 17), LocalDate.of(2027, 4, 28), LocalDate.of(2027, 6, 9),
            LocalDate.of(2027, 7, 28), LocalDate.of(2027, 9, 15), LocalDate.of(2027, 10, 27), LocalDate.of(2027, 12, 8));

    // 한국은행 기준금리 결정 (통화정책방향 결정회의)
    static final List<LocalDate> BOK = List.of(
            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 26), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 28),
            LocalDate.of(2026, 7, 16), LocalDate.of(2026, 8, 27), LocalDate.of(2026, 10, 22), LocalDate.of(2026, 11, 26),
            LocalDate.of(2027, 1, 14), LocalDate.of(2027, 2, 25), LocalDate.of(2027, 4, 9), LocalDate.of(2027, 5, 27),
            LocalDate.of(2027, 7, 15), LocalDate.of(2027, 8, 26), LocalDate.of(2027, 10, 15), LocalDate.of(2027, 11, 25));

    // 표가 채워진 마지막 날 (이 뒤는 "아직 발표 전")
    public static final LocalDate COVERED_UNTIL = LocalDate.of(2027, 12, 31);

    private MarketCalendar() {
    }

    // from ~ to (둘 다 포함) 사이의 일정, 날짜 순
    public static List<Event> between(LocalDate from, LocalDate to) {

        List<Event> out = new ArrayList<>();

        KRX_HOLIDAYS.forEach((d, name) -> {
            if (!d.isBefore(from) && !d.isAfter(to)) {
                out.add(new Event(d, Kind.HOLIDAY, "증시 휴장 · " + name, "코스피·코스닥 거래 없음", null, false));
            }
        });

        for (LocalDate d : LATE_OPEN) {
            if (!d.isBefore(from) && !d.isAfter(to)) {
                out.add(new Event(d, Kind.OPEN, "새해 첫 거래일", "개장식 때문에 1시간 늦게 시작 (10:00 ~ 15:30)", "10:00", false));
            }
        }

        for (LocalDate us : FOMC) {
            ZonedDateTime kst = ZonedDateTime.of(us, LocalTime.of(14, 0), NY).withZoneSameInstant(KST);
            LocalDate d = kst.toLocalDate();
            if (d.isBefore(from) || d.isAfter(to)) continue;
            out.add(new Event(d, Kind.FOMC, "🇺🇸 FOMC 금리 결정",
                    "미국 기준금리 발표 (미국 " + us.getMonthValue() + "/" + us.getDayOfMonth() + " 오후 2시) · 30분 뒤 의장 기자회견",
                    String.format("%02d:%02d", kst.getHour(), kst.getMinute()), true));
        }

        for (LocalDate d : BOK) {
            if (d.isBefore(from) || d.isAfter(to)) continue;
            out.add(new Event(d, Kind.BOK, "🇰🇷 한국은행 기준금리 결정", "금융통화위원회 통화정책방향 결정 · 오전 총재 기자간담회", "09:50", true));
        }

        // 선물·옵션 만기일
        for (YearMonth ym = YearMonth.from(from); !ym.isAfter(YearMonth.from(to)); ym = ym.plusMonths(1)) {
            LocalDate d = expiry(ym);
            if (d.isBefore(from) || d.isAfter(to)) continue;
            boolean quad = ym.getMonthValue() % 3 == 0;
            out.add(new Event(d, Kind.EXPIRY, quad ? "선물·옵션 동시만기일 (네 마녀의 날)" : "옵션 만기일",
                    quad ? "주가지수 선물·옵션, 개별주식 선물·옵션이 한꺼번에 끝나는 날 · 장 마감 무렵 변동이 커질 수 있어요"
                            : "주가지수 옵션이 끝나는 날 · 장 마감 무렵 거래가 몰릴 수 있어요",
                    null, quad));
        }

        out.sort(Comparator.comparing(Event::date).thenComparing(e -> e.kind().ordinal()));
        return out;

    }

    // 매달 둘째 목요일, 휴장일이면 그 전 거래일
    public static LocalDate expiry(YearMonth ym) {

        LocalDate d = ym.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.THURSDAY));
        while (!isTradingDay(d)) d = d.minusDays(1);
        return d;

    }

    public static boolean isTradingDay(LocalDate d) {

        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !KRX_HOLIDAYS.containsKey(d);

    }
}
