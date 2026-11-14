package com.sklabs1037.sklabs1037.couple.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 🎂 커플 생일 규칙 (순수 계산 → 테스트 가능)
 * - 월 · 일만 다룸 (양력). 2월 29일생은 윤년이 아닌 해에는 2월 28일에 챙김
 * - dDay: 오늘이 생일이면 0, 지났으면 내년 생일까지
 */
public final class BirthdayRules {

    private BirthdayRules() {
    }

    /** 알림을 보내는 날 (생일까지 남은 날) */
    public static final int[] REMIND_DAYS = {7, 1, 0};

    /** 월 · 일 확인 (없는 날짜면 거절) */
    public static MonthDay monthDay(Object month, Object day) {

        try {
            return MonthDay.of(Integer.parseInt(String.valueOf(month).strip()), Integer.parseInt(String.valueOf(day).strip()));
        } catch (NumberFormatException | DateTimeException e) {
            throw new IllegalArgumentException("생일의 월과 일을 다시 확인해주세요.");
        }

    }

    /** 그 해에 생일을 챙기는 날 */
    public static LocalDate inYear(MonthDay md, int year) {

        if (md.getMonthValue() == 2 && md.getDayOfMonth() == 29 && !Year.isLeap(year)) return LocalDate.of(year, 2, 28);
        return md.atYear(year);

    }

    /** 다가오는 생일 (오늘 포함) */
    public static LocalDate next(MonthDay md, LocalDate today) {

        LocalDate thisYear = inYear(md, today.getYear());
        return thisYear.isBefore(today) ? inYear(md, today.getYear() + 1) : thisYear;

    }

    public static long dDay(MonthDay md, LocalDate today) {

        return ChronoUnit.DAYS.between(today, next(md, today));

    }

    /** 화면 · 챗봇에 줄 모양 */
    public static Map<String, Object> view(String name, MonthDay md, LocalDate today) {

        LocalDate date = next(md, today);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("month", md.getMonthValue());
        m.put("day", md.getDayOfMonth());
        m.put("date", date.toString());
        m.put("dDay", ChronoUnit.DAYS.between(today, date));
        m.put("text", text(name, md, today));
        return m;

    }

    /** "민지님 생일은 12월 7일이고 23일 남았어요." */
    public static String text(String name, MonthDay md, LocalDate today) {

        long d = dDay(md, today);
        String when = md.getMonthValue() + "월 " + md.getDayOfMonth() + "일";

        if (d == 0) return "오늘은 " + name + "님 생일이에요!";
        if (d == 1) return name + "님 생일은 " + when + ", 바로 내일이에요.";
        return name + "님 생일은 " + when + "이고 " + d + "일 남았어요.";

    }

    public record Message(String title, String body) {
    }

    /** 기념일(100일 단위 · 주년) 알림: 두 사람 모두에게 (daysLeft: 7 · 1 · 0, 그 밖이면 null) */
    public static Message anniversaryMessage(String label, String date, long daysLeft) {

        if (daysLeft == 0) return new Message("🎉 오늘은 " + label + "이에요!", "함께한 시간을 축하해요 💕");
        if (daysLeft == 1) return new Message("💕 내일은 " + label + "이에요", date + " · 준비는 다 됐나요?");
        if (daysLeft == 7) return new Message("💕 " + label + "이 7일 남았어요", date + " · 미리 약속을 잡아 보세요");
        return null;

    }

    /** 상대에게 보내는 알림 (daysLeft: 7 · 1 · 0) */
    public static Message partnerMessage(String name, MonthDay md, int daysLeft) {

        String when = md.getMonthValue() + "월 " + md.getDayOfMonth() + "일";

        return switch (daysLeft) {
            case 0 -> new Message("🎂 오늘은 " + name + "님 생일이에요!", "축하 인사와 하트를 보내 보세요 💗");
            case 1 -> new Message("🎂 내일은 " + name + "님 생일이에요", when + " · 준비는 다 됐나요?");
            default -> new Message("🎂 " + name + "님 생일이 " + daysLeft + "일 남았어요", when + " · 선물과 약속을 미리 챙겨 보세요");
        };

    }
}
