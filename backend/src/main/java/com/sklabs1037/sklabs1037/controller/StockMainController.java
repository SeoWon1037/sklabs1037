package com.sklabs1037.sklabs1037.controller;

import com.sklabs1037.sklabs1037.service.IStockMainService;
import com.sklabs1037.sklabs1037.vo.StockMainVO;
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
 * 메인 화면(종목 대시보드) Controller
 *
 * API 목록
 *   GET /api/stock/dashboard?days=100   → { success, baseDate, stocks: [...] }
 *
 * - 로그인 없이 볼 수 있는 공개 API (DB 조회만 하고 외부 유료 API는 쓰지 않음)
 */
@Slf4j
@RestController
@RequestMapping("/api/stock")
public class StockMainController {

    @Autowired
    private IStockMainService stockMainService;

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

            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", "시세 정보를 불러오지 못했습니다.");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);

        }

    }
}