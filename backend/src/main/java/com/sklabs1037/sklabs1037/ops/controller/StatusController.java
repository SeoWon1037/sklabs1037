package com.sklabs1037.sklabs1037.ops.controller;

import com.sklabs1037.sklabs1037.ops.service.StatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 🟢 서비스 상태 (로그인 없이 누구나, /status 화면)
 *   GET /api/status   { overall, items[{key,label,status,detail}], checks[], tradingToday, at }
 */
@RestController
public class StatusController {

    @Autowired
    private StatusService statusService;

    @GetMapping("/api/status")
    public ResponseEntity<Map<String, Object>> status() {

        Map<String, Object> body = new LinkedHashMap<>(statusService.status());
        body.put("success", true);
        return ResponseEntity.ok(body);

    }
}
