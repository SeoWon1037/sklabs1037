package com.sklabs1037.sklabs1037.duel.controller;

import com.sklabs1037.sklabs1037.duel.service.DuelService;
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
 * ⚔️ 친구 대결
 *
 *   GET  /api/duel                  내 대결 목록 (로그인)
 *   POST /api/duel                  대결 만들기 → { code } (로그인)
 *   GET  /api/duel/{code}           대결 보기 (누구나, 로그인하면 내 역할·받기 가능 여부)
 *   POST /api/duel/{code}/accept    대결 받기 (로그인)
 *   POST /api/duel/{code}/cancel    아무도 안 받은 내 초대 취소 (로그인)
 */
@Slf4j
@RestController
@RequestMapping("/api/duel")
public class DuelController {

    @Autowired
    private DuelService duelService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> mine(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return ok(Map.of("loggedIn", false));

        return run("대결 목록", () -> Map.of("loggedIn", true, "duels", duelService.mine(userId)));

    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 대결을 만들 수 있어요.");

        return run("대결 만들기", () -> duelService.create(userId));

    }

    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> view(@PathVariable String code, HttpServletRequest request) {

        Long userId = userId(request);

        return run("대결", () -> {
            Map<String, Object> duel = duelService.view(code, userId);
            if (duel == null) throw new IllegalArgumentException("대결을 찾을 수 없어요.");
            return Map.of("duel", duel);
        });

    }

    @PostMapping("/{code}/accept")
    public ResponseEntity<Map<String, Object>> accept(@PathVariable String code, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 대결을 받을 수 있어요.");

        return run("대결 받기", () -> Map.of("duel", duelService.accept(code, userId)));

    }

    @PostMapping("/{code}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String code, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("대결 취소", () -> {
            duelService.cancel(code, userId);
            return Map.of();
        });

    }

    // ============================================
    // 공통
    // ============================================
    private interface Body {
        Map<String, Object> get() throws Exception;
    }

    private ResponseEntity<Map<String, Object>> run(String what, Body body) {

        try {

            return ok(body.get());

        } catch (IllegalArgumentException | IllegalStateException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("{} 오류", what, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, what + "을(를) 처리하지 못했어요.");

        }

    }

    private ResponseEntity<Map<String, Object>> ok(Map<String, Object> data) {

        Map<String, Object> m = new HashMap<>(data);
        m.put("success", true);
        return ResponseEntity.ok(m);

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
