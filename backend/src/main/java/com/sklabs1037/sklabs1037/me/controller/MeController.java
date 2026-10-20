package com.sklabs1037.sklabs1037.me.controller;

import com.sklabs1037.sklabs1037.me.service.ActivityService;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 내 활동 · 알림 · 오늘의 SKLabs (모두 로그인)
 *
 *   GET  /api/me/activity              🏅 내 활동 + 배지 (새로 받은 배지 newBadges)
 *   GET  /api/me/today                 📅 출석 도장 + 오늘 할 일
 *   GET  /api/me/notifications         🔔 알림 목록(최근 30개) + 안 읽은 수
 *   GET  /api/me/notifications/unread  🔔 안 읽은 수만 (상단 종, 60초마다)
 *   POST /api/me/notifications/read { id }   읽음 (id 없으면 전부)
 */
@Slf4j
@RestController
@RequestMapping("/api/me")
public class MeController {

    @Autowired
    private ActivityService activityService;

    @Autowired
    private NotificationService notificationService;

    @GetMapping("/activity")
    public ResponseEntity<Map<String, Object>> activity(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 볼 수 있어요.");

        return run("내 활동", () -> activityService.activity(userId));

    }

    @GetMapping("/today")
    public ResponseEntity<Map<String, Object>> today(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 볼 수 있어요.");

        return run("오늘의 SKLabs", () -> activityService.today(userId));

    }

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> notifications(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 볼 수 있어요.");

        return run("알림", () -> Map.of("items", notificationService.list(userId), "unread", notificationService.unread(userId)));

    }

    @GetMapping("/notifications/unread")
    public ResponseEntity<Map<String, Object>> unread(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 볼 수 있어요.");

        return run("알림 수", () -> Map.of("unread", notificationService.unread(userId)));

    }

    @PostMapping("/notifications/read")
    public ResponseEntity<Map<String, Object>> read(@RequestBody(required = false) Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 볼 수 있어요.");

        Long id = req != null && req.get("id") instanceof Number n ? n.longValue() : null;

        return run("알림 읽음", () -> {
            notificationService.markRead(userId, id);
            return Map.of("unread", notificationService.unread(userId));
        });

    }

    // ============================================
    // 공통
    // ============================================
    private interface Body {
        Map<String, Object> get() throws Exception;
    }

    private ResponseEntity<Map<String, Object>> run(String what, Body body) {

        try {

            Map<String, Object> m = new HashMap<>(body.get());
            m.put("success", true);

            return ResponseEntity.ok(m);

        } catch (Exception e) {

            log.error("{} 오류", what, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, what + "을(를) 불러오지 못했습니다.");

        }

    }

    private Long userId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO user = (session == null) ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);

        return user == null ? null : user.getId();

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
