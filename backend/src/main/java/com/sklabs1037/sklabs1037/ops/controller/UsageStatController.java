package com.sklabs1037.sklabs1037.ops.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.ops.service.UsageStatService;
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
 * 📊 사용 통계
 *   POST /api/track          { p: 주소, v: 방문자 번호 }  누구나 (화면 열 때마다, 응답 내용 없음)
 *   GET  /api/admin/stats    관리자: 30일 방문자·페이지뷰 · 인기 화면 · 가입 · 기능별 · 체험 → 가입
 */
@Slf4j
@RestController
public class UsageStatController {

    @Autowired
    private UsageStatService usageStatService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @PostMapping("/api/track")
    public ResponseEntity<Void> track(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> body) {

        if (body != null) {
            usageStatService.pageView(String.valueOf(body.get("p")), body.get("v") == null ? null : String.valueOf(body.get("v")),
                    request.getHeader("User-Agent"));
        }
        return ResponseEntity.noContent().build();

    }

    @GetMapping("/api/admin/stats")
    public ResponseEntity<Map<String, Object>> stats(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO u = session == null ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);
        Map<String, Object> body = new HashMap<>();
        if (u == null || !adminFeedbackService.isAdmin(u.getId())) {
            body.put("success", false);
            body.put("message", "관리자만 볼 수 있어요.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        try {
            body.putAll(usageStatService.dashboard());
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("사용 통계 오류", e);
            body.put("success", false);
            body.put("message", "불러오지 못했어요. (DB에 T_DAILY_STAT 테이블이 있는지 확인)");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

    }
}
