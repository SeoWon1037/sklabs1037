package com.sklabs1037.sklabs1037.qa.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
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
 * 🤖 AI QA 봇
 *
 * API 목록
 *   POST /api/qa/report          GitHub Actions가 점검 결과를 보냄 (헤더 X-QA-Token) → { success, runId }
 *   GET  /api/admin/qa           최근 점검 목록 (관리자)
 *   GET  /api/admin/qa/{runId}   점검 하나 + 화면별 측정값 + 캡처 (관리자)
 */
@Slf4j
@RestController
public class QaController {

    @Autowired
    private QaBotService qaBotService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @PostMapping("/api/qa/report")
    public ResponseEntity<Map<String, Object>> report(@RequestHeader(value = "X-QA-Token", required = false) String token,
                                                      @RequestBody Map<String, Object> payload) {

        if (!qaBotService.configured()) return fail(HttpStatus.SERVICE_UNAVAILABLE, "QA_TOKEN이 설정되지 않았습니다.");
        if (!qaBotService.tokenOk(token)) return fail(HttpStatus.UNAUTHORIZED, "토큰이 맞지 않습니다.");

        try {

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("runId", qaBotService.accept(payload));

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("QA 결과 저장 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "저장하지 못했습니다.");

        }

    }

    @GetMapping("/api/admin/qa")
    public ResponseEntity<Map<String, Object>> runs(HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        try {

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("runs", qaBotService.runs());
            body.put("configured", qaBotService.configured());

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("QA 목록 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "불러오지 못했습니다. (sql/qa-bot.sql 실행 여부 확인)");

        }

    }

    @GetMapping("/api/admin/qa/{runId}")
    public ResponseEntity<Map<String, Object>> run(@PathVariable Long runId, HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        try {

            Map<String, Object> body = new HashMap<>(qaBotService.run(runId));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.NOT_FOUND, e.getMessage());

        }

    }

    // ============================================
    // 공통
    // ============================================
    private ResponseEntity<Map<String, Object>> checkAdmin(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO loginUser = (session == null) ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);

        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");
        if (!adminFeedbackService.isAdmin(loginUser.getId())) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있습니다.");

        return null;

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
