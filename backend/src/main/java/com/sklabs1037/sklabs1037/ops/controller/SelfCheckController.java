package com.sklabs1037.sklabs1037.ops.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.ops.service.SelfCheckService;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 🧪 자가 점검 (관리자만)
 *   GET /api/admin/selfcheck → { success, ok, at, checks: [{ key, name, status, detail, fix }] }
 *   토스를 실제로 부르는 점검이 있어 몇 초 걸릴 수 있음
 */
@Slf4j
@RestController
public class SelfCheckController {

    @Autowired
    private SelfCheckService selfCheckService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @GetMapping("/api/admin/selfcheck")
    public ResponseEntity<Map<String, Object>> run(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO u = session == null ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);

        Map<String, Object> body = new HashMap<>();
        if (u == null || !adminFeedbackService.isAdmin(u.getId())) {
            body.put("success", false);
            body.put("message", "관리자만 볼 수 있어요.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        try {
            body.putAll(selfCheckService.run());
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("자가 점검 오류", e);
            body.put("success", false);
            body.put("message", "점검 중 문제가 생겼어요.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

    }
}
