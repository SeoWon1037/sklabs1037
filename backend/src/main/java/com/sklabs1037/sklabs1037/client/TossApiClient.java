package com.sklabs1037.sklabs1037.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 토스증권 Open API 클라이언트
 * - 토큰 발급과 API 호출을 한 곳에서 담당
 * - ★ 토스는 토큰을 새로 받으면 예전 토큰이 무효가 됨
 *   → 여기서 토큰을 한 번 받아 만료 전까지 재사용 (스케줄러, AI 비서가 같이 사용)
 *
 * 사용하는 API
 *   POST /oauth2/token     토큰 발급 (client_credentials)
 *   GET  /api/v1/prices    현재가 (symbols=005930,000660)
 *   GET  /api/v1/candles   일봉   (symbol=005930&interval=1d&count=100, 최신 날짜부터)
 */
@Slf4j
@Component
public class TossApiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Value("${toss.base-url}")
    private String baseUrl;

    @Value("${toss.client-id}")
    private String clientId;

    @Value("${toss.client-secret}")
    private String clientSecret;

    private final RestClient restClient = RestClient.create();

    // 재사용할 토큰 + 만료 시각
    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    // 키가 설정돼 있는지 (CI처럼 키가 없는 환경에서는 수집을 건너뛰려고)
    public boolean isConfigured() {

        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();

    }

    // 일봉 조회 → candles 목록 (각 항목: timestamp, openPrice, highPrice, lowPrice, closePrice, volume / 값은 전부 문자)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getDailyCandles(String symbol, int count) {

        Map<String, Object> body = restClient.get()
                .uri(baseUrl + "/api/v1/candles?symbol={symbol}&interval=1d&count={count}", symbol, count)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(MAP_TYPE);

        Map<String, Object> result = (Map<String, Object>) body.get("result");

        return (List<Map<String, Object>>) result.get("candles");

    }

    // 현재가 조회 → 목록 (각 항목: symbol, timestamp, lastPrice, currency)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPrices(String symbols) {

        Map<String, Object> body = restClient.get()
                .uri(baseUrl + "/api/v1/prices?symbols={symbols}", symbols)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(MAP_TYPE);

        return (List<Map<String, Object>>) body.get("result");

    }

    // 토큰 꺼내기 (없거나 만료 1분 전이면 새로 발급)
    // synchronized: 스케줄러와 AI 비서가 동시에 요청해도 토큰을 두 번 받지 않게 (두 번 받으면 첫 토큰이 무효가 됨)
    private synchronized String getAccessToken() {

        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {

            return accessToken;

        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<String, Object> result = restClient.post()
                .uri(baseUrl + "/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MAP_TYPE);

        accessToken = (String) result.get("access_token");
        long expiresIn = ((Number) result.get("expires_in")).longValue();   // 보통 86400초 (24시간)
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn);

        log.info("토스 토큰 발급 완료 (유효 {}초)", expiresIn);

        return accessToken;

    }
}