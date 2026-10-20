package com.sklabs1037.sklabs1037.fx.controller;

import com.sklabs1037.sklabs1037.fx.service.FxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 💱 오늘의 환율 (로그인 없이)
 *
 *   GET /api/fx   { date, rates[{code, flag, name, unit, rate, change, changeRate, history}], source, stale }
 */
@Slf4j
@RestController
@RequestMapping("/api/fx")
public class FxController {

    @Autowired
    private FxService fxService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> today() {

        try {

            Map<String, Object> body = new HashMap<>(fxService.today());
            body.put("success", true);
            return ResponseEntity.ok(body);

        } catch (Exception e) {

            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", "환율을 불러오지 못했어요.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);

        }

    }
}
