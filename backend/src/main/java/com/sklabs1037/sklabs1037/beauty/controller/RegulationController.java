package com.sklabs1037.sklabs1037.beauty.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.beauty.service.RegulationQaService;
import com.sklabs1037.sklabs1037.beauty.vo.RagDocVO;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * 화장품 규제 Q&A Controller (RAG)
 *
 * API 목록
 *   GET    /api/beauty/regulation/docs              등록된 문서 목록 (누구나) → { success, docs, admin, ready }
 *   POST   /api/beauty/regulation/docs              문서 올리기 (관리자, multipart: file, title, source)
 *   DELETE /api/beauty/regulation/docs/{docId}      문서 삭제 (관리자)
 *   POST   /api/beauty/regulation/ask { question }  질문 (로그인, 하루 20번) → { answer, sources, found }
 */
@Slf4j
@RestController
@RequestMapping("/api/beauty/regulation")
public class RegulationController {

    private static final long MAX_FILE = 20L * 1024 * 1024;
    private static final int MAX_QUESTION = 500;

    @Autowired
    private RegulationQaService regulationQaService;

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @Autowired
    private JsonMapper jsonMapper;

    @GetMapping("/docs")
    public ResponseEntity<Map<String, Object>> docs(HttpServletRequest request) {

        Long userId = getLoginUserId(request);

        try {

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("docs", regulationQaService.docs());
            body.put("admin", userId != null && adminFeedbackService.isAdmin(userId));
            body.put("loggedIn", userId != null);
            body.put("ready", regulationQaService.ready());

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("규제 문서 목록 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "문서 목록을 불러오지 못했어요. (T_RAG_DOC 테이블이 있는지 확인)");

        }

    }

    @PostMapping("/docs")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "title", required = false) String title,
                                                      @RequestParam(value = "source", required = false) String source,
                                                      HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);

        if (file.isEmpty()) return fail(HttpStatus.BAD_REQUEST, "파일을 골라주세요.");
        if (file.getSize() > MAX_FILE) return fail(HttpStatus.BAD_REQUEST, "20MB 이하 파일만 올릴 수 있어요.");
        if (!(lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".md"))) {
            return fail(HttpStatus.BAD_REQUEST, "PDF, TXT, MD 파일만 올릴 수 있어요.");
        }

        String docTitle = title == null || title.isBlank() ? name.replaceAll("\\.[^.]+$", "") : title.trim();
        if (docTitle.length() > 200) docTitle = docTitle.substring(0, 200);
        String docSource = source == null ? null : (source.length() > 500 ? source.substring(0, 500) : source.trim());

        try {

            RagDocVO doc = regulationQaService.upload(getLoginUserId(request), docTitle, docSource, name, file.getBytes());

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("docId", doc.getDocId());
            body.put("message", "올렸어요. 문단을 나눠 벡터로 바꾸는 중이에요. (큰 문서는 몇 분)");

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException | IllegalStateException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("규제 문서 올리기 오류: {}", name, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "문서를 올리지 못했어요.");

        }

    }

    @DeleteMapping("/docs/{docId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long docId, HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        if (!regulationQaService.delete(docId)) return fail(HttpStatus.NOT_FOUND, "문서를 찾을 수 없어요.");

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);

        return ResponseEntity.ok(body);

    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = getLoginUserId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        String question = req.get("question") == null ? "" : String.valueOf(req.get("question")).trim();
        if (question.length() < 5) return fail(HttpStatus.BAD_REQUEST, "질문을 조금 더 자세히 적어주세요.");
        if (question.length() > MAX_QUESTION) return fail(HttpStatus.BAD_REQUEST, "질문은 " + MAX_QUESTION + "자 이내로 적어주세요.");

        try {

            Map<String, Object> body = new LinkedHashMap<>(regulationQaService.ask(userId, question));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException | IllegalStateException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (RestClientResponseException e) {

            log.error("규제 Q&A 외부 API 오류: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return fail(HttpStatus.BAD_GATEWAY, "AI 응답에 실패했어요. 잠시 후 다시 시도해주세요.");

        } catch (Exception e) {

            log.error("규제 Q&A 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "답변 중 오류가 발생했어요.");

        }

    }

    // ============================================
    // 공통
    // ============================================

    private ResponseEntity<Map<String, Object>> checkAdmin(HttpServletRequest request) {

        Long userId = getLoginUserId(request);

        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");
        if (!adminFeedbackService.isAdmin(userId)) return fail(HttpStatus.FORBIDDEN, "관리자만 문서를 관리할 수 있어요.");

        return null;

    }

    @SuppressWarnings("unchecked")
    private Long getLoginUserId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null) return null;

        Object loginUser = session.getAttribute(UserLoginController.LOGIN_USER);
        if (loginUser == null) return null;

        Map<String, Object> map = jsonMapper.convertValue(loginUser, Map.class);
        Object id = map.get("id");

        return (id instanceof Number n) ? n.longValue() : null;

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
