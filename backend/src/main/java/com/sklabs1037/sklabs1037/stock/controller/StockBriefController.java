package com.sklabs1037.sklabs1037.stock.controller;

import com.sklabs1037.sklabs1037.stock.service.StockBriefService;
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
 * 🧾 종목 한 장 요약
 *
 *   GET  /api/stock/brief?stockId=   저장된 요약 (누구나) { brief, fresh, aiReady }
 *   POST /api/stock/brief?stockId=   새로 만들기 (로그인, 3시간 안에 만든 게 있으면 그대로)
 */
@Slf4j
@RestController
@RequestMapping("/api/stock/brief")
public class StockBriefController {

    @Autowired
    private StockBriefService stockBriefService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@RequestParam Long stockId) {

        return run("종목 요약", () -> stockBriefService.get(stockId));

    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> make(@RequestParam Long stockId, HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        UserLoginVO user = (session == null) ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);
        if (user == null) return fail(HttpStatus.UNAUTHORIZED, "로그인하면 AI 요약을 만들 수 있어요.");

        return run("종목 요약 만들기", () -> stockBriefService.make(stockId));

    }

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

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
