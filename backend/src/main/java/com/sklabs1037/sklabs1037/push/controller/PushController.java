package com.sklabs1037.sklabs1037.push.controller;

import com.sklabs1037.sklabs1037.push.service.PushService;
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
 * 📲 휴대폰·PC 푸시 알림
 *
 *   GET  /api/push/key                         서버 공개키 (누구나, 브라우저 구독에 필요)
 *   GET  /api/push/devices                     내 알림 켠 기기 (로그인)
 *   POST /api/push/subscribe { endpoint, keys: { p256dh, auth } }   이 브라우저 알림 켜기
 *   POST /api/push/unsubscribe { endpoint }    이 브라우저 알림 끄기
 *   POST /api/push/test                        내 기기들로 테스트 알림 (20초에 1번)
 */
@Slf4j
@RestController
@RequestMapping("/api/push")
public class PushController {

    @Autowired
    private PushService pushService;

    @GetMapping("/key")
    public ResponseEntity<Map<String, Object>> key() {

        return run("푸시 키", () -> Map.of("publicKey", pushService.publicKey()));

    }

    @GetMapping("/devices")
    public ResponseEntity<Map<String, Object>> devices(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있어요.");
        return run("푸시 기기", () -> Map.of("devices", pushService.devices(userId)));

    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있어요.");

        Map<?, ?> keys = req.get("keys") instanceof Map<?, ?> m ? m : Map.of();
        return run("푸시 켜기", () -> pushService.subscribe(userId, str(req.get("endpoint")), str(keys.get("p256dh")), str(keys.get("auth")),
                request.getHeader("User-Agent")));

    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribe(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있어요.");
        return run("푸시 끄기", () -> {
            pushService.unsubscribe(userId, str(req.get("endpoint")));
            return Map.of();
        });

    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있어요.");
        return run("푸시 테스트", () -> Map.of("devices", pushService.sendTest(userId)));

    }

    // ============================================
    // 공통
    // ============================================
    private interface Body {
        Map<String, Object> get();
    }

    private ResponseEntity<Map<String, Object>> run(String what, Body body) {

        try {
            Map<String, Object> out = new HashMap<>(body.get());
            out.put("success", true);
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("{} 오류", what, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, what + " 중 오류가 발생했어요.");
        }

    }

    private static String str(Object o) {

        return o == null ? null : String.valueOf(o);

    }

    private Long userId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO u = session == null ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);
        return u == null ? null : u.getId();

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);

    }
}
