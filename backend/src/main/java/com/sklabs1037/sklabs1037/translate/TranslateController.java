package com.sklabs1037.sklabs1037.translate;

import com.sklabs1037.sklabs1037.client.AiBudgetException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 🌐 번역 (로그인 없이 누구나)
 *   POST /api/ai/translate { text, target }  → { success, text, translated, reading, source, sourceName, target, targetName }
 *   target: ko · en · ja · zh · es · vi
 */
@Slf4j
@RestController
public class TranslateController {

    @Autowired
    private TranslateService translateService;

    @PostMapping("/api/ai/translate")
    public ResponseEntity<Map<String, Object>> translate(@RequestBody Map<String, Object> body, HttpServletRequest request) {

        String ip = request.getHeader("X-Real-IP");
        try {

            Map<String, Object> out = new HashMap<>(translateService.translate(body.get("text"), body.get("target"), ip == null || ip.isBlank() ? "local" : ip));
            out.put("success", true);
            return ResponseEntity.ok(out);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (AiBudgetException e) {

            return fail(HttpStatus.SERVICE_UNAVAILABLE, "오늘은 AI 사용량이 다 찼어요. 내일 다시 써주세요.");

        } catch (IllegalStateException e) {

            return fail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());

        } catch (Exception e) {

            log.error("번역 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "번역 중 문제가 생겼어요. 잠시 후 다시 시도해주세요.");

        }

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);

    }
}
