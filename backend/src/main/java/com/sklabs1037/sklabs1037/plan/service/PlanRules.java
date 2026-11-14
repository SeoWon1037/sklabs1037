package com.sklabs1037.sklabs1037.plan.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 🗓️ 내 약속 규칙 (순수 계산 → 테스트 가능)
 * - 날짜 · 시간은 한국 시간 그대로 다룸 (서버가 어느 시간대에 있든 호출하는 쪽이 한국 시간 now를 넘김)
 * - 시간이 00:00이면 "시간 미정" (말할 때 시간을 빼고 말함)
 * - 말로 읽을 문장을 만드는 곳: "내일 저녁 7시 강남에서 민지랑 저녁"
 */
public final class PlanRules {

    private PlanRules() {
    }

    public static final int MAX_UPCOMING = 50;
    public static final int MAX_TITLE = 60;
    public static final int MAX_PLACE = 40;

    private static final String[] DAYS = {"월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"};

    public static String title(Object raw) {

        String s = clean(raw);
        if (s.isEmpty()) throw new IllegalArgumentException("약속 이름을 알려주세요.");
        if (s.length() > MAX_TITLE) throw new IllegalArgumentException("약속 이름은 " + MAX_TITLE + "자까지 쓸 수 있어요.");
        return s;

    }

    public static String place(Object raw) {

        String s = clean(raw);
        if (s.length() > MAX_PLACE) throw new IllegalArgumentException("장소는 " + MAX_PLACE + "자까지 쓸 수 있어요.");
        return s.isEmpty() ? null : s;

    }

    /** date: 2026-11-21, time: 19:00 (없으면 시간 미정) → 약속 시각. 지난 시각 · 1년 넘게 뒤는 거절 */
    public static LocalDateTime when(Object rawDate, Object rawTime, LocalDateTime now) {

        LocalDate date;
        try {
            date = LocalDate.parse(clean(rawDate));
        } catch (Exception e) {
            throw new IllegalArgumentException("날짜를 알 수 없어요. (예: 2026-11-21)");
        }

        String t = clean(rawTime);
        LocalTime time = LocalTime.MIDNIGHT;
        if (!t.isEmpty()) {
            try {
                time = LocalTime.parse(t.length() == 4 ? "0" + t : t).truncatedTo(ChronoUnit.MINUTES);
            } catch (Exception e) {
                throw new IllegalArgumentException("시간을 알 수 없어요. (예: 19:00)");
            }
        }

        LocalDateTime at = LocalDateTime.of(date, time);
        boolean noTime = time.equals(LocalTime.MIDNIGHT);

        if (noTime ? date.isBefore(now.toLocalDate()) : at.isBefore(now)) throw new IllegalArgumentException("이미 지난 시간이에요. 날짜와 시간을 다시 알려주세요.");
        if (date.isAfter(now.toLocalDate().plusYears(1))) throw new IllegalArgumentException("약속은 1년 안의 날짜만 넣을 수 있어요.");

        return at;

    }

    /** 19:00 → "저녁 7시", 15:30 → "오후 3시 30분", 00:00(시간 미정) → "" */
    public static String timeText(LocalTime t) {

        if (t == null || t.equals(LocalTime.MIDNIGHT)) return "";

        int h = t.getHour();
        String part = h < 6 ? "새벽" : h < 12 ? "오전" : h == 12 ? "낮" : h < 18 ? "오후" : h < 22 ? "저녁" : "밤";
        int h12 = h % 12 == 0 ? 12 : h % 12;

        return part + " " + h12 + "시" + (t.getMinute() == 0 ? "" : " " + t.getMinute() + "분");

    }

    /** 오늘 · 내일 · 모레, 그 밖에는 "11월 21일 토요일" */
    public static String dayText(LocalDate d, LocalDate today) {

        long diff = ChronoUnit.DAYS.between(today, d);
        if (diff == 0) return "오늘";
        if (diff == 1) return "내일";
        if (diff == 2) return "모레";

        return d.getMonthValue() + "월 " + d.getDayOfMonth() + "일 " + weekday(d.getDayOfWeek());

    }

    public static String weekday(DayOfWeek w) {

        return DAYS[w.getValue() - 1];

    }

    /** "저녁 7시 강남에서 민지랑 저녁" (날짜는 빼고) */
    public static String line(LocalDateTime at, String title, String place) {

        List<String> parts = new ArrayList<>();
        String time = timeText(at.toLocalTime());
        if (!time.isEmpty()) parts.add(time);
        if (place != null && !place.isBlank()) parts.add(place + "에서");
        parts.add(title);

        return String.join(" ", parts);

    }

    public record Message(String title, String body) {
    }

    /**
     * 알림 한 개 (그날 약속을 묶어서). plans: { planAt(LocalDateTime), title, place } 시간순
     * dayWord: "내일" 또는 "오늘"
     */
    public static Message reminder(List<Map<String, Object>> plans, String dayWord) {

        if (plans == null || plans.isEmpty()) return null;

        Map<String, Object> first = plans.get(0);
        String title = "🗓️ " + dayWord + " 약속: " + first.get("title") + (plans.size() > 1 ? " 외 " + (plans.size() - 1) + "개" : "");

        return new Message(title, speech(plans, dayWord));

    }

    /** 소리로 읽을 문장: "내일 약속이 2개 있어요. 저녁 7시 강남에서 민지랑 저녁. 밤 10시 영화." */
    public static String speech(List<Map<String, Object>> plans, String dayWord) {

        if (plans == null || plans.isEmpty()) return dayWord + " 약속은 없어요.";

        StringBuilder sb = new StringBuilder(dayWord + " 약속이 " + (plans.size() == 1 ? "" : plans.size() + "개 ") + "있어요.");
        for (Map<String, Object> p : plans) {
            sb.append(' ').append(line((LocalDateTime) p.get("planAt"), String.valueOf(p.get("title")), p.get("place") == null ? null : String.valueOf(p.get("place")))).append('.');
        }
        return sb.toString();

    }

    private static String clean(Object raw) {

        return raw == null ? "" : String.valueOf(raw).replaceAll("[\\p{Cntrl}<>]", " ").replaceAll("\\s+", " ").strip();

    }
}
