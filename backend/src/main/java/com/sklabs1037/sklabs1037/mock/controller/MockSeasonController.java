package com.sklabs1037.sklabs1037.mock.controller;

import com.sklabs1037.sklabs1037.mock.service.MockSeasonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 🏆 모의투자 주간 시즌 명예의 전당 (누구나)
 *   GET /api/mock/seasons?weeks=8   최근 n주 TOP 3 [{ weekStart, rank, nickname, returnRate, totalAsset }]
 */
@Slf4j
@RestController
public class MockSeasonController {

    @Autowired
    private MockSeasonService mockSeasonService;

    @GetMapping("/api/mock/seasons")
    public ResponseEntity<Map<String, Object>> seasons(@RequestParam(defaultValue = "8") int weeks) {

        Map<String, Object> body = new LinkedHashMap<>();
        try {
            body.put("success", true);
            body.put("seasons", mockSeasonService.recent(weeks));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("주간 시즌 조회 오류", e);
            body.put("success", false);
            body.put("message", "시즌 기록을 불러오지 못했어요.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

    }
}
