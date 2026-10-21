package com.sklabs1037.sklabs1037.mock.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.mock.service.ResetRequestService;
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
 * ♻️ 모의투자 계좌 초기화 신청
 *
 * 사용자 (로그인)
 *   GET  /api/mock/reset-request           내 기다리는 신청 + 마지막 처리 결과
 *   POST /api/mock/reset-request { reason } 신청
 *   POST /api/mock/reset-request/cancel     신청 취소
 * 관리자
 *   GET  /api/admin/resets                  신청 목록 (기다리는 것 + 최근 처리)
 *   GET  /api/admin/resets/count            기다리는 수 (탭 배지)
 *   POST /api/admin/resets/{id}/approve     승인 → 바로 초기화
 *   POST /api/admin/resets/{id}/reject { note }   거절
 */
@Slf4j
@RestController
public class MockResetController {

    @Autowired
    private ResetRequestService resetRequestService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    // ===== 사용자 =====
    @GetMapping("/api/mock/reset-request")
    public ResponseEntity<Map<String, Object>> mine(HttpServletRequest request) {

        UserLoginVO user = user(request);
        if (user == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있어요.");

        return run("초기화 신청", () -> resetRequestService.mine(user.getId()));

    }

    @PostMapping("/api/mock/reset-request")
    public ResponseEntity<Map<String, Object>> request(@RequestBody(required = false) Map<String, Object> req, HttpServletRequest request) {

        UserLoginVO user = user(request);
        if (user == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있어요.");

        String reason = req == null || req.get("reason") == null ? null : String.valueOf(req.get("reason"));
        return run("초기화 신청", () -> resetRequestService.request(user.getId(), reason));

    }

    @PostMapping("/api/mock/reset-request/cancel")
    public ResponseEntity<Map<String, Object>> cancel(HttpServletRequest request) {

        UserLoginVO user = user(request);
        if (user == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있어요.");

        return run("초기화 신청 취소", () -> resetRequestService.cancel(user.getId()));

    }

    // ===== 관리자 =====
    @GetMapping("/api/admin/resets")
    public ResponseEntity<Map<String, Object>> adminList(HttpServletRequest request) {

        if (admin(request) == null) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있어요.");
        return run("초기화 신청 목록", () -> resetRequestService.adminList());

    }

    @GetMapping("/api/admin/resets/count")
    public ResponseEntity<Map<String, Object>> count(HttpServletRequest request) {

        if (admin(request) == null) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있어요.");
        return run("초기화 신청 수", () -> Map.of("count", resetRequestService.pendingCount()));

    }

    @PostMapping("/api/admin/resets/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id, HttpServletRequest request) {

        Long adminId = admin(request);
        if (adminId == null) return fail(HttpStatus.FORBIDDEN, "관리자만 처리할 수 있어요.");

        return run("초기화 승인", () -> {
            resetRequestService.approve(adminId, id);
            return resetRequestService.adminList();
        });

    }

    @PostMapping("/api/admin/resets/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> req,
                                                      HttpServletRequest request) {

        Long adminId = admin(request);
        if (adminId == null) return fail(HttpStatus.FORBIDDEN, "관리자만 처리할 수 있어요.");

        String note = req == null || req.get("note") == null ? null : String.valueOf(req.get("note"));
        return run("초기화 거절", () -> {
            resetRequestService.reject(adminId, id, note);
            return resetRequestService.adminList();
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

        } catch (IllegalArgumentException | IllegalStateException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("{} 오류", what, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, what + "을(를) 처리하지 못했어요.");

        }

    }

    private UserLoginVO user(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        return (session == null) ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);

    }

    private Long admin(HttpServletRequest request) {

        UserLoginVO u = user(request);
        return u != null && adminFeedbackService.isAdmin(u.getId()) ? u.getId() : null;

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
