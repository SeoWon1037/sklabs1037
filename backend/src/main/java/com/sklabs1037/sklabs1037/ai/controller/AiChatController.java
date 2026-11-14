package com.sklabs1037.sklabs1037.ai.controller;

import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.ai.service.AiChatRules;
import com.sklabs1037.sklabs1037.ai.service.IAiChatService;
import com.sklabs1037.sklabs1037.ai.vo.AiChatVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 챗봇 Controller
 *
 * API 목록
 *   POST /api/ai/chat       질문하기   { message }          → { success, chatId, answer, toolsUsed, sources, loginRequired, navigate }
 *   POST /api/ai/feedback   답변 평가  { chatId, rating }   → { success }   (rating: UP / DOWN)
 *
 * 비용 보호
 *   - 로그인하지 않아도 질문 가능: IP당 하루 20번 (AiChatRules.ANON_DAILY) + 요청 제한(RateLimitGuard) + 하루 비용 한도(ClaudeApiClient)
 *   - 로그인이 필요한 답(내 계좌 · 하트)은 AI가 "로그인해 주세요"라고 안내 → loginRequired: true
 *   - 👍/👎 평가는 로그인한 사람만
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

    // 세션의 로그인 정보에서 회원 ID를 꺼낼 때 사용
    @Autowired
    private JsonMapper jsonMapper;

    // "날짜:IP" → 로그인하지 않은 사람이 오늘 질문한 횟수 (서버 메모리)
    private final Map<String, Integer> anonymousCounts = new java.util.concurrent.ConcurrentHashMap<>();

    // ============================================
    // 질문하기
    // ============================================
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody AiChatVO vo,
                                                    HttpServletRequest request) {

        // 1. 로그인 여부 (안 해도 질문은 가능)
        HttpSession session = request.getSession(false);
        Long userId = (session == null) ? null : getLoginUserId(session);

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

        // 3. 로그인하지 않은 사람은 하루 횟수 제한
        if (userId == null) {

            String ip = request.getHeader("X-Real-IP");
            String day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString();

            if (!AiChatRules.allowAnonymous(anonymousCounts, day, ip == null || ip.isBlank() ? "local" : ip)) {

                Map<String, Object> body = new HashMap<>();
                body.put("success", false);
                body.put("loginRequired", true);
                body.put("message", "로그인 없이 쓸 수 있는 하루 " + AiChatRules.ANON_DAILY + "번을 다 썼어요. 로그인하면 계속 물어볼 수 있어요.");

                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);

            }

        }

        // 4. AI 호출
        try {

            vo.setMessage(message);
            vo.setUserId(userId);
            vo.setReadOnly(session != null && Boolean.TRUE.equals(session.getAttribute(com.sklabs1037.sklabs1037.demo.service.DemoService.SESSION_KEY)));

            AiChatVO result = aiChatService.chat(vo);

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("chatId", result.getChatId());
            body.put("answer", result.getAnswer());
            body.put("toolsUsed", result.getToolsUsed());
            body.put("sources", result.getSources());
            body.put("loginRequired", result.isLoginRequired());
            body.put("navigate", result.getNavigate());   // 🧭 "커플 페이지 열어줘" → 화면이 이 주소로 이동

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

    // ============================================
    // 답변 피드백 (👍 / 👎)
    // ============================================
    @PostMapping("/feedback")
    public ResponseEntity<Map<String, Object>> feedback(@RequestBody AiChatVO vo,
                                                        HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(UserLoginController.LOGIN_USER) == null) {

            return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        }

        if (vo.getChatId() == null || !("UP".equals(vo.getRating()) || "DOWN".equals(vo.getRating()))) {

            return fail(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");

        }

        // 본인이 받은 답변에만 평가할 수 있게 회원 ID를 조건에 사용
        vo.setUserId(getLoginUserId(session));

        if (!aiChatService.saveRating(vo)) {

            return fail(HttpStatus.NOT_FOUND, "평가할 답변을 찾을 수 없습니다.");

        }

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);

        return ResponseEntity.ok(body);

    }

    // ============================================
    // 공통
    // ============================================

    // 세션에 저장된 로그인 정보에서 회원 ID 꺼내기 (로그인 정보 객체를 Map으로 바꿔서 "id" 값 사용)
    @SuppressWarnings("unchecked")
    private Long getLoginUserId(HttpSession session) {

        Object loginUser = session.getAttribute(UserLoginController.LOGIN_USER);
        if (loginUser == null) return null;

        Map<String, Object> map = jsonMapper.convertValue(loginUser, Map.class);
        Object id = map.get("id");

        return (id instanceof Number n) ? n.longValue() : null;

    }

    // 실패 응답
    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}