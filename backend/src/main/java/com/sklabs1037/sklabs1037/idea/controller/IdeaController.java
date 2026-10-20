package com.sklabs1037.sklabs1037.idea.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.idea.service.IdeaService;
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
 * 💡 기능 제안 + 투표
 *
 *   GET    /api/ideas?sort=TOP|NEW&status=   목록 (누구나)
 *   POST   /api/ideas/check { title, body }  비슷한 제안 + 카테고리 (로그인)
 *   POST   /api/ideas { title, body, category }   제안하기 (로그인, 하루 5개)
 *   POST   /api/ideas/{id}/vote              👍 투표 / 취소 (로그인)
 *   DELETE /api/ideas/{id}                   내 제안 지우기
 *   POST   /api/ideas/{id}/status { status, note }   관리자: 상태 바꾸기
 */
@Slf4j
@RestController
@RequestMapping("/api/ideas")
public class IdeaController {

    @Autowired
    private IdeaService ideaService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String sort,
                                                    @RequestParam(required = false) String status,
                                                    HttpServletRequest request) {

        Long userId = userId(request);

        return run("제안 목록", () -> {
            Map<String, Object> m = new HashMap<>(ideaService.list(userId, sort, status));
            m.put("loggedIn", userId != null);
            m.put("admin", userId != null && adminFeedbackService.isAdmin(userId));
            return m;
        });

    }

    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> check(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        if (userId(request) == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 제안할 수 있어요.");

        return run("비슷한 제안 찾기", () -> ideaService.check(str(req, "title"), str(req, "body")));

    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 제안할 수 있어요.");

        return run("제안하기", () -> ideaService.create(userId, str(req, "title"), str(req, "body"), str(req, "category")));

    }

    @PostMapping("/{id}/vote")
    public ResponseEntity<Map<String, Object>> vote(@PathVariable Long id, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 투표할 수 있어요.");

        return run("투표", () -> ideaService.vote(userId, id));

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("제안 지우기", () -> {
            ideaService.delete(userId, id);
            return Map.of();
        });

    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long id, @RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null || !adminFeedbackService.isAdmin(userId)) return fail(HttpStatus.FORBIDDEN, "관리자만 바꿀 수 있어요.");

        return run("상태 바꾸기", () -> {
            ideaService.changeStatus(id, str(req, "status"), str(req, "note"));
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

    private static String str(Map<String, Object> req, String key) {

        return req == null || req.get(key) == null ? null : String.valueOf(req.get(key));

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
