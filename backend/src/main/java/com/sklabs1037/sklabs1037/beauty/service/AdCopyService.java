package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.dao.BeautyLabDao;
import com.sklabs1037.sklabs1037.beauty.service.AdCopyRules.Finding;
import com.sklabs1037.sklabs1037.beauty.service.AdCopyRules.Level;
import com.sklabs1037.sklabs1037.beauty.vo.BeautyLogVO;
import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * 📣 광고 문구 검수 (뷰티 랩)
 *
 * 1. 검사 (누구나, AI 없음): AdCopyRules로 걸리는 표현 · 이유 · 근거 · 바꿔 쓸 방향
 * 2. ✨ AI로 고쳐 쓰기 (로그인, 하루 20번): 걸린 표현 목록을 주고 광고 느낌은 살려 다시 쓰게 함
 *    → 서버가 고친 문구를 같은 규칙으로 다시 검사 (AI가 "고쳤다"고 해도 규칙이 최종 판단)
 *    → 기능성은 사용자가 고른 것만, 공식 효능 문구로
 * - 기록: T_BEAUTY_LOG KIND = 'ADCOPY' (최근 기록에서 다시 보기)
 */
@Slf4j
@Service
public class AdCopyService {

    public static final int MAX_TEXT = 2000;

    private static final String KIND = "ADCOPY";

    private static final String SYSTEM = """
            너는 화장품 회사의 광고 문구 검수 담당이다. 한국 「화장품법」 제13조와 시행규칙 [별표 5] 기준으로,
            서버가 찾은 "걸린 표현"을 모두 고쳐서 광고 문구를 다시 쓴다.

            규칙
            - 원래 문구의 길이·톤·문장 순서·제품 특징은 최대한 살린다. 새로운 효능이나 숫자를 지어내지 않는다.
            - 질병·치료·재생·염증·의사 추천·최고/최상·100%·부작용 없음 같은 표현은 쓰지 않는다.
            - 기능성 효능은 "심사·보고 받은 기능성" 목록에 있는 것만, 주어진 공식 효능 문구 그대로 쓴다. 목록에 없으면 그 효능을 말하지 않는다.
            - 실증이 필요한 표현(임상, 1위, 저자극, 붓기 등)은 근거가 없으므로 빼거나 "사용감" 표현으로 바꾼다.
            - changes에는 바꾼 곳만, before는 원문 표현 그대로, after는 바꾼 표현, why는 20자 이내.
            - 반드시 rewrite_copy 도구로만 답한다.
            """;

