package com.sklabs1037.sklabs1037.autofix.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.autofix.service.AutofixService;
import com.sklabs1037.sklabs1037.qa.service.QaBotService;
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
 * 🤖 AI 자동 수정
 *
 * 관리자
 *   POST /api/admin/inquiries/{id}/autofix     [AI로 고치기] → GitHub ai-fix 이슈
 *   GET  /api/admin/autofix                    수정 목록 (+ 오늘 사용 수, 설정 여부)
 *   GET  /api/admin/autofix/count              승인 기다리는 수 (탭 배지)
 *   POST /api/admin/autofix/{id}/approve       PR 합치기 → 배포
 *   POST /api/admin/autofix/{id}/reject { note }   PR·이슈 닫기
 * GitHub Actions (ai-fix.yml)
 *   POST /api/autofix/callback (헤더 X-QA-Token) { issue, status, pr, branch, summary, files, error, runUrl }
 */
@Slf4j
@RestController
public class AutofixController {

    @Autowired
    private AutofixService autofixService;

    @Autowired
    private QaBotService qaBotService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @PostMapping("/api/admin/inquiries/{id}/autofix")
    public ResponseEntity<Map<String, Object>> request(@PathVariable Long id, HttpServletRequest request) {

        Long admin = admin(request);
        if (admin == null) return fail(HttpStatus.FORBIDDEN, "관리자만 할 수 있어요.");
        return run("AI 수정 요청", () -> autofixService.request(admin, id));

    }

    @GetMapping("/api/admin/autofix")
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest request) {

        if (admin(request) == null) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있어요.");
        return run("AI 수정 목록", () -> autofixService.list());

    }

    @GetMapping("/api/admin/autofix/count")
    public ResponseEntity<Map<String, Object>> count(HttpServletRequest request) {

        if (admin(request) == null) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있어요.");
        return run("AI 수정 수", () -> Map.of("count", autofixService.readyCount()));

    }

    @PostMapping("/api/admin/autofix/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id, HttpServletRequest request) {

        Long admin = admin(request);
        if (admin == null) return fail(HttpStatus.FORBIDDEN, "관리자만 할 수 있어요.");
        return run("AI 수정 승인", () -> {
            autofixService.approve(admin, id);
            return Map.of();
        });

    }

    @PostMapping("/api/admin/autofix/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> req,
                                                      HttpServletRequest request) {

        Long admin = admin(request);
        if (admin == null) return fail(HttpStatus.FORBIDDEN, "관리자만 할 수 있어요.");
        return run("AI 수정 거절", () -> {
            autofixService.reject(admin, id, req == null || req.get("note") == null ? null : String.valueOf(req.get("note")));
            return Map.of();
        });

    }

    @PostMapping("/api/autofix/callback")
    public ResponseEntity<Map<String, Object>> callback(@RequestHeader(value = "X-QA-Token", required = false) String token,
                                                        @RequestBody Map<String, Object> req) {

        if (!qaBotService.tokenOk(token)) return fail(HttpStatus.UNAUTHORIZED, "토큰이 맞지 않습니다.");
        return run("AI 수정 콜백", () -> {
            autofixService.callback(req);
            return Map.of();
        });

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
        } catch (IllegalStateException e) {
            log.warn("{} 실패: {}", what, e.getMessage());
            return fail(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (Exception e) {
            log.error("{} 오류", what, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, what + " 중 오류가 발생했어요.");
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
