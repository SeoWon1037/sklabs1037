package com.sklabs1037.sklabs1037.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Claude API 클라이언트 (LLM 호출 담당)
 * - POST https://api.anthropic.com/v1/messages
 * - 질문(messages) + 쓸 수 있는 도구 목록(tools)을 보내면
 *   Claude가 "답변" 또는 "이 도구를 써줘(tool_use)"를 돌려줌
 * - 도구가 필요 없는 호출(뉴스 요약 등)은 tools 에 null 을 넘기면 됨
 */
@Component
public class ClaudeApiClient {

    private static final String MESSAGES_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION  = "2023-06-01";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Value("${anthropic.api-key}")
    private String apiKey;

    @Value("${anthropic.model}")
    private String model;

    @Value("${anthropic.max-tokens}")
    private int maxTokens;

    private final RestClient restClient = RestClient.create();

    // 키가 설정돼 있는지
    public boolean isConfigured() {

        return apiKey != null && !apiKey.isBlank();

    }

    // 메시지 보내기 → Claude 응답 전체 (content, stop_reason 등)
    public Map<String, Object> createMessage(String system,
                                             List<Map<String, Object>> tools,
                                             List<Map<String, Object>> messages) {

        return createMessage(system, tools, messages, null, maxTokens);

    }

    // 정해진 도구 하나를 반드시 쓰게 해서 "정해진 모양의 JSON"으로 답을 받기 (뷰티 랩 성분 읽기, 기획 브리프)
    // - toolChoice: {"type":"tool","name":"도구 이름"}
    // - 이미지(사진)는 messages의 content에 {"type":"image","source":{...base64}} 블록으로 넣음
    public Map<String, Object> createMessage(String system,
                                             List<Map<String, Object>> tools,
                                             List<Map<String, Object>> messages,
                                             Map<String, Object> toolChoice,
                                             int maxTokensOverride) {

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokensOverride > 0 ? maxTokensOverride : maxTokens);
        body.put("system", system);
        body.put("messages", messages);

        // 도구가 있을 때만 보냄
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        if (toolChoice != null) {
            body.put("tool_choice", toolChoice);
        }

        return restClient.post()
                .uri(MESSAGES_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP_TYPE);

    }
}