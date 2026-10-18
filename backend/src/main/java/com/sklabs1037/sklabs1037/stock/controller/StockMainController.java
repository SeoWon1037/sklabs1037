package com.sklabs1037.sklabs1037.stock.controller;

import com.sklabs1037.sklabs1037.stock.service.IStockMainService;
import com.sklabs1037.sklabs1037.stock.vo.StockMainVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주식 종목 화면 Controller
 *
 * API 목록
 *   GET /api/stock/dashboard?days=100     → { success, baseDate, stocks: [...] }   종목 목록 + 미니 차트
 *   GET /api/stock/detail?symbol=005930   → { success, stock: {...}, candles: [...] } 종목 상세 + 일봉
 *
 * - 로그인 없이 볼 수 있는 공개 API (DB 조회만 하고 외부 유료 API는 쓰지 않음)
 */
@Slf4j
@RestController
@RequestMapping("/api/stock")
public class StockMainController {

    @Autowired
    private IStockMainService stockMainService;

    // 종목 목록 + 미니 차트
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(@RequestParam(defaultValue = "100") int days) {

        // 5~100거래일로 제한
        int safeDays = Math.max(5, Math.min(days, 100));

        try {

            List<StockMainVO> stocks = stockMainService.getDashboard(safeDays);

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("baseDate", stocks.isEmpty() ? null : stocks.get(0).getTradeDate());
            body.put("stocks", stocks);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("대시보드 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "시세 정보를 불러오지 못했습니다.");

        }

    }

    // 종목 상세 + 일봉
    @GetMapping("/detail")
    public ResponseEntity<Map<String, Object>> detail(@RequestParam(required = false) String symbol) {

        // 6자리 숫자 종목코드만 허용
        if (symbol == null || !symbol.matches("\\d{6}")) {

            return fail(HttpStatus.BAD_REQUEST, "종목코드를 확인해주세요.");

        }

        try {

            Map<String, Object> detail = stockMainService.getDetail(symbol);

            if (detail == null) return fail(HttpStatus.NOT_FOUND, "지원하지 않는 종목입니다.");

            Map<String, Object> body = new HashMap<>(detail);
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("종목 상세 조회 오류: {}", symbol, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "종목 정보를 불러오지 못했습니다.");

        }

    }

    // 실패 응답
    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}