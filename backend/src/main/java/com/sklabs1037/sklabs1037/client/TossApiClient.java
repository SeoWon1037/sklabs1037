package com.sklabs1037.sklabs1037.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
 *   GET  /api/v1/stocks    종목 정보 (symbols=최대 200개 → sharesOutstanding 상장 주식 수 등)
 *   GET  /api/v1/stocks/all 시장 전체 종목 목록 (market=KOSPI / KOSDAQ → symbol, name, securityType ...)
 *
 * - 현재가(prices)와 종목 정보(stocks)는 한 번에 최대 200종목
 * - 호출이 너무 많아 429(Too Many Requests)가 오면 1초 → 2초 → 4초 쉬고 다시 시도
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

    private final RestClient restClient = RestClients.withTimeout("TOSS", 20);   // 응답 20초 제한 (멈춘 연결이 스케줄러를 묶지 않게)

    // 재사용할 토큰 + 만료 시각
    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;
    private Instant tokenIssuedAt = Instant.EPOCH;

    // 운영 상태 (관리자 화면): 마지막 성공 / 마지막 실패
    private volatile Instant lastOkAt;
    private volatile Instant lastErrorAt;
    private volatile String lastError;

    // 키가 설정돼 있는지 (CI처럼 키가 없는 환경에서는 수집을 건너뛰려고)
    public boolean isConfigured() {

        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();

    }

    // 한 번에 조회할 수 있는 최대 종목 수 (prices, stocks)
    public static final int MAX_SYMBOLS = 200;

    // 일봉 조회 → candles 목록 (각 항목: timestamp, openPrice, highPrice, lowPrice, closePrice, volume / 값은 전부 문자)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getDailyCandles(String symbol, int count) {

        Map<String, Object> body = withRetry(() -> restClient.get()
                .uri(baseUrl + "/api/v1/candles?symbol={symbol}&interval=1d&count={count}", symbol, count)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(MAP_TYPE));

        Map<String, Object> result = (Map<String, Object>) body.get("result");

        return (List<Map<String, Object>>) result.get("candles");

    }

    // 현재가 조회 → 목록 (각 항목: symbol, timestamp, lastPrice, currency)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPrices(String symbols) {

        Map<String, Object> body = withRetry(() -> restClient.get()
                .uri(baseUrl + "/api/v1/prices?symbols={symbols}", symbols)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(MAP_TYPE));

        return (List<Map<String, Object>>) body.get("result");

    }

    // 종목 정보 조회 → 목록 (각 항목: symbol, name, market, sharesOutstanding(문자) ...)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getStocks(String symbols) {

        Map<String, Object> body = withRetry(() -> restClient.get()
                .uri(baseUrl + "/api/v1/stocks?symbols={symbols}", symbols)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(MAP_TYPE));

        return (List<Map<String, Object>>) body.get("result");

    }

    // 시장 전체 종목 목록 (상장 중인 종목만) → 목록 (각 항목: symbol, name, securityType, isCommonShare, isinCode)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listStocks(String market) {

        Map<String, Object> body = withRetry(() -> restClient.get()
                .uri(baseUrl + "/api/v1/stocks/all?market={market}&status=ACTIVE", market)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(MAP_TYPE));

        return (List<Map<String, Object>>) body.get("result");

    }

    // 429(호출이 너무 많음)이면 1초 → 2초 → 4초 쉬고 다시 시도, 그래도 안 되면 그대로 실패
    // 401(유효하지 않은 토큰)이면 토큰을 새로 받아서 1번만 다시 시도
    //   → 같은 키로 다른 서버(로컬 PC ↔ 운영 EC2)가 토큰을 새로 받으면 이쪽 토큰이 무효가 되기 때문
    private <T> T withRetry(Supplier<T> call) {

        try {

            T result = retrying(call);
            lastOkAt = Instant.now();

            return result;

        } catch (RuntimeException e) {

            lastErrorAt = Instant.now();
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            lastError = message.length() > 300 ? message.substring(0, 300) : message;
            throw e;

        }

    }

    // 운영 상태 (관리자 화면용, 토큰 값은 절대 넣지 않음)
    public Map<String, Object> health() {

        Map<String, Object> h = new java.util.LinkedHashMap<>();
        h.put("configured", isConfigured());
        h.put("lastOkAt", lastOkAt == null ? null : lastOkAt.toString());
        h.put("lastErrorAt", lastErrorAt == null ? null : lastErrorAt.toString());
        h.put("lastError", lastError);
        h.put("tokenIssuedAt", tokenIssuedAt.equals(Instant.EPOCH) ? null : tokenIssuedAt.toString());
        h.put("tokenExpiresAt", tokenExpiresAt.equals(Instant.EPOCH) ? null : tokenExpiresAt.toString());

        return h;

    }

    private <T> T retrying(Supplier<T> call) {

        long waitMs = 1000;
        boolean tokenRenewed = false;

        for (int attempt = 1; ; attempt++) {

            try {

                return call.get();

            } catch (HttpClientErrorException.Unauthorized e) {

                if (tokenRenewed) throw e;

                log.warn("토스 토큰이 무효가 됨(다른 서버가 새 토큰을 받았을 수 있음) → 새로 받고 다시 시도");
                invalidateToken();
                tokenRenewed = true;

            } catch (HttpClientErrorException.TooManyRequests e) {

                if (attempt >= 4) throw e;

                log.warn("토스 API 호출 제한(429) → {}ms 쉬고 다시 시도 ({}번째)", waitMs, attempt);

                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }

                waitMs *= 2;

            }

        }

    }

    // 토큰 버리기 (다음 호출 때 새로 발급)
    // - 방금(5초 안에) 받은 토큰이면 그대로 둠: 동시에 실패한 다른 요청이 새 토큰까지 버리지 않게
    private synchronized void invalidateToken() {

        if (Instant.now().isBefore(tokenIssuedAt.plusSeconds(5))) return;

        accessToken = null;
        tokenExpiresAt = Instant.EPOCH;

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
        tokenIssuedAt = Instant.now();

        log.info("토스 토큰 발급 완료 (유효 {}초)", expiresIn);

        return accessToken;

    }
}