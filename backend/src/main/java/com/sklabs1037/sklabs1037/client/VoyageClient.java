package com.sklabs1037.sklabs1037.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Voyage AI 임베딩 클라이언트 (문장 → 숫자 1024개 벡터)
 * - POST https://api.voyageai.com/v1/embeddings
 * - Claude API에는 임베딩이 없어서, Anthropic이 권하는 Voyage를 씀
 * - input_type: 문서는 "document", 질문은 "query" (검색 정확도가 올라감)
 * - 무료 사용(결제 수단 없음)은 분당 요청·토큰 제한이 작아서
 *   요청 사이 간격(voyage.min-interval-ms)을 두고, 429면 쉬었다가 다시 시도
 *   → 결제 수단을 등록해 한도가 늘면 VOYAGE_MIN_INTERVAL_MS=0 으로 빠르게
 */
@Slf4j
@Component
public class VoyageClient {

    private static final String URL = "https://api.voyageai.com/v1/embeddings";
    public static final int DIMENSION = 1024;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    @Value("${voyage.api-key:}")
    private String apiKey;

    @Value("${voyage.model:voyage-3.5}")
    private String model;

    @Value("${voyage.min-interval-ms:21000}")
    private long minIntervalMs;

    private final RestClient restClient = RestClients.withTimeout("VOYAGE", 30);

    // 마지막 요청 시각 (요청 간격 지키기)
    private long lastCallAt = 0;

    public boolean isConfigured() {

        return apiKey != null && !apiKey.isBlank();

    }

    // 문장들 → 벡터들 (순서 그대로)
    @SuppressWarnings("unchecked")
    public synchronized List<float[]> embed(List<String> texts, String inputType) {

        if (!isConfigured()) throw new IllegalStateException("VOYAGE_API_KEY가 설정되지 않았어요.");
        if (texts.isEmpty()) return List.of();

        Map<String, Object> body = new HashMap<>();
        body.put("input", texts);
        body.put("model", model);
        body.put("input_type", inputType);
        body.put("output_dimension", DIMENSION);

        long waitMs = 5000;

        for (int attempt = 1; ; attempt++) {

            waitInterval();

            try {

                Map<String, Object> res = restClient.post()
                        .uri(URL)
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(MAP_TYPE);

                List<Map<String, Object>> data = (List<Map<String, Object>>) res.get("data");
                float[][] out = new float[texts.size()][];

                for (Map<String, Object> d : data) {

                    int index = ((Number) d.get("index")).intValue();
                    List<Number> values = (List<Number>) d.get("embedding");
                    float[] v = new float[values.size()];
                    for (int i = 0; i < v.length; i++) v[i] = values.get(i).floatValue();
                    out[index] = v;

                }

                return Arrays.asList(out);

            } catch (HttpClientErrorException.TooManyRequests e) {

                if (attempt >= 6) throw e;

                log.warn("Voyage 호출 제한(429) → {}초 쉬고 다시 ({}번째)", waitMs / 1000, attempt);
                sleep(waitMs);
                waitMs = Math.min(waitMs * 2, 60_000);

            }

        }

    }

    // 벡터 → pgvector 글자 형식 "[0.1,0.2,...]"
    public static String toPgVector(float[] v) {

        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }

        return sb.append(']').toString();

    }

    private void waitInterval() {

        long gap = System.currentTimeMillis() - lastCallAt;
        if (lastCallAt > 0 && gap < minIntervalMs) sleep(minIntervalMs - gap);
        lastCallAt = System.currentTimeMillis();

    }

    private void sleep(long ms) {

        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("임베딩 대기 중 중단됨");
        }

    }
}
