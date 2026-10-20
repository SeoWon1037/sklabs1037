package com.sklabs1037.sklabs1037.couple.controller;

import com.sklabs1037.sklabs1037.couple.service.CouplePhotoService;
import com.sklabs1037.sklabs1037.couple.service.CoupleService;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 💕 커플 맺기 (모두 로그인)
 *
 *   GET  /api/couple                     내 커플 + 보낸·받은 신청
 *   GET  /api/couple/summary             메인용 짧은 정보 { couple: {partnerName, days, next}, received }
 *   POST /api/couple/request { nickname, startDate }   신청
 *   POST /api/couple/{id}/accept | decline | cancel
 *   POST /api/couple/end                 커플 끊기
 *   POST /api/couple/heart               💗 하트 날리기 (커플끼리만) → { sent: {total, today} }
 *   GET  /api/couple/photos              📸 기념일 앨범 (칸 + 사진 목록)
 *   POST /api/couple/photos { milestone, caption, image(base64) }   사진 올리기
 *   POST /api/couple/photos/{id}/delete  사진 지우기
 *   GET  /api/couple/photos/{id}/image   사진 (같은 커플만)
 */
@Slf4j
@RestController
@RequestMapping("/api/couple")
public class CoupleController {

    @Autowired
    private CoupleService coupleService;

    @Autowired
    private CouplePhotoService couplePhotoService;

    // ===== 📸 기념일 앨범 =====
    @GetMapping("/photos")
    public ResponseEntity<Map<String, Object>> album(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("앨범", () -> couplePhotoService.album(userId));

    }

    @PostMapping("/photos")
    public ResponseEntity<Map<String, Object>> upload(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("사진 올리기", () -> couplePhotoService.upload(userId, str(req, "milestone"), str(req, "caption"), str(req, "image")));

    }

    @PostMapping("/photos/{photoId}/delete")
    public ResponseEntity<Map<String, Object>> deletePhoto(@PathVariable Long photoId, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("사진 지우기", () -> couplePhotoService.delete(userId, photoId));

    }

    // 사진 그림 (같은 커플이 아니면 404, 브라우저에만 하루 저장)
    @GetMapping("/photos/{photoId}/image")
    public void image(@PathVariable Long photoId, HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {

        Long userId = userId(request);
        Map<String, Object> p = userId == null ? null : couplePhotoService.image(userId, photoId);

        if (p == null || !(p.get("data") instanceof byte[] data)) {
            response.setStatus(404);
            return;
        }

        response.setContentType(String.valueOf(p.get("contentType")));
        response.setHeader("Cache-Control", "private, max-age=86400");
        response.setContentLength(data.length);
        response.getOutputStream().write(data);

    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> mine(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 볼 수 있어요.");

        return run("커플", () -> coupleService.mine(userId));

    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 볼 수 있어요.");

        return run("커플 요약", () -> coupleService.summary(userId));

    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> request(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 신청할 수 있어요.");

        return run("커플 신청", () -> {
            coupleService.request(userId, str(req, "nickname"), str(req, "startDate"));
            return coupleService.mine(userId);
        });

    }

    @PostMapping("/{id}/{action}")
    public ResponseEntity<Map<String, Object>> act(@PathVariable Long id, @PathVariable String action, HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("커플", () -> {
            switch (action) {
                case "accept" -> coupleService.accept(userId, id);
                case "decline" -> coupleService.decline(userId, id);
                case "cancel" -> coupleService.cancel(userId, id);
                default -> throw new IllegalArgumentException("알 수 없는 요청이에요.");
            }
            return coupleService.mine(userId);
        });

    }

    @PostMapping("/heart")
    public ResponseEntity<Map<String, Object>> heart(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("하트", () -> coupleService.heart(userId));

    }

    @PostMapping("/end")
    public ResponseEntity<Map<String, Object>> end(HttpServletRequest request) {

        Long userId = userId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");

        return run("커플 끊기", () -> {
            coupleService.end(userId);
            return coupleService.mine(userId);
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
