package com.sklabs1037.sklabs1037.ops.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.ops.service.ApiCallLogService;
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
 * 🔌 외부 연동 현황 (관리자만)
 *   GET /api/admin/api-calls   오늘 서비스별 · 최근 15분 · 최근 24시간 시간대별 · 기능별 · 최근 실패
 */
@Slf4j
@RestController
public class ApiCallLogController {

    @Autowired
    private ApiCallLogService apiCallLogService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @GetMapping("/api/admin/api-calls")
    public ResponseEntity<Map<String, Object>> apiCalls(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO u = session == null ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);
        if (u == null || !adminFeedbackService.isAdmin(u.getId())) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", "관리자만 볼 수 있어요.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        try {
            Map<String, Object> body = new HashMap<>(apiCallLogService.dashboard());
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("외부 연동 현황 오류", e);
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", "외부 연동 현황을 불러오지 못했어요. (DB에 T_API_CALL_LOG 테이블이 있는지 확인)");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

    }
}
