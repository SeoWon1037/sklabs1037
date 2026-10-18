package com.sklabs1037.sklabs1037.admin.controller;

import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.admin.vo.AdminFeedbackVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * 관리자 피드백 대시보드 Controller
 *
 * API 목록
 *   GET /api/admin/check                       관리자인지 → { admin }
 *   GET /api/admin/feedback                    요약 + 도구별 + 날짜별
 *   GET /api/admin/feedback/chats?rating=DOWN  대화 목록 (rating: UP / DOWN / 없으면 전체)
 *
 * - 로그인 + T_USER.ROLE = 'ADMIN' 인 회원만 (매 요청마다 DB에서 확인)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminFeedbackController {

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    // 세션의 로그인 정보에서 회원 ID를 꺼낼 때 사용
    @Autowired
    private JsonMapper jsonMapper;

    // 관리자인지 (상단바에 "관리자" 메뉴를 보여줄지 정할 때)
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> check(HttpServletRequest request) {

        Map<String, Object> body = new HashMap<>();
        body.put("admin", adminFeedbackService.isAdmin(getLoginUserId(request)));

        return ResponseEntity.ok(body);

    }

    // 요약 + 도구별 + 날짜별
    @GetMapping("/feedback")
    public ResponseEntity<Map<String, Object>> dashboard(HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        Map<String, Object> body = new HashMap<>(adminFeedbackService.getDashboard());
        body.put("success", true);

        return ResponseEntity.ok(body);

    }

    // 대화 목록
    @GetMapping("/feedback/chats")
    public ResponseEntity<Map<String, Object>> chats(@RequestParam(required = false) String rating,
                                                     @RequestParam(defaultValue = "30") int size,
                                                     HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        AdminFeedbackVO param = new AdminFeedbackVO();
        param.setRating("UP".equals(rating) || "DOWN".equals(rating) ? rating : null);   // 정해진 값만
        param.setSize(Math.max(1, Math.min(size, 100)));

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("chats", adminFeedbackService.getChats(param));

        return ResponseEntity.ok(body);

    }

    // ============================================
    // 공통
    // ============================================

    // 관리자가 아니면 실패 응답, 관리자면 null
    private ResponseEntity<Map<String, Object>> checkAdmin(HttpServletRequest request) {

        Long userId = getLoginUserId(request);

        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");
        if (!adminFeedbackService.isAdmin(userId)) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있습니다.");

        return null;

    }

    // 세션에 저장된 로그인 정보에서 회원 ID 꺼내기
    @SuppressWarnings("unchecked")
    private Long getLoginUserId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null) return null;

        Object loginUser = session.getAttribute(UserLoginController.LOGIN_USER);
        if (loginUser == null) return null;

        Map<String, Object> map = jsonMapper.convertValue(loginUser, Map.class);
        Object id = map.get("id");

        return (id instanceof Number n) ? n.longValue() : null;

    }

    // 실패 응답
    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}