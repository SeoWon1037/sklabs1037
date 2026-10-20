package com.sklabs1037.sklabs1037.stock.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.stock.service.IStockBoardService;
import com.sklabs1037.sklabs1037.stock.vo.StockBoardVO;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자: 토론방 신고 관리
 *
 * API 목록 (관리자만, 매 요청마다 DB에서 ROLE 확인)
 *   GET  /api/admin/reports                       처리할 신고가 있는 글 → { success, posts, blindAt }
 *   GET  /api/admin/reports/count                 처리할 글 수 (관리자 탭 숫자) → { success, count }
 *   POST /api/admin/reports/{postId} { action }   DELETE 글 삭제 / DISMISS 문제 없음 → { success }
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/reports")
public class BoardReportAdminController {

    @Autowired
    private IStockBoardService stockBoardService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        try {

            List<Map<String, Object>> posts = new ArrayList<>();

            for (StockBoardVO p : stockBoardService.getReportedPosts()) {
                Map<String, Object> row = new HashMap<>();
                row.put("postId", p.getPostId());
                row.put("symbol", p.getSymbol());
                row.put("stockName", p.getStockName());
                row.put("nickname", p.getNickname());
                row.put("opinion", p.getOpinion());
                row.put("content", p.getContent());
                row.put("insAt", p.getInsAt());
                row.put("deleted", Boolean.TRUE.equals(p.getDeleted()));
                row.put("reportCount", p.getReportCount());
                row.put("reasons", p.getReasons());
                row.put("memos", p.getMemos());
                row.put("firstReportAt", p.getFirstReportAt());
                posts.add(row);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("posts", posts);
            body.put("blindAt", IStockBoardService.BLIND_AT);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("신고 목록 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "신고 목록을 불러오지 못했습니다.");

        }

    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> count(HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        try {

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("count", stockBoardService.countReportedPosts());

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            // 테이블이 아직 없을 때도 관리자 화면은 뜨게
            log.warn("신고 수 조회 실패: {}", e.getMessage());
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "신고 수를 불러오지 못했습니다.");

        }

    }

    @PostMapping("/{postId}")
    public ResponseEntity<Map<String, Object>> handle(@PathVariable Long postId,
                                                      @RequestBody Map<String, String> body,
                                                      HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        try {

            stockBoardService.handleReport(getLoginUser(request).getId(), postId, body.get("action"));

            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);

            return ResponseEntity.ok(ok);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("신고 처리 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "신고를 처리하지 못했습니다.");

        }

    }

    // ============================================
    // 공통
    // ============================================
    private ResponseEntity<Map<String, Object>> checkAdmin(HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);

        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");
        if (!adminFeedbackService.isAdmin(loginUser.getId())) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있습니다.");

        return null;

    }

    private UserLoginVO getLoginUser(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        return (session == null) ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
