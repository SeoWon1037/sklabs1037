package com.sklabs1037.sklabs1037.client;

import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 외부 API용 RestClient (시간 제한 + 🔌 연동 기록)
 *
 * - RestClient.create()는 응답을 기다리는 시간 제한이 없어서, 상대 서버가 멈추면 스레드가 끝없이 기다림
 *   → 스케줄러 스레드(4개)가 이렇게 묶이면 다른 정해진 작업(AI 리그 09:10 등)이 아예 안 돌 수 있음
 * - 연결 5초, 응답은 API마다 (토스 20초, 뉴스 15초, Voyage 30초, Claude 120초, 소셜 로그인 10초)
 *
 * 연동 기록 (인터셉터)
 *   - 이 RestClient로 나가는 요청은 전부 한 번씩 ApiCallLog에 기록: 서비스, 경로, 응답 번호, 걸린 시간
 *   - 클라이언트 코드(TossApiClient 등)는 손대지 않아도 됨 → 새 외부 API를 붙여도 여기만 쓰면 자동 기록
 *   - 재시도도 한 번씩 따로 기록됨 (토스 401 → 토큰 재발급 → 200 이 그대로 보임)
 *   - 걸린 시간 = 요청을 보내고 응답 머리(상태 코드)를 받을 때까지
 */
public final class RestClients {

    // service에 이 값을 주면 주소(host)를 보고 서비스 이름을 정함 (카카오·네이버 로그인)
    public static final String BY_HOST = "BY_HOST";

    private RestClients() {
    }

    public static RestClient withTimeout(String service, int readSeconds) {

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));

        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(recording(service))
                .build();

    }

    // 요청 한 번마다 기록 남기는 인터셉터
    static ClientHttpRequestInterceptor recording(String service) {

        return (request, body, execution) -> {

            String svc = BY_HOST.equals(service) ? ApiCallLog.serviceOf(request.getURI().getHost()) : service;
            String action = ApiCallLog.actionOf(request.getURI().getPath());
            long start = ApiCallLog.start();

            try {

                ClientHttpResponse response = execution.execute(request, body);
                int code = response.getStatusCode().value();
                boolean ok = code < 400;

                ApiCallLog.record(svc, action, ok, code, ApiCallLog.elapsedMs(start), ok ? null : "HTTP " + code);

                return response;

            } catch (IOException | RuntimeException e) {

                // 연결 실패 · 시간 초과: 응답 번호 없음
                ApiCallLog.record(svc, action, false, null, ApiCallLog.elapsedMs(start), errorText(e));
                throw e;

            }

        };

    }

    // 예외 → 짧은 이유 (종류 + 메시지)
    public static String errorText(Throwable e) {

        String name = e.getClass().getSimpleName();
        String msg = e.getMessage();

        return msg == null || msg.isBlank() ? name : name + ": " + msg;

    }
}
