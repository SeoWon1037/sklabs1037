package com.sklabs1037.sklabs1037.calendar.service;

import com.sklabs1037.sklabs1037.calendar.dao.CalendarAlertDao;
import com.sklabs1037.sklabs1037.calendar.service.MarketCalendar.Event;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 🔔 증시 일정 알림 (작업 CALENDAR_ALERT, 매일 19:00)
 * - 내일 일정(MarketCalendar)이 있으면, 그 종류를 켠 회원에게 사이트 알림 + 📲 푸시 (한 사람에 하루 1개로 묶어서)
 * - FOMC는 한국 시간 새벽이라 "내일 새벽 4시"처럼 전날 저녁에 알려주는 게 맞음
 * - 같은 날 두 번 돌아도 알림 중복 방지 키(CAL:날짜)로 한 번만
 */
@Slf4j
@Service
public class CalendarAlertService {

    public static final List<String> KINDS = List.of("FOMC", "BOK", "EXPIRY", "HOLIDAY");

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private CalendarAlertDao calendarAlertDao;

    @Autowired
    private NotificationService notificationService;

    public record Message(String title, String body) {
    }

    // ============================================
    // 내 설정
    // ============================================
    public Map<String, Object> get(Long userId) {

        String kinds = calendarAlertDao.selectKinds(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", kinds != null);
        m.put("kinds", kinds == null ? KINDS : parse(kinds));
        return m;

    }

    public Map<String, Object> set(Long userId, boolean enabled, List<String> kinds) {

        List<String> clean = kinds == null ? KINDS : KINDS.stream().filter(kinds::contains).toList();
        if (!enabled || clean.isEmpty()) calendarAlertDao.delete(userId);
        else calendarAlertDao.upsert(userId, String.join(",", clean));
        return get(userId);

    }

    // ============================================
    // 매일 19:00
    // ============================================
    public JobOutcome sendAll() {

        LocalDate tomorrow = LocalDate.now(KST).plusDays(1);
        List<Event> events = MarketCalendar.between(tomorrow, tomorrow);
        if (events.isEmpty()) return JobOutcome.of(0, 0, "내일(" + tomorrow + ") 일정 없음");

        int sent = 0;
        int failed = 0;
        List<Map<String, Object>> subs = calendarAlertDao.selectAll();

        for (Map<String, Object> s : subs) {
            Message msg = message(events, parse(String.valueOf(s.get("kinds"))));
            if (msg == null) continue;
            try {
                Long userId = ((Number) s.get("userId")).longValue();
                notificationService.notify(userId, "CALENDAR", msg.title(), msg.body(), "/calendar", "CAL:" + tomorrow);
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("증시 일정 알림 실패: {}", e.getMessage());
            }
        }

        return JobOutcome.of(sent + failed, failed, "내일 일정 " + events.size() + "개 · 알림 " + sent + "명 (켠 회원 " + subs.size() + "명)");

    }

    // 내일 일정 중 이 회원이 켠 종류만 → 알림 한 개 (없으면 null, 순수 계산)
    static Message message(List<Event> events, List<String> kinds) {

        List<Event> mine = events.stream().filter(e -> kinds.contains(kindOf(e))).toList();
        if (mine.isEmpty()) return null;

        Event first = mine.stream().filter(Event::big).findFirst().orElse(mine.get(0));
        String title = "📅 내일 " + first.title() + (mine.size() > 1 ? " 외 " + (mine.size() - 1) + "개" : "");

        List<String> lines = new ArrayList<>();
        for (Event e : mine) {
            String when = e.time() == null ? "" : (e.time().compareTo("06:00") < 0 ? "새벽 " : "") + e.time() + " ";
            lines.add(when + e.title());
        }
        return new Message(title, String.join(" · ", lines));

    }

    // 개장(새해 첫날 늦은 개장)은 휴장 알림을 켠 사람에게
    private static String kindOf(Event e) {

        return e.kind() == MarketCalendar.Kind.OPEN ? "HOLIDAY" : e.kind().name();

    }

    private static List<String> parse(String csv) {

        return Arrays.stream(csv.split(",")).map(String::strip).filter(KINDS::contains).toList();

    }
}
