package com.sklabs1037.sklabs1037.ops.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.ops.service.AiUsageService;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 💸 AI 비용 (관리자만)
 *   GET  /api/admin/ai-cost                         오늘 · 30일 · 기능별 · 이번 달 · 한도
 *   POST /api/admin/ai-cost { dailyUsd, paused }    하루 한도 바꾸기 · 긴급 정지 / 다시 켜기
 */
@Slf4j
@RestController
public class AiCostController {

    @Autowired
    private AiUsageService aiUsageService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @GetMapping("/api/admin/ai-cost")
    public ResponseEntity<Map<String, Object>> get(HttpServletRequest request) {

        if (admin(request) == null) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있어요.");

        try {
            Map<String, Object> body = new HashMap<>(aiUsageService.dashboard());
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("AI 비용 현황 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "불러오지 못했어요. (DB에 T_AI_USAGE 테이블이 있는지 확인)");
        }

    }

    @PostMapping("/api/admin/ai-cost")
    public ResponseEntity<Map<String, Object>> update(HttpServletRequest request, @RequestBody Map<String, Object> payload) {

        Long admin = admin(request);
        if (admin == null) return fail(HttpStatus.FORBIDDEN, "관리자만 할 수 있어요.");

        try {
            double usd = payload.get("dailyUsd") instanceof Number n ? n.doubleValue() : -1;
            aiUsageService.update(admin, usd, Boolean.TRUE.equals(payload.get("paused")));
            Map<String, Object> body = new HashMap<>(aiUsageService.dashboard());
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        }

    }

    private Long admin(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO u = session == null ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);
        return u != null && adminFeedbackService.isAdmin(u.getId()) ? u.getId() : null;

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);

    }
}
