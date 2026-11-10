package com.sklabs1037.sklabs1037.market.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.market.service.MarketService;
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
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * 🛍️ 중고거래 장터
 *   GET  /api/market/meta                         카테고리 · 상품 상태 · 거래 방법 목록 (누구나)
 *   GET  /api/market/items?category=&q=&sort=&sale=&free=&page=   목록 (누구나, 24개씩)
 *   GET  /api/market/items/{id}                   상세 (누구나)
 *   GET  /api/market/photos/{id}?thumb=1          사진 (누구나, 1년 캐시)
 *   GET  /api/market/my                           내 판매 목록 + 찜 (로그인)
 *   POST /api/market/items                        올리기 { category,title,price,description,cond,tradeType,region,photos[{image,thumb}] }
 *   POST /api/market/items/{id}                   고치기 (+ keepPhotoIds[])
 *   POST /api/market/items/{id}/status { status } 판매중 / 예약중 / 판매완료
 *   POST /api/market/items/{id}/bump              끌어올리기 (24시간에 한 번)
 *   POST /api/market/items/{id}/delete            지우기 (판매자 또는 관리자)
 *   POST /api/market/items/{id}/like { on }       찜
 *   POST /api/market/items/{id}/chat              판매자와 채팅 시작 → { roomId } → /messages?room=
 *   POST /api/market/items/{id}/report { reason } 신고
 *   POST /api/market/ai-write { name, memo }      ✨ AI 글쓰기 도우미
 *   GET  /api/market/users/{id}                   판매자 프로필: 매너 온도 · 받은 후기 · 판매 상품 (누구나)
 *   GET  /api/market/items/{id}/buyers            판매완료 때 고를 "거래한 사람" 후보 (판매자)
 *   POST /api/market/items/{id}/sold { buyerId }  판매완료 + 거래한 사람 (buyerId 없어도 됨)
 *   POST /api/market/items/{id}/review { rating, tags[], comment }   거래 후기 (판매자 ↔ 거래한 사람)
 *   POST /api/market/keywords { keyword }         🔔 키워드 알림 등록 (10개까지)
 *   POST /api/market/keywords/delete { keyword }  키워드 지우기
 *   GET  /api/market/admin/reported               🛠 신고된 · 숨겨진 상품 (관리자)
 *   POST /api/market/admin/{id}/restore           🛠 숨김 풀기 + 신고 기록 지움 (관리자)
 */
@Slf4j
@RestController
@RequestMapping("/api/market")
public class MarketController {

    @Autowired
    private MarketService marketService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    // ============================================
    // 누구나
    // ============================================
    @GetMapping("/meta")
    public ResponseEntity<Map<String, Object>> meta() {

        return run("장터 정보", marketService::meta);

    }

