package com.sklabs1037.sklabs1037.brain.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.brain.service.BrainImportService;
import com.sklabs1037.sklabs1037.brain.service.BrainService;
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
 * 🧠 세컨드 브레인 Controller (모두 로그인 필요, 본인 노트만)
 *
 * API 목록
 *   GET  /api/brain/notes?kind=&q=        목록 (RAW · WIKI · OUTPUT, 찾을 말)  → { notes: [{ id, kind, title, topics, snippet, updatedAt }] }
 *   GET  /api/brain/notes/{id}            노트 하나 + 출처(sources) · 이걸로 만든 것(usedBy)
 *   POST /api/brain/notes                 새 노트 { kind?(RAW|OUTPUT), title?, body, topics?, fromId? } → { note }
 *   POST /api/brain/notes/{id}            고치기 { title?, body, topics? } → { note }
 *   POST /api/brain/notes/{id}/delete     지우기
 *   GET  /api/brain/graph                 그래프 { nodes, hubs, edges }
 *   POST /api/brain/organize              🤖 AI 정리 { query } → { note } (WIKI, 하루 20번)
 *   POST /api/brain/sample                예시 메모 6개 (노트가 없을 때만)
 *   GET  /api/brain/import                📥 SKLabs 스프링 코드 기록 상태 { admin, available, imported, on }
 *   POST /api/brain/import                📥 받기 (관리자만) → { added, updated } — 그 뒤로는 배포 때마다 자동
 */
@Slf4j
@RestController
@RequestMapping("/api/brain")
public class BrainController {

    @Autowired
    private BrainService brainService;

    @Autowired
    private BrainImportService brainImportService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @GetMapping("/notes")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String kind, @RequestParam(required = false) String q, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("노트 목록", () -> Map.of("notes", brainService.list(userId, kind, q)));

    }

    @GetMapping("/notes/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("노트", () -> Map.of("note", brainService.get(userId, id)));

    }

    @PostMapping("/notes")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("노트 저장", () -> Map.of("note", brainService.create(userId, req.get("kind"), req.get("title"), req.get("body"), req.get("topics"), req.get("fromId"))));

    }

    @PostMapping("/notes/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("노트 고치기", () -> Map.of("note", brainService.update(userId, id, req.get("title"), req.get("body"), req.get("topics"))));

    }

    @PostMapping("/notes/{id}/delete")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("노트 지우기", () -> {
            brainService.delete(userId, id);
            return Map.of();
        });

    }

    @GetMapping("/graph")
    public ResponseEntity<Map<String, Object>> graph(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("그래프", () -> brainService.graph(userId));

    }

    @PostMapping("/organize")
    public ResponseEntity<Map<String, Object>> organize(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("AI 정리", () -> Map.of("note", brainService.organize(userId, req.get("query"))));

    }

    @PostMapping("/sample")
    public ResponseEntity<Map<String, Object>> sample(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("예시 메모", () -> Map.of("added", brainService.sample(userId)));

    }

    // 📥 SKLabs 스프링 코드 기록 (사이트를 만든 사람 = 관리자만)
    @GetMapping("/import")
    public ResponseEntity<Map<String, Object>> importStatus(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        if (!adminFeedbackService.isAdmin(userId)) return run("코드 기록", () -> Map.of("admin", false));

        return run("코드 기록", () -> {
            Map<String, Object> m = new HashMap<>(brainImportService.status(userId));
            m.put("admin", true);
            return m;
        });

    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importNotes(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        if (!adminFeedbackService.isAdmin(userId)) return fail(HttpStatus.FORBIDDEN, "관리자만 받을 수 있어요.");

        return run("코드 기록 받기", () -> brainImportService.sync(userId));

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
