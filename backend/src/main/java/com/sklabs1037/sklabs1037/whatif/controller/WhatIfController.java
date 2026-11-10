package com.sklabs1037.sklabs1037.whatif.controller;

import com.sklabs1037.sklabs1037.whatif.service.WhatIfCalc;
import com.sklabs1037.sklabs1037.whatif.service.WhatIfService;
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
import java.util.NoSuchElementException;

/**
 * ⏳ "그때 샀더라면" (로그인 없이 누구나)
 *   GET /api/whatif?symbol=005930&from=2024-11-08&amount=1000000
 *   → { success, stock, amount, buyDate, buyPrice, nowDate, nowPrice, value, profit, rate, days,
 *       clamped, firstDate, peak, trough, series[{d,v}], bench }
 *   가격 기준이 바뀐 날이 끼면 { success:false, message, breakDate, retryFrom }
 */
@Slf4j
@RestController
@RequestMapping("/api/whatif")
public class WhatIfController {

    @Autowired
    private WhatIfService whatIfService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> calc(@RequestParam(required = false) String symbol,
                                                    @RequestParam(required = false) String from,
                                                    @RequestParam(defaultValue = "1000000") long amount) {

        Map<String, Object> body = new HashMap<>();
        try {

            body.putAll(whatIfService.calc(symbol, from, amount));
            body.put("success", true);
            return ResponseEntity.ok(body);

        } catch (WhatIfCalc.Broken e) {

            body.put("breakDate", e.getBreakDate().toString());
            body.put("retryFrom", e.getRetryFrom().toString());
            return fail(body, HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (NoSuchElementException e) {

            return fail(body, HttpStatus.NOT_FOUND, e.getMessage());

        } catch (IllegalArgumentException e) {

            return fail(body, HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("그때 샀더라면 계산 오류", e);
            return fail(body, HttpStatus.INTERNAL_SERVER_ERROR, "계산 중 문제가 생겼어요. 잠시 후 다시 시도해주세요.");

        }

    }

    private ResponseEntity<Map<String, Object>> fail(Map<String, Object> body, HttpStatus status, String message) {

        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);

    }
}
