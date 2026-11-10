package com.sklabs1037.sklabs1037.og;

import com.sklabs1037.sklabs1037.market.service.MarketService;
import com.sklabs1037.sklabs1037.whatif.service.WhatIfService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 🔗 공유용 주소 — 카카오톡 등에 붙이면 내용이 담긴 미리보기 카드가 뜨고, 누르면 진짜 화면으로 넘어감
 *   GET /api/og/whatif?s=005930&d=2024-11-08&a=1000000   → "삼성전자를 100만 원어치 샀더라면 지금 286만 원 (+185.90%)"
 *   GET /api/og/market/{id}                              → 상품 제목 · 가격 · 첫 사진
 * - 계산이 안 되거나 없는 상품이어도 오류 대신 기본 소개 카드로 (링크는 그대로 열림)
 * - HTML 만들기 · 이스케이프는 OgPage
 */
@Slf4j
@RestController
@RequestMapping("/api/og")
public class OgController {

    @Autowired
    private WhatIfService whatIfService;

    @Autowired
    private MarketService marketService;

    @Value("${app.frontend-url:https://sklabs1037.com}")
    private String siteUrl;

    @GetMapping(value = "/whatif", produces = "text/html;charset=UTF-8")
    public String whatIf(@RequestParam(required = false) String s,
                         @RequestParam(required = false) String d,
                         @RequestParam(defaultValue = "1000000") long a) {

        String path = "/whatif";
        try {
            Map<String, Object> r = whatIfService.calc(s, d, a, false);
            Map<?, ?> stock = (Map<?, ?>) r.get("stock");
            path = "/whatif?s=" + enc(String.valueOf(stock.get("symbol"))) + "&d=" + enc(d.strip()) + "&a=" + a;
            return OgPage.html(siteUrl, path, whatIfTitle(String.valueOf(stock.get("name")), a, num(r.get("value")), String.valueOf(r.get("rate"))),
                    r.get("buyDate") + "에 샀다면 지금 얼마일까? 나도 계산해보기 · 배당 · 세금 · 수수료 제외", null);
        } catch (RuntimeException e) {
            return OgPage.html(siteUrl, path, "⏳ 그때 샀더라면 · SKLabs", "그때 그 주식을 샀다면 지금 얼마가 됐을까요? 종목과 날짜, 금액만 고르면 바로 알려드려요.", null);
        }

    }

    @GetMapping(value = "/market/{id}", produces = "text/html;charset=UTF-8")
    public String market(@PathVariable Long id) {

        String path = "/market/" + id;
        try {
            Map<String, Object> it = marketService.ogItem(id);
            if (it != null) return OgPage.html(siteUrl, path, it.get("title") + " · " + it.get("price"), String.valueOf(it.get("desc")), (String) it.get("image"));
        } catch (RuntimeException e) {
            log.info("상품 미리보기 실패 ({}): {}", id, e.getMessage());
        }
        return OgPage.html(siteUrl, "/market", "🛍️ 중고거래 · SKLabs", "안 쓰는 물건을 사고팔아요. 사진 한 장이면 올릴 수 있어요.", null);

    }

    // "삼성전자 100만 원 → 286만 원 (+185.90%) · 그때 샀더라면"
    static String whatIfTitle(String name, long amount, long value, String rate) {

        String sign = rate.startsWith("-") ? "" : "+";
        return name + " " + String.format("%,d", amount) + "원 → " + String.format("%,d", value) + "원 (" + sign + rate + "%) · 그때 샀더라면";

    }

    private static String enc(String s) {

        return URLEncoder.encode(s, StandardCharsets.UTF_8);

    }

    private static long num(Object o) {

        return o instanceof Number n ? n.longValue() : 0L;

    }
}