    @GetMapping("/items")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String category,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(required = false) String sort,
                                                    @RequestParam(defaultValue = "false") boolean sale,
                                                    @RequestParam(defaultValue = "false") boolean free,
                                                    @RequestParam(defaultValue = "0") int page) {

        return run("상품 목록", () -> marketService.list(category, q, sort, sale, free, page));

    }

    @GetMapping("/items/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest request) {

        Long me = userId(request);
        return run("상품", () -> marketService.detail(me, id, isAdmin(me)));

    }

    // 사진: 한 번 올리면 안 바뀌므로 브라우저·CDN이 1년 저장
    @GetMapping("/photos/{photoId}")
    public void photo(@PathVariable Long photoId, @RequestParam(defaultValue = "false") boolean thumb,
                      HttpServletResponse response) throws java.io.IOException {

        Map<String, Object> p = marketService.photo(photoId, thumb);
        if (p == null || !(p.get("data") instanceof byte[] data)) {
            response.setStatus(404);
            return;
        }

        response.setContentType(String.valueOf(p.get("contentType")));
        response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLength(data.length);
        response.getOutputStream().write(data);

    }

    // ============================================
    // 로그인
    // ============================================
    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> my(HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 볼 수 있어요.");
        return run("내 상품", () -> marketService.my(me));

    }

    @PostMapping("/items")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 올릴 수 있어요.");
        return run("상품 올리기", () -> marketService.create(me, body));

    }

    @PostMapping("/items/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        return run("상품 고치기", () -> marketService.update(me, id, body));

    }

    @PostMapping("/items/{id}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        return run("상태 바꾸기", () -> {
            marketService.status(me, id, body.get("status") == null ? null : String.valueOf(body.get("status")));
            return Map.of();
        });

    }

    @PostMapping("/items/{id}/bump")
    public ResponseEntity<Map<String, Object>> bump(@PathVariable Long id, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        return run("끌어올리기", () -> {
            marketService.bump(me, id);
            return Map.of();
        });

    }

    @PostMapping("/items/{id}/delete")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        return run("상품 지우기", () -> {
            marketService.delete(me, id, isAdmin(me));
            return Map.of();
        });

    }

    @PostMapping("/items/{id}/like")
    public ResponseEntity<Map<String, Object>> like(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 찜할 수 있어요.");
        return run("찜", () -> marketService.like(me, id, Boolean.TRUE.equals(body.get("on"))));

    }

    @PostMapping("/items/{id}/chat")
    public ResponseEntity<Map<String, Object>> chat(@PathVariable Long id, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 채팅할 수 있어요.");
        return run("채팅 시작", () -> marketService.chat(me, id));

    }

    @PostMapping("/items/{id}/report")
    public ResponseEntity<Map<String, Object>> report(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 신고할 수 있어요.");
        return run("신고", () -> marketService.report(me, id, body.get("reason") == null ? null : String.valueOf(body.get("reason"))));

    }

    @PostMapping("/ai-write")
    public ResponseEntity<Map<String, Object>> aiWrite(@RequestBody Map<String, Object> body, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 쓸 수 있어요.");
        return run("AI 글쓰기", () -> marketService.aiWrite(me, body.get("name") == null ? null : String.valueOf(body.get("name")),
                body.get("memo") == null ? null : String.valueOf(body.get("memo"))));

    }

    // ============================================
    // ⭐ 거래 후기 · 프로필 · 🔔 키워드
    // ============================================
    @GetMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> profile(@PathVariable Long userId) {

        return run("프로필", () -> marketService.profile(userId));

    }

    @GetMapping("/items/{id}/buyers")
    public ResponseEntity<Map<String, Object>> buyers(@PathVariable Long id, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        return run("거래한 사람 목록", () -> marketService.buyers(me, id));

    }

    @PostMapping("/items/{id}/sold")
    public ResponseEntity<Map<String, Object>> sold(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        return run("판매완료", () -> {
            marketService.sold(me, id, body.get("buyerId") instanceof Number n ? Long.valueOf(n.longValue()) : null);
            return Map.of();
        });

    }

    @PostMapping("/items/{id}/review")
    public ResponseEntity<Map<String, Object>> review(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 후기를 남길 수 있어요.");
        return run("후기 남기기", () -> {
            marketService.review(me, id, body);
            return Map.of();
        });

    }

    @PostMapping("/keywords")
    public ResponseEntity<Map<String, Object>> addKeyword(@RequestBody Map<String, Object> body, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        return run("키워드 등록", () -> marketService.addKeyword(me, body.get("keyword") == null ? null : String.valueOf(body.get("keyword"))));

    }

    @PostMapping("/keywords/delete")
    public ResponseEntity<Map<String, Object>> removeKeyword(@RequestBody Map<String, Object> body, HttpServletRequest request) {

        Long me = userId(request);
        if (me == null) return fail(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        return run("키워드 지우기", () -> marketService.removeKeyword(me, body.get("keyword") == null ? null : String.valueOf(body.get("keyword"))));

    }

    // ============================================
    // 🛠 관리자
    // ============================================
    @GetMapping("/admin/reported")
    public ResponseEntity<Map<String, Object>> reported(HttpServletRequest request) {

        if (!isAdmin(userId(request))) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있어요.");
        return run("신고된 상품", marketService::reported);

    }

    @PostMapping("/admin/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(@PathVariable Long id, HttpServletRequest request) {

        if (!isAdmin(userId(request))) return fail(HttpStatus.FORBIDDEN, "관리자만 할 수 있어요.");
        return run("숨김 풀기", () -> {
            marketService.restore(id);
            return Map.of();
        });

    }

    // ============================================
    // 공통
    // ============================================
    private ResponseEntity<Map<String, Object>> run(String what, Supplier<Map<String, Object>> job) {

        try {

            Map<String, Object> body = new HashMap<>(job.get());
            body.put("success", true);
            return ResponseEntity.ok(body);

        } catch (NoSuchElementException e) {

            return fail(HttpStatus.NOT_FOUND, e.getMessage());

        } catch (IllegalArgumentException | IllegalStateException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("{} 오류", what, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, what + " 중 문제가 생겼어요. 잠시 후 다시 시도해주세요.");

        }

    }

    private Long userId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO u = session == null ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);
        return u == null ? null : u.getId();

    }

    private boolean isAdmin(Long userId) {

        return userId != null && adminFeedbackService.isAdmin(userId);

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);

    }
}
