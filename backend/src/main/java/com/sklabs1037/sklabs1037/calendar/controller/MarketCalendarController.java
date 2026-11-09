package com.sklabs1037.sklabs1037.calendar.controller;

import com.sklabs1037.sklabs1037.calendar.service.CalendarAlertService;
import com.sklabs1037.sklabs1037.calendar.service.MarketCalendar;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import com.sklabs1037.sklabs1037.calendar.service.MarketCalendar.Event;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

/**
 * 📅 증시 일정 (로그인 없이 누구나)
 *   GET /api/market/calendar?month=2026-11   그 달 일정 + 오늘부터 다가오는 일정 6개 + 오늘이 거래일인지
 *   GET  /api/market/calendar/alert            🔔 내 알림 설정 (로그인)
 *   POST /api/market/calendar/alert { enabled, kinds[] }   켜기·끄기·종류 바꾸기 (로그인)
 */
@RestController
public class MarketCalendarController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private CalendarAlertService calendarAlertService;

    @GetMapping("/api/market/calendar")
    public ResponseEntity<Map<String, Object>> calendar(@RequestParam(required = false) String month) {

        LocalDate today = LocalDate.now(KST);
        YearMonth ym;
        try {
            ym = month == null || month.isBlank() ? YearMonth.from(today) : YearMonth.parse(month);
        } catch (Exception e) {
            ym = YearMonth.from(today);
        }
        // 너무 먼 달은 막음 (표가 없는 곳)
        if (ym.isBefore(YearMonth.of(2026, 1))) ym = YearMonth.of(2026, 1);
        if (ym.isAfter(YearMonth.from(MarketCalendar.COVERED_UNTIL))) ym = YearMonth.from(MarketCalendar.COVERED_UNTIL);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("today", today.toString());
        body.put("month", ym.toString());
        body.put("tradingToday", MarketCalendar.isTradingDay(today));
        body.put("events", MarketCalendar.between(ym.atDay(1), ym.atEndOfMonth()).stream().map(MarketCalendarController::toMap).toList());
        body.put("upcoming", MarketCalendar.between(today, today.plusDays(60)).stream().limit(6).map(MarketCalendarController::toMap).toList());
        body.put("minMonth", "2026-01");
        body.put("maxMonth", YearMonth.from(MarketCalendar.COVERED_UNTIL).toString());

        return ResponseEntity.ok(body);

    }

    static Map<String, Object> toMap(Event e) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", e.date().toString());
        m.put("kind", e.kind().name());
        m.put("title", e.title());
        m.put("detail", e.detail());
        m.put("time", e.time());
        m.put("big", e.big());
        return m;

    }

    @GetMapping("/api/market/calendar/alert")
    public ResponseEntity<Map<String, Object>> getAlert(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "로그인이 필요해요."));

        try {
            Map<String, Object> body = new LinkedHashMap<>(calendarAlertService.get(userId));
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", "알림 설정을 불러오지 못했어요."));
        }

    }

    @PostMapping("/api/market/calendar/alert")
    public ResponseEntity<Map<String, Object>> setAlert(HttpServletRequest request, @RequestBody Map<String, Object> payload) {

        Long userId = userId(request);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "로그인이 필요해요."));

        try {
            List<String> kinds = payload.get("kinds") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : null;
            Map<String, Object> body = new LinkedHashMap<>(calendarAlertService.set(userId, Boolean.TRUE.equals(payload.get("enabled")), kinds));
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", "저장하지 못했어요."));
        }

    }

    private Long userId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO u = session == null ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);
        return u == null ? null : u.getId();

    }
}
