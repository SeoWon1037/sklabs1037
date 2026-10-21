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
 *   GET  /api/mock/account                         내 계좌 + 보유 종목 + 미체결 주문 + 주문 가능 종목
 *                                                  → { success, loggedIn, account, holdings, pendingOrders, stocks, market }
 *   GET  /api/mock/quote?symbol=035720             종목 하나의 현재가 (주문 칸, 누구나) → { success, stock }
 *   GET  /api/mock/trades?page=1                   내 거래 내역 (20개씩)              → { success, trades, total, page, size }
 *   POST /api/mock/order { stockId, side, quantity, orderType, limitPrice }
 *                                                  주문 (MARKET 바로 체결 / LIMIT 대기) → { success, trade }
 *   POST /api/mock/orders/{orderId}/cancel         지정가 주문 취소                   → { success }
 *   POST /api/mock/reset                           (예전 주소) 바로 초기화하지 않음 → 관리자 승인 필요 안내
 *   GET  /api/mock/ranking?size=10&period=ALL      수익률 랭킹 (누구나, period=ALL|WEEK) → { success, period, ranking, me, participants }
 *   GET  /api/mock/history                         내 수익률 추이 (그래프)            → { success, initCash, points: [{ date, totalAsset, returnRate, live? }] }
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

    // 종목 하나의 현재가 (주문 칸에서 검색해 고른 종목)
    @GetMapping("/quote")
    public ResponseEntity<Map<String, Object>> quote(@RequestParam(required = false) String symbol) {

        if (symbol == null || symbol.isBlank() || symbol.length() > 12) return fail(HttpStatus.BAD_REQUEST, "종목코드를 확인해주세요.");

        try {

            Map<String, Object> stock = mockTradeService.getQuote(symbol.trim());
            if (stock == null) return fail(HttpStatus.NOT_FOUND, "시세가 아직 없는 종목이에요. 주식 종목 화면에서 한 번 열어주세요.");

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("stock", stock);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("모의투자 현재가 조회 오류: {}", symbol, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "현재가를 불러오지 못했습니다.");

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

            Map<String, Object> trade = mockTradeService.order(loginUser.getId(), vo);

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

    // 지정가 주문 취소
    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long orderId, HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            mockTradeService.cancelOrder(loginUser.getId(), orderId);

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("모의투자 주문 취소 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "주문을 취소하지 못했습니다.");

        }

    }

    // 초기화: 이제 관리자 승인이 필요 → POST /api/mock/reset-request (MockResetController)
    // 예전 화면이 열려 있어도 바로 지워지지 않게 막음
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(HttpServletRequest request) {

        return fail(HttpStatus.BAD_REQUEST, "계좌 초기화는 관리자 승인이 필요해요. 새로고침 후 \"초기화 신청\"을 눌러주세요.");

    }

    // 수익률 랭킹 (누구나)
    @GetMapping("/ranking")
    public ResponseEntity<Map<String, Object>> ranking(@RequestParam(defaultValue = "10") int size,
                                                       @RequestParam(defaultValue = "ALL") String period,
                                                       HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        int safeSize = Math.max(1, Math.min(size, 50));

        try {

            Map<String, Object> body = new HashMap<>(mockTradeService.getRanking(loginUser == null ? null : loginUser.getId(), safeSize, period));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("모의투자 랭킹 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "랭킹을 불러오지 못했습니다.");

        }

    }

    // 내 수익률 추이
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> history(HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            Map<String, Object> body = new HashMap<>(mockTradeService.getHistory(loginUser.getId()));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("모의투자 수익률 추이 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "수익률 추이를 불러오지 못했습니다.");

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