    private static final Map<String, Object> TOOL = Map.of(
            "name", "rewrite_copy",
            "description", "규정에 맞게 고친 광고 문구",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "rewritten", Map.of("type", "string"),
                            "changes", Map.of("type", "array", "maxItems", 20, "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "before", Map.of("type", "string"),
                                            "after", Map.of("type", "string"),
                                            "why", Map.of("type", "string")),
                                    "required", List.of("before", "after", "why")))),
                    "required", List.of("rewritten", "changes")));

    @Autowired
    private BeautyLabDao beautyLabDao;

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private JsonMapper jsonMapper;

    // ============================================
    // 1. 규칙 검사 (누구나)
    // ============================================
    public Map<String, Object> check(String text, List<String> claims) {

        String t = cleanText(text);
        List<String> owned = cleanClaims(claims);
        return result(t, owned, AdCopyRules.check(t, owned));

    }

    // ============================================
    // 2. AI로 고쳐 쓰기 (로그인, 하루 20번)
    // ============================================
    public Map<String, Object> rewrite(Long userId, String text, List<String> claims) {

        String t = cleanText(text);
        List<String> owned = cleanClaims(claims);

        List<Finding> findings = AdCopyRules.check(t, owned);
        if (findings.stream().noneMatch(f -> f.level() != Level.INFO)) {
            throw new IllegalArgumentException("고칠 표현이 없어요. 그대로 써도 돼요.");
        }

        if (count(userId) >= BeautyLabService.DAILY_LIMIT) {
            throw new IllegalArgumentException("오늘은 " + BeautyLabService.DAILY_LIMIT + "번까지 쓸 수 있어요. 내일 다시 시도해주세요.");
        }
        if (!claudeApiClient.isConfigured()) throw new IllegalStateException("AI 기능이 설정되지 않았습니다.");

        StringBuilder sb = new StringBuilder("## 원래 문구\n").append(t).append("\n\n## 심사·보고 받은 기능성\n");
        if (owned.isEmpty()) sb.append("(없음 → 미백·주름·자외선·여드름·탈모·아토피 효능은 말하지 않기)\n");
        for (String c : owned) sb.append("- ").append(c).append(": \"").append(AdCopyRules.CLAIMS.get(c)).append("\"\n");
        sb.append("\n## 서버가 찾은 걸린 표현\n");
        for (Finding f : findings) {
            if (f.level() == Level.INFO) continue;
            sb.append("- \"").append(f.phrase()).append("\" [").append(f.category()).append("] ").append(f.reason()).append(" → ").append(f.suggest()).append("\n");
        }

        Map<String, Object> response = claudeApiClient.createMessage(SYSTEM, List.of(TOOL),
                List.of(Map.of("role", "user", "content", sb.toString())), Map.of("type", "tool", "name", "rewrite_copy"), 2000);

        Map<?, ?> input = toolInput(response);
        String rewritten = cut(str(input.get("rewritten")), MAX_TEXT + 500);
        if (rewritten.isEmpty()) throw new IllegalStateException("AI가 문구를 고치지 못했어요. 잠시 후 다시 시도해주세요.");

        Map<String, Object> out = result(t, owned, findings);

        // 서버가 같은 규칙으로 다시 검사
        List<Finding> after = AdCopyRules.check(rewritten, owned);
        Map<String, Object> rw = new LinkedHashMap<>();
        rw.put("text", rewritten);
        rw.put("changes", changes(input.get("changes")));
        rw.put("verdict", AdCopyRules.verdict(after));
        rw.put("findings", after.stream().map(AdCopyService::toMap).toList());
        rw.put("errorCount", after.stream().filter(f -> f.level() == Level.ERROR).count());
        rw.put("warnCount", after.stream().filter(f -> f.level() == Level.WARN).count());
        out.put("rewrite", rw);

        out.put("logId", saveLog(userId, cut(t.replaceAll("\\s+", " "), 40), findings.size(), out));
        out.put("remaining", Math.max(0, BeautyLabService.DAILY_LIMIT - count(userId)));
        return out;

    }

    public int remaining(Long userId) {

        return Math.max(0, BeautyLabService.DAILY_LIMIT - count(userId));

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================
    static Map<String, Object> result(String text, List<String> claims, List<Finding> findings) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", text);
        m.put("claims", claims);
        m.put("verdict", AdCopyRules.verdict(findings));
        m.put("errorCount", findings.stream().filter(f -> f.level() == Level.ERROR).count());
        m.put("warnCount", findings.stream().filter(f -> f.level() == Level.WARN).count());
        m.put("findings", findings.stream().map(AdCopyService::toMap).toList());
        m.put("ruleVersion", AdCopyRules.RULE_VERSION);
        return m;

    }

    static Map<String, Object> toMap(Finding f) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("start", f.start());
        m.put("end", f.end());
        m.put("phrase", f.phrase());
        m.put("level", f.level().name());
        m.put("category", f.category());
        m.put("reason", f.reason());
        m.put("suggest", f.suggest());
        m.put("basis", f.basis());
        return m;

    }

    static List<Map<String, String>> changes(Object raw) {

        List<Map<String, String>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m) || out.size() >= 20) continue;
            String before = cut(str(m.get("before")), 80);
            String after = cut(str(m.get("after")), 80);
            if (before.isEmpty() && after.isEmpty()) continue;
            Map<String, String> c = new LinkedHashMap<>();
            c.put("before", before);
            c.put("after", after);
            c.put("why", cut(str(m.get("why")), 40));
            out.add(c);
        }
        return out;

    }

    static String cleanText(String text) {

        String t = text == null ? "" : text.replace("\r\n", "\n").strip();
        if (t.length() < 5) throw new IllegalArgumentException("검사할 광고 문구를 5자 이상 넣어주세요.");
        if (t.length() > MAX_TEXT) throw new IllegalArgumentException("광고 문구는 " + MAX_TEXT + "자까지 넣을 수 있어요.");
        return t;

    }

    static List<String> cleanClaims(List<String> claims) {

        if (claims == null) return List.of();
        return claims.stream().filter(AdCopyRules.CLAIMS::containsKey).distinct().toList();

    }

    // ============================================
    // 공통
    // ============================================
    private int count(Long userId) {

        BeautyLogVO p = new BeautyLogVO();
        p.setUserId(userId);
        p.setKind(KIND);
        return beautyLabDao.countToday(p);

    }

    private Long saveLog(Long userId, String title, int count, Map<String, Object> result) {

        try {

            BeautyLogVO vo = new BeautyLogVO();
            vo.setUserId(userId);
            vo.setKind(KIND);
            vo.setInputType("TEXT");
            vo.setTitle(title);
            vo.setItemCount(count);
            vo.setResultJson(jsonMapper.writeValueAsString(result));
            beautyLabDao.insertLog(vo);
            return vo.getLogId();

        } catch (Exception e) {

            log.warn("광고 문구 기록 저장 실패: {}", e.getMessage());
            return null;

        }

    }

    private static Map<?, ?> toolInput(Map<String, Object> response) {

        if (response != null && response.get("content") instanceof List<?> blocks) {
            for (Object b : blocks) {
                if (b instanceof Map<?, ?> m && "tool_use".equals(m.get("type")) && m.get("input") instanceof Map<?, ?> in) return in;
            }
        }
        throw new IllegalStateException("AI 응답에서 결과를 찾지 못했어요.");

    }

    private static String str(Object o) {

        return o == null ? "" : String.valueOf(o).strip();

    }

    private static String cut(String s, int max) {

        return s.length() > max ? s.substring(0, max) : s;

    }
}
