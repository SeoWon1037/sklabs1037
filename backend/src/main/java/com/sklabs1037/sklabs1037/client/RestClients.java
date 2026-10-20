package com.sklabs1037.sklabs1037.client;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 외부 API용 RestClient (시간 제한 있음)
 *
 * - RestClient.create()는 응답을 기다리는 시간 제한이 없어서, 상대 서버가 멈추면 스레드가 끝없이 기다림
 *   → 스케줄러 스레드(4개)가 이렇게 묶이면 다른 정해진 작업(AI 리그 09:10 등)이 아예 안 돌 수 있음
 * - 연결 5초, 응답은 API마다 (토스 20초, 뉴스 15초, Voyage 30초, Claude 120초)
 */
public final class RestClients {

    private RestClients() {
    }

    public static RestClient withTimeout(int readSeconds) {

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));

        return RestClient.builder().requestFactory(factory).build();

    }
}
