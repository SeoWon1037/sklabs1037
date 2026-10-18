package com.sklabs1037.sklabs1037.stock.controller;

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
 * 종목 토론방 Controller
 *
 * API 목록
 *   GET    /api/stock/board?stockId=1&page=1          글 목록 (20개씩) + 의견 비율 → { success, posts, total, page, size, upCount, downCount, loggedIn }
 *   POST   /api/stock/board  { stockId, opinion, content }  글 쓰기              → { success }
 *   DELETE /api/stock/board/{postId}                   글 삭제                     → { success }
 *   GET    /api/stock/board/{postId}/comments          댓글 목록                   → { success, comments }
 *   POST   /api/stock/board/{postId}/comments { content }   댓글 쓰기              → { success }
 *   DELETE /api/stock/board/comments/{commentId}       댓글 삭제                   → { success }
 *
 * - 목록은 누구나, 쓰기·삭제는 로그인한 사람만
 * - 삭제는 본인 글/댓글 또는 관리자만
 * - 작성자 회원 번호는 화면에 보내지 않고, 대신 삭제 가능 여부(canDelete)만 보냄
 */
@Slf4j
@RestController
@RequestMapping("/api/stock/board")
public class StockBoardController {

    // 글 목록 한 번에 보여줄 개수
    private static final int PAGE_SIZE = 20;

    // 관리자 권한 값 (T_USER.ROLE)
    private static final String ADMIN_ROLE = "ADMIN";

    @Autowired
    private IStockBoardService stockBoardService;

    // 글 목록 + 의견 비율
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) Long stockId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    HttpServletRequest request) {

        if (stockId == null) return fail(HttpStatus.BAD_REQUEST, "종목을 확인해주세요.");

        UserLoginVO loginUser = getLoginUser(request);
        int safePage = Math.max(1, Math.min(page, 100));   // 1~100페이지로 제한

        try {

            List<Map<String, Object>> posts = new ArrayList<>();

            for (StockBoardVO p : stockBoardService.getPosts(stockId, safePage, PAGE_SIZE)) {
                Map<String, Object> row = new HashMap<>();
                row.put("postId", p.getPostId());
                row.put("nickname", p.getNickname());
                row.put("opinion", p.getOpinion());
                row.put("content", p.getContent());
                row.put("insAt", p.getInsAt());
                row.put("deleted", Boolean.TRUE.equals(p.getDeleted()));
                row.put("commentCount", p.getCommentCount());
                row.put("canDelete", !Boolean.TRUE.equals(p.getDeleted()) && canDelete(loginUser, p.getUserId()));
                posts.add(row);
            }

            StockBoardVO opinion = stockBoardService.getOpinionCount(stockId);

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("posts", posts);
            body.put("total", stockBoardService.countPosts(stockId));
            body.put("page", safePage);
            body.put("size", PAGE_SIZE);
            body.put("upCount", opinion == null ? 0 : opinion.getUpCount());
            body.put("downCount", opinion == null ? 0 : opinion.getDownCount());
            body.put("loggedIn", loginUser != null);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("토론 글 목록 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "토론 글을 불러오지 못했습니다.");

        }

    }

    // 글 쓰기
    @PostMapping
    public ResponseEntity<Map<String, Object>> writePost(@RequestBody StockBoardVO vo, HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 글을 쓸 수 있습니다.");

        try {

            stockBoardService.writePost(loginUser.getId(), vo.getStockId(), vo.getOpinion(), vo.getContent());

            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("토론 글 쓰기 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "글을 저장하지 못했습니다.");

        }

    }

    // 글 삭제
    @DeleteMapping("/{postId}")
    public ResponseEntity<Map<String, Object>> deletePost(@PathVariable Long postId, HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            stockBoardService.deletePost(loginUser.getId(), isAdmin(loginUser), postId);

            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (SecurityException e) {

            return fail(HttpStatus.FORBIDDEN, e.getMessage());

        } catch (Exception e) {

            log.error("토론 글 삭제 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "글을 삭제하지 못했습니다.");

        }

    }

    // 댓글 목록
    @GetMapping("/{postId}/comments")
    public ResponseEntity<Map<String, Object>> comments(@PathVariable Long postId, HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);

        try {

            List<Map<String, Object>> comments = new ArrayList<>();

            for (StockBoardVO c : stockBoardService.getComments(postId)) {
                Map<String, Object> row = new HashMap<>();
                row.put("commentId", c.getCommentId());
                row.put("nickname", c.getNickname());
                row.put("content", c.getContent());
                row.put("insAt", c.getInsAt());
                row.put("canDelete", canDelete(loginUser, c.getUserId()));
                comments.add(row);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("comments", comments);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("토론 댓글 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "댓글을 불러오지 못했습니다.");

        }

    }

    // 댓글 쓰기
    @PostMapping("/{postId}/comments")
    public ResponseEntity<Map<String, Object>> writeComment(@PathVariable Long postId,
                                                            @RequestBody StockBoardVO vo,
                                                            HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 댓글을 쓸 수 있습니다.");

        try {

            stockBoardService.writeComment(loginUser.getId(), postId, vo.getContent());

            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("토론 댓글 쓰기 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "댓글을 저장하지 못했습니다.");

        }

    }

    // 댓글 삭제
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(@PathVariable Long commentId, HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            stockBoardService.deleteComment(loginUser.getId(), isAdmin(loginUser), commentId);

            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (SecurityException e) {

            return fail(HttpStatus.FORBIDDEN, e.getMessage());

        } catch (Exception e) {

            log.error("토론 댓글 삭제 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "댓글을 삭제하지 못했습니다.");

        }

    }

    // ============================================
    // 공통
    // ============================================

    // 세션에 저장된 로그인 정보 (로그아웃 상태면 null)
    private UserLoginVO getLoginUser(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        return (session == null) ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);

    }

    private boolean isAdmin(UserLoginVO loginUser) {

        return loginUser != null && ADMIN_ROLE.equals(loginUser.getRole());

    }

    // 삭제 버튼을 보여줄지: 본인 글이거나 관리자
    private boolean canDelete(UserLoginVO loginUser, Long writerId) {

        return loginUser != null && (isAdmin(loginUser) || loginUser.getId().equals(writerId));

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