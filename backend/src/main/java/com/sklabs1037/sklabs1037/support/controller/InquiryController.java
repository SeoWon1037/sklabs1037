package com.sklabs1037.sklabs1037.support.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.support.service.InquiryService;
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
 * 📮 문의함
 *
 * 사용자 (로그인)
 *   POST /api/support { category, title, body, page }   문의 남기기 (하루 5개)
 *   GET  /api/support/mine                              내 문의 + 답변
 * 관리자
 *   GET  /api/admin/inquiries?status=OPEN|ANSWERED|CLOSED   목록 (없으면 전체 100개)
 *   GET  /api/admin/inquiries/count                    답변 기다리는 수 (탭 배지)
 *   POST /api/admin/inquiries/{id}/reply { reply, close }
 *   POST /api/admin/inquiries/{id}/status { status }
 *   POST /api/admin/inquiries/{id}/retriage            AI 분류 다시
 */
@Slf4j
@RestController
public class InquiryController {

    @Autowired
    private InquiryService inquiryService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    // ===== 사용자 =====
    @PostMapping("/api/support")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 문의할 수 있어요.");
        return run("문의 남기기", () -> inquiryService.create(me, str(req.get("category")), str(req.get("title")), str(req.get("body")),
                str(req.get("page")), request.getHeader("User-Agent")));

    }

    @GetMapping("/api/support/mine")
    public ResponseEntity<Map<String, Object>> mine(HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 볼 수 있어요.");
        return run("내 문의", () -> Map.of("inquiries", inquiryService.mine(me)));

    }

    // ===== 관리자 =====
    @GetMapping("/api/admin/inquiries")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String status, HttpServletRequest request) {

        if (admin(request) == null) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있어요.");
        return run("문의 목록", () -> inquiryService.adminList(status));

    }

    @GetMapping("/api/admin/inquiries/count")
    public ResponseEntity<Map<String, Object>> count(HttpServletRequest request) {

        if (admin(request) == null) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있어요.");
        return run("문의 수", () -> Map.of("count", inquiryService.openCount()));

    }

    @PostMapping("/api/admin/inquiries/{id}/reply")
    public ResponseEntity<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long adminId = admin(request);
        if (adminId == null) return fail(HttpStatus.FORBIDDEN, "관리자만 할 수 있어요.");
        return run("답변", () -> {
            inquiryService.reply(adminId, id, str(req.get("reply")), Boolean.TRUE.equals(req.get("close")));
            return Map.of();
        });

    }

    @PostMapping("/api/admin/inquiries/{id}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long id, @RequestBody Map<String, Object> req, HttpServletRequest request) {

        if (admin(request) == null) return fail(HttpStatus.FORBIDDEN, "관리자만 할 수 있어요.");
        return run("상태 바꾸기", () -> {
            inquiryService.changeStatus(id, str(req.get("status")));
            return Map.of();
        });

    }

    @PostMapping("/api/admin/inquiries/{id}/retriage")
    public ResponseEntity<Map<String, Object>> retriage(@PathVariable Long id, HttpServletRequest request) {

        if (admin(request) == null) return fail(HttpStatus.FORBIDDEN, "관리자만 할 수 있어요.");
        return run("AI 분류", () -> {
            inquiryService.retriage(id);
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

    private Long admin(HttpServletRequest request) {

        Long id = userId(request);
        return id != null && adminFeedbackService.isAdmin(id) ? id : null;

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);

    }
}
