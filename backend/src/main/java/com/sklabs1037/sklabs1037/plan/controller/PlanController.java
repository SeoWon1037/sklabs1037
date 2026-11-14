package com.sklabs1037.sklabs1037.plan.controller;

import com.sklabs1037.sklabs1037.plan.service.PlanService;
import com.sklabs1037.sklabs1037.plan.service.VoiceBriefService;
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
 * 🗓️ 내 약속 + 🔊 음성 브리핑 Controller (모두 로그인 필요)
 *
 * API 목록
 *   GET  /api/plans                내 약속 (오늘부터)            → { plans: [{ id, title, place, date, time, day, weekday, text }] }
 *   POST /api/plans                약속 추가 { title, date, time?, place?, shared?(💕 커플과 함께), weekly?(🔁 매주 12주) } → { plan }
 *   POST /api/plans/{id}/delete    약속 지우기 (?all=true: 반복 약속이면 그 약속부터 뒤의 것까지)
 *   GET  /api/plans/past                    지난 90일 약속 중 사진이 있는 것
 *   POST /api/plans/{id}/photos             📷 약속에 사진 올리기 { image(data URL) } (약속마다 4장, 3MB)
 *   POST /api/plans/photos/{photoId}/delete 사진 지우기 (올린 사람 · 약속을 만든 사람)
 *   GET  /api/plans/photos/{photoId}/image  사진 (만든 사람 + 함께하는 약속의 상대만)
 *   GET  /api/voice/settings       음성 브리핑 설정              → { morning, close, region, regions }
 *   POST /api/voice/settings       { morning, close, region }
 *   GET  /api/voice/brief?kind=    지금 읽을 문장 (morning | close | plan) → { title, text }
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class PlanController {

    @Autowired
    private PlanService planService;

    @Autowired
    private VoiceBriefService voiceBriefService;

    @GetMapping("/plans")
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("약속 목록", () -> Map.of("plans", planService.upcoming(userId)));

    }

    @PostMapping("/plans")
    public ResponseEntity<Map<String, Object>> add(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("약속 추가", () -> Map.of("plan", planService.add(userId, req.get("title"), req.get("date"), req.get("time"), req.get("place"),
                com.sklabs1037.sklabs1037.plan.service.PlanRules.yes(req.get("shared")), com.sklabs1037.sklabs1037.plan.service.PlanRules.yes(req.get("weekly")))));

    }

    @PostMapping("/plans/{id}/delete")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean all, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("약속 지우기", () -> {
            return Map.of("deleted", planService.delete(userId, id, all));
        });

    }

    // ===== 📷 약속 사진 =====
    @GetMapping("/plans/past")
    public ResponseEntity<Map<String, Object>> past(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("지난 약속", () -> Map.of("plans", planService.pastWithPhotos(userId)));

    }

    @PostMapping("/plans/{id}/photos")
    public ResponseEntity<Map<String, Object>> addPhoto(@PathVariable Long id, @RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("사진 올리기", () -> {
            planService.addPhoto(userId, id, req.get("image") == null ? null : String.valueOf(req.get("image")));
            return Map.of();
        });

    }

    @PostMapping("/plans/photos/{photoId}/delete")
    public ResponseEntity<Map<String, Object>> deletePhoto(@PathVariable Long photoId, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("사진 지우기", () -> {
            planService.deletePhoto(userId, photoId);
            return Map.of();
        });

    }

    // 사진 그림 (볼 수 없는 사람이면 404, 브라우저에만 하루 저장)
    @GetMapping("/plans/photos/{photoId}/image")
    public void photo(@PathVariable Long photoId, HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

        Long userId = userId(request);
        Map<String, Object> p = userId == null ? null : planService.photo(userId, photoId);

        if (p == null || !(p.get("data") instanceof byte[] data)) {
            response.setStatus(404);
            return;
        }

        response.setContentType(String.valueOf(p.get("contentType")));
        response.setHeader("Cache-Control", "private, max-age=86400");
        response.setContentLength(data.length);
        response.getOutputStream().write(data);

    }

    @GetMapping("/voice/settings")
    public ResponseEntity<Map<String, Object>> settings(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("음성 브리핑 설정", () -> voiceBriefService.settings(userId));

    }

    @PostMapping("/voice/settings")
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("음성 브리핑 설정", () -> voiceBriefService.save(userId,
                Boolean.TRUE.equals(req.get("morning")), Boolean.TRUE.equals(req.get("close")), req.get("region")));

    }

    @GetMapping("/voice/brief")
    public ResponseEntity<Map<String, Object>> brief(@RequestParam(defaultValue = "morning") String kind, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인하면 브리핑을 들을 수 있어요.");

        return run("음성 브리핑", () -> voiceBriefService.brief(userId, kind));

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
