package com.sklabs1037.sklabs1037.beauty.controller;

import com.sklabs1037.sklabs1037.beauty.service.BeautyLabService;
import com.sklabs1037.sklabs1037.beauty.service.FormulaCheckService;
import com.sklabs1037.sklabs1037.beauty.service.IngredientDictionary;
import com.sklabs1037.sklabs1037.beauty.vo.IngredientVO;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * 뷰티 랩 Controller
 *
 * API 목록
 *   POST /api/beauty/analyze { image, text, skinType }   성분 분석 (로그인, 하루 20번)
 *        - image: "data:image/jpeg;base64,..." (화면에서 1568px 이하로 줄여서 보냄) / text: 전성분 붙여넣기
 *   POST /api/beauty/compare { a: {image|text, name}, b: {...}, skinType }   두 제품 성분 비교 (로그인, 하루 20번)
 *   POST /api/beauty/brief { request }                   기획 요청 → 개발 브리프 (로그인, 하루 20번)
 *   GET  /api/beauty/ingredients?q=나이아신              성분 사전 검색 (누구나)
 *   GET  /api/beauty/history                             내 최근 기록 + 오늘 남은 횟수
 *   GET  /api/beauty/history/{logId}                     내 기록 하나 다시 보기
 */
@Slf4j
@RestController
@RequestMapping("/api/beauty")
public class BeautyLabController {

    // 사진 최대 크기 (base64 글자 수 기준, 약 5MB 이미지)
    private static final int MAX_IMAGE_CHARS = 7_000_000;
    private static final int MAX_TEXT = 3000;
    private static final int MAX_REQUEST = 1500;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    @Autowired
    private BeautyLabService beautyLabService;

    @Autowired
    private IngredientDictionary dictionary;

    @Autowired
    private FormulaCheckService formulaCheckService;

    @Autowired
    private JsonMapper jsonMapper;

    // ============================================
    // 성분 분석
    // ============================================
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = getLoginUserId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        String image = req.get("image") == null ? "" : String.valueOf(req.get("image"));
        String text = req.get("text") == null ? "" : String.valueOf(req.get("text")).trim();
        String skinType = req.get("skinType") == null ? null : String.valueOf(req.get("skinType"));
        if (skinType != null && !BeautyLabService.SKIN_TYPES.contains(skinType)) skinType = null;

        String base64 = null;
        String mediaType = null;

        if (!image.isBlank()) {

            // "data:image/jpeg;base64,xxxx" → 종류 + 본문
            int comma = image.indexOf(',');
            if (!image.startsWith("data:") || comma < 0 || !image.substring(0, comma).endsWith(";base64")) {
                return fail(HttpStatus.BAD_REQUEST, "사진 형식을 확인해주세요.");
            }

            mediaType = image.substring(5, comma - ";base64".length());
            base64 = image.substring(comma + 1);

            if (!IMAGE_TYPES.contains(mediaType)) return fail(HttpStatus.BAD_REQUEST, "JPG, PNG, WEBP 사진만 올릴 수 있어요.");
            if (base64.length() > MAX_IMAGE_CHARS) return fail(HttpStatus.BAD_REQUEST, "사진이 너무 커요. 5MB 이하로 올려주세요.");

        } else {

            if (text.isEmpty()) return fail(HttpStatus.BAD_REQUEST, "사진을 올리거나 전성분을 붙여 넣어주세요.");
            if (text.length() > MAX_TEXT) return fail(HttpStatus.BAD_REQUEST, "전성분은 " + MAX_TEXT + "자 이내로 붙여 넣어주세요.");

        }

