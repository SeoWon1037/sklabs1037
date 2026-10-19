package com.sklabs1037.sklabs1037.mock.controller;

import com.sklabs1037.sklabs1037.mock.service.IMockTradeService;
import com.sklabs1037.sklabs1037.mock.vo.MockTradeVO;
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
 * 모의투자 Controller
 *
 * API 목록
 *   GET  /api/mock/account                         내 계좌 + 보유 종목 + 주문 가능 종목 → { success, loggedIn, account, holdings, stocks }
 *   GET  /api/mock/trades?page=1                   내 거래 내역 (20개씩)              → { success, trades, total, page, size }
 *   POST /api/mock/order { stockId, side, quantity }  시장가 주문 (BUY / SELL)        → { success, trade }
 *   POST /api/mock/reset                           계좌 초기화                        → { success }
 *   GET  /api/mock/ranking?size=10                 수익률 랭킹 (누구나)                → { success, ranking, me, participants }
 *
 * - 랭킹은 누구나, 나머지는 로그인한 사람만 (본인 계좌만)
 * - 랭킹에는 닉네임만 보내고 회원 번호는 보내지 않음
 */
@Slf4j
@RestController
@RequestMapping("/api/mock")
public class MockTradeController {

    // 거래 내역 한 번에 보여줄 개수
    private static final int TRADE_PAGE_SIZE = 20;

    @Autowired
    private IMockTradeService mockTradeService;

    // 내 계좌
    @GetMapping("/account")
    public ResponseEntity<Map<String, Object>> account(HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);

        if (loginUser == null) {

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("loggedIn", false);

            return ResponseEntity.ok(body);

        }

        try {

            Map<String, Object> body = new HashMap<>(mockTradeService.getAccount(loginUser.getId()));
            body.put("success", true);
            body.put("loggedIn", true);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("모의투자 계좌 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "계좌 정보를 불러오지 못했습니다.");

        }

    }

    // 내 거래 내역
    @GetMapping("/trades")
    public ResponseEntity<Map<String, Object>> trades(@RequestParam(defaultValue = "1") int page,
                                                      HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        int safePage = Math.max(1, Math.min(page, 500));

        try {

            Map<String, Object> body = new HashMap<>(mockTradeService.getTrades(loginUser.getId(), safePage, TRADE_PAGE_SIZE));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("모의투자 거래 내역 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "거래 내역을 불러오지 못했습니다.");

        }

    }

    // 주문
    @PostMapping("/order")
    public ResponseEntity<Map<String, Object>> order(@RequestBody MockTradeVO vo, HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 주문할 수 있습니다.");

        try {

            int quantity = (vo.getQuantity() == null) ? 0 : vo.getQuantity();

            Map<String, Object> trade = mockTradeService.order(loginUser.getId(), vo.getStockId(), vo.getSide(), quantity);

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("trade", trade);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            // 현금 부족, 보유 수량 부족 같은 안내 문구는 그대로 화면에
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("모의투자 주문 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "주문을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.");

        }

    }

    // 초기화
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            mockTradeService.reset(loginUser.getId());

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("모의투자 초기화 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "초기화하지 못했습니다.");

        }

    }

    // 수익률 랭킹 (누구나)
    @GetMapping("/ranking")
    public ResponseEntity<Map<String, Object>> ranking(@RequestParam(defaultValue = "10") int size,
                                                       HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        int safeSize = Math.max(1, Math.min(size, 50));

        try {

            Map<String, Object> body = new HashMap<>(mockTradeService.getRanking(loginUser == null ? null : loginUser.getId(), safeSize));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("모의투자 랭킹 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "랭킹을 불러오지 못했습니다.");

        }

    }

    // ============================================
    // 공통
    // ============================================

    // 세션의 로그인 정보 (로그아웃 상태면 null)
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
