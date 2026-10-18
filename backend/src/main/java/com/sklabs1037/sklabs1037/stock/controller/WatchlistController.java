package com.sklabs1037.sklabs1037.stock.controller;

import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.stock.service.IWatchlistService;
import com.sklabs1037.sklabs1037.stock.vo.WatchlistVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관심 종목 Controller
 *
 * API 목록
 *   GET    /api/watchlist              → { success, loggedIn, stocks: [...] }   (로그아웃 상태면 loggedIn=false, 빈 목록)
 *   POST   /api/watchlist  { stockId } → { success }   추가
 *   DELETE /api/watchlist?stockId=1    → { success }   삭제
 *
 * - 추가/삭제는 로그인한 사람만, 본인 목록만
 */
@Slf4j
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    @Autowired
    private IWatchlistService watchlistService;

    // 세션의 로그인 정보에서 회원 ID를 꺼낼 때 사용
    @Autowired
    private JsonMapper jsonMapper;

    // 내 관심 종목
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest request) {

        Long userId = getLoginUserId(request);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("loggedIn", userId != null);

        if (userId == null) {
            body.put("stocks", List.of());
            return ResponseEntity.ok(body);
        }

        try {

            body.put("stocks", watchlistService.getWatchlist(userId));

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("관심 종목 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "관심 종목을 불러오지 못했습니다.");

        }

    }

    // 추가
    @PostMapping
    public ResponseEntity<Map<String, Object>> add(@RequestBody WatchlistVO vo, HttpServletRequest request) {

        Long userId = getLoginUserId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        if (vo.getStockId() == null) return fail(HttpStatus.BAD_REQUEST, "종목을 확인해주세요.");

        try {

            watchlistService.add(userId, vo.getStockId());   // 이미 있으면 그대로 두고 성공 처리

            return ok();

        } catch (Exception e) {

            log.error("관심 종목 추가 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "관심 종목을 추가하지 못했습니다.");

        }

    }

    // 삭제
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> remove(@RequestParam(required = false) Long stockId,
                                                      HttpServletRequest request) {

        Long userId = getLoginUserId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        if (stockId == null) return fail(HttpStatus.BAD_REQUEST, "종목을 확인해주세요.");

        try {

            watchlistService.remove(userId, stockId);

            return ok();

        } catch (Exception e) {

            log.error("관심 종목 삭제 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "관심 종목을 삭제하지 못했습니다.");

        }

    }

    // ============================================
    // 공통
    // ============================================

    // 세션에 저장된 로그인 정보에서 회원 ID 꺼내기 (로그아웃 상태면 null)
    @SuppressWarnings("unchecked")
    private Long getLoginUserId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null) return null;

        Object loginUser = session.getAttribute(UserLoginController.LOGIN_USER);
        if (loginUser == null) return null;

        Map<String, Object> map = jsonMapper.convertValue(loginUser, Map.class);
        Object id = map.get("id");

        return (id instanceof Number n) ? n.longValue() : null;

    }

    private ResponseEntity<Map<String, Object>> ok() {

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);

        return ResponseEntity.ok(body);

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}