package com.sklabs1037.sklabs1037.controller;

import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.service.IAiChatService;
import com.sklabs1037.sklabs1037.vo.AiChatVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 주식 비서 Controller
 *
 * API 목록
 *   POST /api/ai/chat   질문하기 { "message" } → { success, answer, toolsUsed }
 *
 * 비용 보호
 *   - 로그인한 사람만 사용 가능
 *   - 질문 500자 제한
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private static final int MAX_MESSAGE_LENGTH = 500;

    @Autowired
    private IAiChatService aiChatService;

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody AiChatVO vo,
                                                    HttpServletRequest request) {

        // 1. 로그인 확인
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(UserLoginController.LOGIN_USER) == null) {

            return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        }

        // 2. 입력 확인
        String message = (vo.getMessage() == null) ? "" : vo.getMessage().trim();

        if (message.isEmpty()) {

            return fail(HttpStatus.BAD_REQUEST, "질문을 입력해주세요.");

        }

        if (message.length() > MAX_MESSAGE_LENGTH) {

            return fail(HttpStatus.BAD_REQUEST, "질문은 " + MAX_MESSAGE_LENGTH + "자 이내로 입력해주세요.");

        }

        if (!claudeApiClient.isConfigured()) {

            return fail(HttpStatus.SERVICE_UNAVAILABLE, "AI 기능이 설정되지 않았습니다.");

        }

        // 3. AI 호출
        try {

            vo.setMessage(message);
            AiChatVO result = aiChatService.chat(vo);

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("answer", result.getAnswer());
            body.put("toolsUsed", result.getToolsUsed());

            return ResponseEntity.ok(body);

        } catch (RestClientResponseException e) {

            // Claude/토스 API가 에러를 돌려준 경우 (키 오류, 크레딧 부족 등) → 원인은 로그에만
            log.error("AI 외부 API 오류: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return fail(HttpStatus.BAD_GATEWAY, "AI 응답에 실패했습니다. 잠시 후 다시 시도해주세요.");

        } catch (Exception e) {

            log.error("AI 처리 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "AI 처리 중 오류가 발생했습니다.");

        }

    }

    // 실패 응답
    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}