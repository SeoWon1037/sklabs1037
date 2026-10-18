package com.sklabs1037.sklabs1037.controller;

import com.sklabs1037.sklabs1037.service.INewsListService;
import com.sklabs1037.sklabs1037.vo.NewsListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 메인 화면(뉴스 목록) Controller
 *
 * API 목록
 *   GET /api/news?category=MARKET&stockId=1&size=15   → { success, news: [...] }
 *     - category, stockId 는 선택 (없으면 전체)
 *
 * - 로그인 없이 볼 수 있는 공개 API
 */
@Slf4j
@RestController
@RequestMapping("/api/news")
public class NewsListController {

    @Autowired
    private INewsListService newsListService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String category,
                                                    @RequestParam(required = false) Long stockId,
                                                    @RequestParam(defaultValue = "15") int size) {

        NewsListVO param = new NewsListVO();

        // 정해진 값만 허용 (그 외는 전체로)
        param.setCategory("MARKET".equals(category) || "STOCK".equals(category) ? category : null);
        param.setStockId(stockId);
        param.setSize(Math.max(1, Math.min(size, 50)));   // 1~50개로 제한

        try {

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("news", newsListService.getNewsList(param));

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("뉴스 목록 조회 오류", e);

            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", "뉴스를 불러오지 못했습니다.");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);

        }

    }
}