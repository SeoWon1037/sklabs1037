package com.sklabs1037.sklabs1037.dm.controller;

import com.sklabs1037.sklabs1037.dm.service.DmLiveHub;
import com.sklabs1037.sklabs1037.dm.service.DmService;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

/**
 * 💬 1:1 메시지 (전부 로그인)
 *
 *   GET  /api/dm/stream                        실시간 (SSE: msg · read)
 *   GET  /api/dm/unread                        안 읽은 메시지 수 (상단 💬)
 *   GET  /api/dm/users?q=닉                    상대 찾기 (앞부분 일치, 10명)
 *   GET  /api/dm/rooms                         내 대화방 목록
 *   POST /api/dm/rooms { userId }              대화방 열기 (없으면 만들기) → { roomId }
 *   GET  /api/dm/rooms/{id}?before=메시지ID      메시지 40개씩 (위로 스크롤하면 이전)
 *   POST /api/dm/rooms/{id}/messages { body }  보내기
 *   POST /api/dm/rooms/{id}/read { lastId }    여기까지 읽음
 *   POST /api/dm/rooms/{id}/block { on }       상대 차단 / 풀기
 */
@Slf4j
@RestController
@RequestMapping("/api/dm")
public class DmController {

    @Autowired
    private DmService dmService;

    @Autowired
    private DmLiveHub hub;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        SseEmitter emitter = hub.subscribe(me);
        if (emitter == null) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();

        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .header("Cache-Control", "no-cache")
                .body(emitter);

    }

    @GetMapping("/unread")
    public ResponseEntity<Map<String, Object>> unread(HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return ok(Map.of("unread", 0));
        return run("안 읽은 메시지", () -> Map.of("unread", dmService.unread(me)));

    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> users(@RequestParam(required = false) String q, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return login();
        return run("회원 찾기", () -> Map.of("users", dmService.search(me, q)));

    }

    @GetMapping("/rooms")
    public ResponseEntity<Map<String, Object>> rooms(HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return login();
        return run("대화방 목록", () -> dmService.rooms(me));

    }

    @PostMapping("/rooms")
    public ResponseEntity<Map<String, Object>> open(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return login();
        return run("대화방 열기", () -> dmService.open(me, num(req.get("userId"))));

    }

    @GetMapping("/rooms/{id}")
    public ResponseEntity<Map<String, Object>> messages(@PathVariable Long id, @RequestParam(required = false) Long before, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return login();
        return run("메시지", () -> dmService.messages(me, id, before));

    }

    @PostMapping("/rooms/{id}/messages")
    public ResponseEntity<Map<String, Object>> send(@PathVariable Long id, @RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return login();
        return run("메시지 보내기", () -> dmService.send(me, id, req.get("body") == null ? null : String.valueOf(req.get("body"))));

    }

    @PostMapping("/rooms/{id}/read")
    public ResponseEntity<Map<String, Object>> read(@PathVariable Long id, @RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return login();
        return run("읽음", () -> dmService.read(me, id, num(req.get("lastId"))));

    }

    @PostMapping("/rooms/{id}/block")
    public ResponseEntity<Map<String, Object>> block(@PathVariable Long id, @RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return login();
        return run("차단", () -> {
            dmService.block(me, id, Boolean.TRUE.equals(req.get("on")));
            return Map.of();
        });

    }

    // ============================================
    // 공통
    // ============================================
    private interface Body {
        Map<String, Object> get();
    }

    private ResponseEntity<Map<String, Object>> run(String what, Body body) {

        try {
            return ok(body.get());
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("{} 오류", what, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, what + " 중 오류가 발생했어요.");
        }

    }

    private static ResponseEntity<Map<String, Object>> ok(Map<String, Object> m) {

        Map<String, Object> out = new HashMap<>(m);
        out.put("success", true);
        return ResponseEntity.ok(out);

    }

    private ResponseEntity<Map<String, Object>> login() {

        return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있어요.");

    }

    private static Long num(Object o) {

        if (o instanceof Number n) return n.longValue();
        try {
            return o == null ? null : Long.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }

    }

    private Long userId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO u = session == null ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);
        return u == null ? null : u.getId();

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);

    }
}
