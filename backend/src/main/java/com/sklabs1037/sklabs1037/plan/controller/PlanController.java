package com.sklabs1037.sklabs1037.plan.controller;

import com.sklabs1037.sklabs1037.plan.service.PlanService;
import com.sklabs1037.sklabs1037.plan.service.VoiceBriefService;
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
 * 🗓️ 내 약속 + 🔊 음성 브리핑 Controller (모두 로그인 필요)
 *
 * API 목록
 *   GET  /api/plans                내 약속 (오늘부터)            → { plans: [{ id, title, place, date, time, day, weekday, text }] }
 *   POST /api/plans                약속 추가 { title, date, time?, place? } → { plan }
 *   POST /api/plans/{id}/delete    약속 지우기
 *   GET  /api/voice/settings       음성 브리핑 설정              → { morning, close, region, regions }
 *   POST /api/voice/settings       { morning, close, region }
 *   GET  /api/voice/brief?kind=    지금 읽을 문장 (morning | close | plan) → { title, text }
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class PlanController {

    @Autowired
    private PlanService planService;

    @Autowired
    private VoiceBriefService voiceBriefService;

    @GetMapping("/plans")
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("약속 목록", () -> Map.of("plans", planService.upcoming(userId)));

    }

    @PostMapping("/plans")
    public ResponseEntity<Map<String, Object>> add(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("약속 추가", () -> Map.of("plan", planService.add(userId, req.get("title"), req.get("date"), req.get("time"), req.get("place"))));

    }

    @PostMapping("/plans/{id}/delete")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("약속 지우기", () -> {
            planService.delete(userId, id);
            return Map.of();
        });

    }

    @GetMapping("/voice/settings")
    public ResponseEntity<Map<String, Object>> settings(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("음성 브리핑 설정", () -> voiceBriefService.settings(userId));

    }

    @PostMapping("/voice/settings")
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("음성 브리핑 설정", () -> voiceBriefService.save(userId,
                Boolean.TRUE.equals(req.get("morning")), Boolean.TRUE.equals(req.get("close")), req.get("region")));

    }

    @GetMapping("/voice/brief")
    public ResponseEntity<Map<String, Object>> brief(@RequestParam(defaultValue = "morning") String kind, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인하면 브리핑을 들을 수 있어요.");

        return run("음성 브리핑", () -> voiceBriefService.brief(userId, kind));

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

        } catch (IllegalArgumentException | IllegalStateException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("{} 오류", what, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, what + "을(를) 처리하지 못했어요.");

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