        try {

            Map<String, Object> body = new LinkedHashMap<>(beautyLabService.analyze(userId, base64, mediaType, text, skinType));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (RestClientResponseException e) {

            log.error("성분 분석 AI 오류: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return fail(HttpStatus.BAD_GATEWAY, "AI가 사진을 읽지 못했어요. 잠시 후 다시 시도해주세요.");

        } catch (Exception e) {

            log.error("성분 분석 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "분석 중 오류가 발생했어요.");

        }

    }

    // ============================================
    // 두 제품 성분 비교
    // ============================================
    @PostMapping("/compare")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> compare(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = getLoginUserId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        String skinType = req.get("skinType") == null ? null : String.valueOf(req.get("skinType"));
        if (skinType != null && !BeautyLabService.SKIN_TYPES.contains(skinType)) skinType = null;

        Map<String, String> a;
        Map<String, String> b;

        try {
            a = productInput(req.get("a"), "A 제품");
            b = productInput(req.get("b"), "B 제품");
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        try {

            Map<String, Object> body = new LinkedHashMap<>(beautyLabService.compare(userId, a, b, skinType));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (RestClientResponseException e) {

            log.error("성분 비교 AI 오류: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return fail(HttpStatus.BAD_GATEWAY, "AI가 사진을 읽지 못했어요. 잠시 후 다시 시도해주세요.");

        } catch (Exception e) {

            log.error("성분 비교 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "비교 중 오류가 발생했어요.");

        }

    }

    // { image | text, name } → { image(base64), mediaType, text, name } (검사 포함)
    private Map<String, String> productInput(Object raw, String label) {

        if (!(raw instanceof Map<?, ?> m)) throw new IllegalArgumentException(label + "을(를) 입력해주세요.");

        String image = m.get("image") == null ? "" : String.valueOf(m.get("image"));
        String text = m.get("text") == null ? "" : String.valueOf(m.get("text")).trim();
        String name = m.get("name") == null ? "" : String.valueOf(m.get("name")).trim();

        Map<String, String> out = new HashMap<>();
        out.put("name", name.length() > 60 ? name.substring(0, 60) : name);

        if (!image.isBlank()) {

            int comma = image.indexOf(',');
            if (!image.startsWith("data:") || comma < 0 || !image.substring(0, comma).endsWith(";base64")) {
                throw new IllegalArgumentException(label + ": 사진 형식을 확인해주세요.");
            }

            String mediaType = image.substring(5, comma - ";base64".length());
            String base64 = image.substring(comma + 1);

            if (!IMAGE_TYPES.contains(mediaType)) throw new IllegalArgumentException(label + ": JPG, PNG, WEBP 사진만 올릴 수 있어요.");
            if (base64.length() > MAX_IMAGE_CHARS / 2) throw new IllegalArgumentException(label + ": 사진이 너무 커요.");

            out.put("image", base64);
            out.put("mediaType", mediaType);

        } else {

            if (text.isEmpty()) throw new IllegalArgumentException(label + ": 사진을 올리거나 전성분을 붙여 넣어주세요.");
            if (text.length() > MAX_TEXT) throw new IllegalArgumentException(label + ": 전성분은 " + MAX_TEXT + "자 이내로 붙여 넣어주세요.");

            out.put("text", text);

        }

        return out;

    }

    // ============================================
    // 기획 브리프
    // ============================================
    @PostMapping("/brief")
    public ResponseEntity<Map<String, Object>> brief(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        Long userId = getLoginUserId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        String text = req.get("request") == null ? "" : String.valueOf(req.get("request")).trim();
        if (text.length() < 10) return fail(HttpStatus.BAD_REQUEST, "어떤 제품을 원하는지 조금 더 자세히 적어주세요. (10자 이상)");
        if (text.length() > MAX_REQUEST) return fail(HttpStatus.BAD_REQUEST, "요청은 " + MAX_REQUEST + "자 이내로 적어주세요.");

        try {

            Map<String, Object> body = new LinkedHashMap<>(beautyLabService.brief(userId, text));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (RestClientResponseException e) {

            log.error("기획 브리프 AI 오류: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return fail(HttpStatus.BAD_GATEWAY, "AI 응답에 실패했어요. 잠시 후 다시 시도해주세요.");

        } catch (Exception e) {

            log.error("기획 브리프 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "브리프를 만드는 중 오류가 발생했어요.");

        }

    }

    // ============================================
    // 성분 사전 (누구나)
    // ============================================
    // ============================================
    // 🧪 처방 검토 (AI 없이 규칙만, 로그인 없이 누구나)
    // POST /api/beauty/formula/check
    //   { items: [{ name, pct, qs }], rinseOff, claims: ['미백'], kids }
    //   → { verdict, total, qsPct, issues, functional, label, items, ruleVersion }
    // ============================================
    @PostMapping("/formula/check")
    public ResponseEntity<Map<String, Object>> formulaCheck(@RequestBody Map<String, Object> req) {

        try {

            List<FormulaCheckService.Item> items = new ArrayList<>();

            if (req.get("items") instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> m)) continue;
                    String name = m.get("name") == null ? "" : String.valueOf(m.get("name"));
                    if (name.length() > 100) throw new IllegalArgumentException("원료 이름은 100자까지 쓸 수 있어요.");
                    boolean qs = Boolean.TRUE.equals(m.get("qs"));
                    Double pct = null;
                    if (!qs && m.get("pct") != null && !String.valueOf(m.get("pct")).isBlank()) {
                        try {
                            pct = Double.valueOf(String.valueOf(m.get("pct")).replace(",", "").replace("%", "").trim());
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("'" + name + "' 함량을 숫자로 넣어주세요.");
                        }
                    }
                    items.add(new FormulaCheckService.Item(name, pct, qs));
                }
            }

            List<String> claims = new ArrayList<>();
            if (req.get("claims") instanceof List<?> list) for (Object o : list) claims.add(String.valueOf(o));

            Map<String, Object> result = formulaCheckService.check(items,
                    Boolean.TRUE.equals(req.get("rinseOff")), claims, Boolean.TRUE.equals(req.get("kids")));

            Map<String, Object> body = new HashMap<>(result);
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("처방 검토 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "처방을 검토하지 못했습니다.");

        }

    }

    @GetMapping("/ingredients")
    public ResponseEntity<Map<String, Object>> ingredients(@RequestParam(required = false) String q) {

        String query = q == null ? "" : q.trim();
        if (query.length() > 50) query = query.substring(0, 50);

        List<IngredientVO> list = dictionary.search(query, query.isEmpty() ? 200 : 50);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("ingredients", list);
        body.put("total", dictionary.all().size());

        return ResponseEntity.ok(body);

    }

    // ============================================
    // 내 기록
    // ============================================
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> history(HttpServletRequest request) {

        Long userId = getLoginUserId(request);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("loggedIn", userId != null);
        body.put("limit", BeautyLabService.DAILY_LIMIT);

        if (userId == null) return ResponseEntity.ok(body);

        try {

            body.put("logs", beautyLabService.myLogs(userId));
            body.put("remainingAnalyze", beautyLabService.remaining(userId, "ANALYZE"));
            body.put("remainingBrief", beautyLabService.remaining(userId, "BRIEF"));
            body.put("remainingCompare", beautyLabService.remaining(userId, "COMPARE"));

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("뷰티 랩 기록 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "기록을 불러오지 못했어요.");

        }

    }

    @GetMapping("/history/{logId}")
    public ResponseEntity<Map<String, Object>> historyOne(@PathVariable Long logId, HttpServletRequest request) {

        Long userId = getLoginUserId(request);
        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            Map<String, Object> result = beautyLabService.myLog(userId, logId);
            if (result == null) return fail(HttpStatus.NOT_FOUND, "기록을 찾을 수 없어요.");

            Map<String, Object> body = new LinkedHashMap<>(result);
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("뷰티 랩 기록 조회 오류: {}", logId, e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "기록을 불러오지 못했어요.");

        }

    }

    // ============================================
    // 공통
    // ============================================

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
