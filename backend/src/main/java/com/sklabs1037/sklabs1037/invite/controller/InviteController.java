package com.sklabs1037.sklabs1037.invite.controller;

import com.sklabs1037.sklabs1037.invite.service.InviteService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * 🤝 친구 초대 (로그인 필요)
 *   GET  /api/invite                  내 초대 코드 · 초대한 사람들
 *   POST /api/invite/accept { code }  초대 받기 (가입한 지 3일 안, 한 번만)
 */
@Slf4j
@RestController
@RequestMapping("/api/invite")
public class InviteController {

    @Autowired
    private InviteService inviteService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> my(HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        return run("초대 정보", () -> inviteService.my(me));

    }

    @PostMapping("/accept")
    public ResponseEntity<Map<String, Object>> accept(@RequestBody Map<String, Object> body, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        return run("초대 받기", () -> inviteService.accept(me, body.get("code") == null ? null : String.valueOf(body.get("code"))));

    }

    private ResponseEntity<Map<String, Object>> run(String what, Supplier<Map<String, Object>> work) {

        try {
            Map<String, Object> body = new HashMap<>(work.get());
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (NoSuchElementException e) {
            return fail(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("{} 오류", what, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, what + " 중 문제가 생겼어요.");
        }

    }

    private Long userId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute(UserLoginController.LOGIN_USER);
        return user instanceof UserLoginVO vo ? vo.getId() : null;

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);

    }
}
