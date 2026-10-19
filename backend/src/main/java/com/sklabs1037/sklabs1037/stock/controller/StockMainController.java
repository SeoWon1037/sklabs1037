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
 *   GET /api/stock/dashboard?days=20&limit=30  → { success, baseDate, stocks: [...] }   시가총액 상위 + 주요 종목 + 미니 차트
 *   GET /api/stock/list?market=ALL&page=1      → { success, stocks, total, page, hasMore } 목록 탭 (ALL·KOSPI·KOSDAQ·ETF, 30개씩)
 *   GET /api/stock/search?q=카카오             → { success, stocks: [...] }            종목 검색 (최대 20개)
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
    public ResponseEntity<Map<String, Object>> dashboard(@RequestParam(defaultValue = "100") int days,
                                                         @RequestParam(defaultValue = "30") int limit) {

        // 5~100거래일, 목록은 1~100개로 제한
        int safeDays = Math.max(5, Math.min(days, 100));
        int safeLimit = Math.max(1, Math.min(limit, 100));

        try {

            List<StockMainVO> stocks = stockMainService.getDashboard(safeDays, safeLimit);

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

    // 목록 탭 + 더보기
    private static final int PAGE_SIZE = 30;
    private static final java.util.Set<String> MARKETS = java.util.Set.of("ALL", "KOSPI", "KOSDAQ", "ETF");

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(defaultValue = "ALL") String market,
                                                    @RequestParam(defaultValue = "1") int page) {

        String safeMarket = MARKETS.contains(market) ? market : "ALL";
        int safePage = Math.max(1, Math.min(page, 200));

        try {

            Map<String, Object> result = stockMainService.getPage(safeMarket, (safePage - 1) * PAGE_SIZE, PAGE_SIZE, 20);
            int total = (int) result.get("total");

            Map<String, Object> body = new HashMap<>(result);
            body.put("success", true);
            body.put("page", safePage);
            body.put("hasMore", safePage * PAGE_SIZE < total);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("종목 목록 조회 오류: {} {}", market, page, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "종목 목록을 불러오지 못했습니다.");

        }

    }

    // 종목 검색 (2글자 미만이고 숫자도 아니면 빈 목록)
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam(required = false) String q) {

        String query = (q == null) ? "" : q.trim();

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);

        if (query.isEmpty() || query.length() > 30) {
            body.put("stocks", List.of());
            return ResponseEntity.ok(body);
        }

        try {

            body.put("stocks", stockMainService.search(query, 20));

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("종목 검색 오류: {}", query, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "검색하지 못했습니다.");

        }

    }

    // 종목 상세 + 일봉
    @GetMapping("/detail")
    public ResponseEntity<Map<String, Object>> detail(@RequestParam(required = false) String symbol) {

        // 6자리 종목코드만 허용 (새 종목코드는 영문이 섞임 예: 0088M0)
        if (symbol == null || !symbol.matches("[0-9A-Z]{6}")) {

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