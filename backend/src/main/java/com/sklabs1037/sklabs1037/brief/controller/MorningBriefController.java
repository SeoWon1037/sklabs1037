package com.sklabs1037.sklabs1037.brief.controller;

import com.sklabs1037.sklabs1037.brief.service.MorningBriefService;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * 아침 브리핑 메일 Controller (마이페이지)
 *
 * API 목록 (로그인한 본인만)
 *   GET  /api/briefing                → { success, loggedIn, enabled, hasEmail, lastSentDay }
 *   POST /api/briefing { enabled }    → { success }    켜기 / 끄기
 *   POST /api/briefing/test           → { success }    나에게 지금 보내보기 (10분에 한 번)
 */
@Slf4j
@RestController
@RequestMapping("/api/briefing")
public class MorningBriefController {

    @Autowired
    private MorningBriefService morningBriefService;

    @Autowired
    private JsonMapper jsonMapper;

    @GetMapping
    public ResponseEntity<Map<String, Object>> status(HttpServletRequest request) {

        Long userId = getLoginUserId(request);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("loggedIn", userId != null);

        if (userId == null) return ResponseEntity.ok(body);

        try {

            body.putAll(morningBriefService.getStatus(userId));
            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("아침 브리핑 상태 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "불러오지 못했습니다.");

        }

    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> setEnabled(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = getLoginUserId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            morningBriefService.setEnabled(userId, Boolean.TRUE.equals(req.get("enabled")));
            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("아침 브리핑 설정 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "저장하지 못했습니다.");

        }

    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(HttpServletRequest request) {

        Long userId = getLoginUserId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            morningBriefService.sendTest(userId);
            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("아침 브리핑 테스트 메일 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요.");

        }

    }

    // ============================================
    // 공통
    // ============================================

    @SuppressWarnings("unchecked")
    private Long getLoginUserId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null) return null;

        Object loginUser = session.getAttribute(UserLoginController.LOGIN_USER);
        if (loginUser == null) return null;

        Map<String, Object> map = jsonMapper.convertValue(loginUser, Map.class);
        Object id = map.get("id");

        return (id instanceof Number n) ? n.longValue() : null;

    }

    private ResponseEntity<Map<String, Object>> ok() {

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);

        return ResponseEntity.ok(body);

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
