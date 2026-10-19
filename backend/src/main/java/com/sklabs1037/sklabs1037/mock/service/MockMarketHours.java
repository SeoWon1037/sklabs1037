package com.sklabs1037.sklabs1037.mock.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 모의투자 장 시간 / 호가 단위 규칙
 * - 정규장: 평일 09:00 ~ 15:30 (한국 시간)
 * - 공휴일은 따로 계산하지 않음 (공휴일엔 시세가 안 바뀌어서 체결되지 않고, 장 마감 시각에 만료됨)
 */
public final class MockMarketHours {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(15, 30);

    private MockMarketHours() {
    }

    // 지금 한국 시간
    public static ZonedDateTime now() {

        return ZonedDateTime.now(KST);

    }

    // 정규장 시간인지 (평일 09:00 ~ 15:30)
    public static boolean isOpen(ZonedDateTime time) {

        LocalTime t = time.toLocalTime();

        return isWeekday(time.toLocalDate()) && !t.isBefore(OPEN) && !t.isAfter(CLOSE);

    }

    // 지금 넣은 지정가 주문이 유효한 거래일
    // - 평일 장 마감(15:30) 전이면 오늘, 아니면 다음 평일
    public static LocalDate tradingDay(ZonedDateTime time) {

        LocalDate day = time.toLocalDate();

        if (isWeekday(day) && !time.toLocalTime().isAfter(CLOSE)) return day;

        do {
            day = day.plusDays(1);
        } while (!isWeekday(day));

        return day;

    }

    // 한국 주식 호가 단위 (2023년 기준)
    public static BigDecimal tickSize(BigDecimal price) {

        long p = price.longValue();

        if (p < 2_000) return BigDecimal.ONE;
        if (p < 5_000) return BigDecimal.valueOf(5);
        if (p < 20_000) return BigDecimal.valueOf(10);
        if (p < 50_000) return BigDecimal.valueOf(50);
        if (p < 200_000) return BigDecimal.valueOf(100);
        if (p < 500_000) return BigDecimal.valueOf(500);

        return BigDecimal.valueOf(1_000);

    }

    private static boolean isWeekday(LocalDate day) {

        DayOfWeek d = day.getDayOfWeek();

        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;

    }
}
