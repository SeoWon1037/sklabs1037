package com.sklabs1037.sklabs1037.mock.controller;

import com.sklabs1037.sklabs1037.mock.service.DiagnosisService;
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
 * 🩺 포트폴리오 AI 진단 (로그인)
 *
 *   GET  /api/mock/diagnosis   마지막 진단 + 오늘 남은 횟수
 *   POST /api/mock/diagnosis   지금 진단 받기 (하루 3번)
 */
@Slf4j
@RestController
@RequestMapping("/api/mock/diagnosis")
public class MockDiagnosisController {

    @Autowired
    private DiagnosisService diagnosisService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> last(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 볼 수 있어요.");

        return run("진단", () -> diagnosisService.last(userId));

    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> diagnose(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 받을 수 있어요.");

        return run("AI 진단", () -> diagnosisService.diagnose(userId));

    }

    private interface Body {
        Map<String, Object> get() throws Exception;
    }

    private ResponseEntity<Map<String, Object>> run(String what, Body body) {

        try {

            Map<String, Object> m = new HashMap<>(body.get());
            m.put("success", true);
            return ResponseEntity.ok(m);

        } catch (IllegalArgumentException | IllegalStateException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("{} 오류", what, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, what + "을(를) 처리하지 못했어요.");

        }

    }

    private Long userId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO user = (session == null) ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);

        return user == null ? null : user.getId();

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
