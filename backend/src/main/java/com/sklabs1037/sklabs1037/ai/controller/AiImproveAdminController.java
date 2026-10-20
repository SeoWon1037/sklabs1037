package com.sklabs1037.sklabs1037.ai.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.ai.service.PromptImproveService;
import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
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
 * 관리자: 🔁 스스로 고치는 AI 비서
 *
 * API 목록 (관리자만, 매 요청마다 DB에서 ROLE 확인)
 *   GET  /api/admin/ai-improve                     지금 버전, 개선안 목록(분석·변경점·채점), 버전별 👍/👎
 *   POST /api/admin/ai-improve/run                 지금 개선 실행 (별도 스레드, 작업 AI_IMPROVE)
 *   POST /api/admin/ai-improve/{version}/activate  이 버전 적용 (0 = 기본 프롬프트로 되돌리기)
 *   POST /api/admin/ai-improve/{version}/reject    개선안 거절
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai-improve")
public class AiImproveAdminController {

    @Autowired
    private PromptImproveService promptImproveService;

    @Autowired
    private OpsJobs opsJobs;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> overview(HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        try {

            Map<String, Object> body = new HashMap<>(promptImproveService.overview());
            body.put("running", opsJobs.runningJobs().contains("AI_IMPROVE"));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("AI 개선 화면 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "불러오지 못했습니다. (sql/ai-prompt.sql 실행 여부 확인)");

        }

    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("started", opsJobs.runAsync("AI_IMPROVE"));

        return ResponseEntity.ok(body);

    }

    @PostMapping("/{version}/activate")
    public ResponseEntity<Map<String, Object>> activate(@PathVariable int version, HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        try {

            promptImproveService.activate(version, getLoginUser(request).getId());
            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        }

    }

    @PostMapping("/{version}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable int version, HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        try {

            promptImproveService.reject(version, getLoginUser(request).getId());
            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        }

    }

    // ============================================
    // 공통
    // ============================================
    private ResponseEntity<Map<String, Object>> checkAdmin(HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);

        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");
        if (!adminFeedbackService.isAdmin(loginUser.getId())) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있습니다.");

        return null;

    }

    private UserLoginVO getLoginUser(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        return (session == null) ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);

    }

    private ResponseEntity<Map<String, Object>> ok() {

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);

        return ResponseEntity.ok(body);

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
