package com.sklabs1037.sklabs1037.ops.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.ops.service.ClientErrorService;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 🐞 화면 오류
 *   POST /api/track/error { kind, message, stack, path }       화면이 보냄 (누구나, IP당 10분 20번 — RateLimitGuard)
 *   GET  /api/admin/client-errors?status=OPEN                  관리자 목록
 *   POST /api/admin/client-errors/{id}/status { status }       OPEN / DONE / IGNORED
 */
@Slf4j
@RestController
public class ClientErrorController {

    @Autowired
    private ClientErrorService clientErrorService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    // 화면 쪽은 결과를 쓰지 않음 → 무슨 일이 있어도 조용히 200 (오류 보고가 또 오류를 만들지 않게)
    @PostMapping("/api/track/error")
    public ResponseEntity<Map<String, Object>> report(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {

        Map<String, Object> out = new HashMap<>();
        try {
            out.put("saved", clientErrorService.report(body, request.getHeader("User-Agent")));
        } catch (Exception e) {
            log.warn("화면 오류 저장 실패: {}", e.getMessage());
            out.put("saved", false);
        }
        out.put("success", true);
        return ResponseEntity.ok(out);

    }

    @GetMapping("/api/admin/client-errors")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String status, HttpServletRequest request) {

        if (!admin(request)) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있어요.");
        try {
            Map<String, Object> body = new HashMap<>(clientErrorService.list(status));
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("화면 오류 목록 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "불러오지 못했어요. (DB에 T_CLIENT_ERROR 테이블이 있는지 확인)");
        }

    }

    @PostMapping("/api/admin/client-errors/{id}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {

        if (!admin(request)) return fail(HttpStatus.FORBIDDEN, "관리자만 할 수 있어요.");
        try {
            clientErrorService.status(id, body.get("status") == null ? null : String.valueOf(body.get("status")));
            Map<String, Object> out = new HashMap<>();
            out.put("success", true);
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        }

    }

    private boolean admin(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO u = session == null ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);
        return u != null && adminFeedbackService.isAdmin(u.getId());

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);

    }
}
