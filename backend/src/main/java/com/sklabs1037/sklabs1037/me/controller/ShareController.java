package com.sklabs1037.sklabs1037.me.controller;

import com.sklabs1037.sklabs1037.me.service.ShareService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 📸 공유 카드 보기 (로그인 없이, 링크를 받은 누구나)
 *
 *   GET /api/share/{code}   만든 순간의 스냅샷 { card }
 */
@Slf4j
@RestController
@RequestMapping("/api/share")
public class ShareController {

    @Autowired
    private ShareService shareService;

    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String code) {

        Map<String, Object> body = new HashMap<>();

        try {

            Map<String, Object> card = shareService.get(code);

            if (card == null) {
                body.put("success", false);
                body.put("message", "카드를 찾을 수 없어요.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }

            body.put("success", true);
            body.put("card", card);
            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("공유 카드 오류", e);
            body.put("success", false);
            body.put("message", "카드를 불러오지 못했어요.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);

        }

    }
}
