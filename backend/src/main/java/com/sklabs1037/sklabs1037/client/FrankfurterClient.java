package com.sklabs1037.sklabs1037.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 💱 환율 (Frankfurter, https://frankfurter.dev)
 * - 키 없이 쓰는 공개 API (유럽중앙은행 등 기관 고시 환율, 하루 한 번 바뀜)
 * - GET /v2/rates?base=USD&quotes=KRW,JPY&from=날짜 → [{date, base, quote, rate}, ...]
 */
@Slf4j
@Component
public class FrankfurterClient {

    private static final String URL = "https://api.frankfurter.dev/v2/rates";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JsonMapper jsonMapper;

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> series(String base, List<String> quotes, LocalDate from) throws Exception {

        String uri = URL + "?base=" + base + "&quotes=" + String.join(",", quotes) + "&from=" + from;

        HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("User-Agent", "SKLabs/1.0 (+https://sklabs1037.com)")
                .GET()
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("환율 API " + res.statusCode());

        Object body = jsonMapper.readValue(res.body(), Object.class);

        // 배열이 기본, 혹시 {rates:[...]} 모양이어도 받기
        List<Object> list = body instanceof List<?> l ? (List<Object>) l
                : body instanceof Map<?, ?> m && m.get("rates") instanceof List<?> r ? (List<Object>) r : List.of();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object o : list) if (o instanceof Map<?, ?> m) rows.add((Map<String, Object>) m);
        return rows;

    }
}
