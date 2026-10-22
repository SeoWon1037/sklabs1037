package com.sklabs1037.sklabs1037.ops.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.ops.service.ServerMonitorService;
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
 * 🖥 서버 상태 (관리자만)
 *   GET /api/admin/server   지금 값 + 최근 1시간(5초 간격) + DB 정보 · 연결 상태 · 큰 테이블
 *   화면이 5초마다 다시 부름 (값은 서버가 이미 재 둔 것 → 화면 여러 개를 열어도 DB 부담 같음)
 */
@Slf4j
@RestController
public class ServerMonitorController {

    @Autowired
    private ServerMonitorService serverMonitorService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @GetMapping("/api/admin/server")
    public ResponseEntity<Map<String, Object>> server(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO u = session == null ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);
        if (u == null || !adminFeedbackService.isAdmin(u.getId())) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", "관리자만 볼 수 있어요.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        try {
            Map<String, Object> body = new HashMap<>(serverMonitorService.snapshot());
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("서버 상태 오류", e);
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", "서버 상태를 불러오지 못했어요.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

    }
}
