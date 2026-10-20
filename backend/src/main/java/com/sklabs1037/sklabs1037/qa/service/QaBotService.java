package com.sklabs1037.sklabs1037.qa.service;

import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.ops.service.JobRunService;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import com.sklabs1037.sklabs1037.ops.service.OpsAlertService;
import com.sklabs1037.sklabs1037.qa.dao.QaRunDao;
import com.sklabs1037.sklabs1037.qa.vo.QaRunVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 🤖 AI QA 봇 (서버 쪽)
 *
 * GitHub Actions(qa-bot.yml, 매일 08:30 + 배포 직후)가 진짜 브라우저로 운영 사이트를 휴대폰·PC 크기로 열고 눌러본 결과를
 * POST /api/qa/report 로 보냄 → 여기서
 *   1. 토큰 확인 (QA_TOKEN, 서버 .env 와 GitHub Secrets 에 같은 값)
 *   2. 저장 후 바로 응답 (분석은 별도 스레드, 작업 이름 QA_BOT → 운영 화면에 기록, 밖에서 들어온 요청이라 MANUAL로 기록 → 로컬 PC에서도 분석)
 *   3. 규칙 판정 : 페이지 오류, 서버 오류(5xx), 기능 흐름 실패 → 심각 / 콘솔 에러, 가로 넘침, 6초 넘게 느림 → 주의
 *   4. AI 판정   : Claude가 캡처를 보고 깨진 화면(겹친 글자, 잘린 버튼, 계속 "불러오는 중", 에러 문구, 빈 화면)을 찾음
 *   5. 심각이 있으면 FAIL, 주의만 있으면 WARN → 관리자 메일 + /admin/qa 에서 캡처와 함께 확인
 */
@Slf4j
@Service
public class QaBotService {

    private static final int KEEP_RUNS = 30;
    private static final int MAX_CHECKS = 24;
    private static final int MAX_SHOT_CHARS = 600_000;   // 캡처 한 장 base64 (약 450KB)
    private static final int SLOW_MS = 6000;
    private static final int OVERFLOW_PX = 4;

    private static final String VISION_SYSTEM = """
            너는 한국어 웹사이트(SKLabs: 주식 시세·모의투자·AI 비서·화장품 성분 도구)의 QA 엔지니어다.
            휴대폰(390px)과 PC(1400px) 화면 캡처를 보고, 사용자가 바로 알아챌 화면 문제만 찾는다.

            찾을 것
            - 글자·버튼이 겹치거나 잘림, 화면 밖으로 넘침, 가로 스크롤
            - "불러오는 중..."이 그대로 남아 있음, 빈 카드·빈 목록인데 안내가 없음
            - 에러 문구(서버에 연결할 수 없습니다, 불러오지 못했습니다 등), 깨진 이미지·아이콘
            - 읽기 어려운 대비, 정렬이 무너진 표·카드
            찾지 말 것
            - 디자인 취향, 데이터 값(가격·수익률)이 맞는지, 캡처 아래가 잘린 것(캡처는 화면 일부만 찍음)
            - 다크 테마는 원래 디자인이다.
            - 확실하지 않으면 보고하지 않는다. 문제가 없으면 issues는 빈 배열.
            - 반드시 report_issues 도구로만 답한다.
            """;

    private static final Map<String, Object> REPORT_TOOL = Map.of(
            "name", "report_issues",
            "description", "화면 캡처에서 찾은 문제",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "summary", Map.of("type", "string", "description", "전체 상태 한 줄 요약 (80자 이내)"),
                            "issues", Map.of("type", "array", "maxItems", 10, "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "key", Map.of("type", "string", "description", "캡처 이름 (예: home-mobile)"),
                                            "severity", Map.of("type", "string", "enum", List.of("HIGH", "MEDIUM", "LOW")),
                                            "title", Map.of("type", "string", "description", "문제 한 줄"),
                                            "detail", Map.of("type", "string", "description", "어디가 어떻게 (100자 이내)")),
                                    "required", List.of("key", "severity", "title")))),
                    "required", List.of("summary", "issues")));

    @Value("${qa.token:}")
    private String qaToken;

    @Value("${app.frontend-url}")
    private String siteUrl;

    @Autowired
    private QaRunDao qaRunDao;

    @Autowired
    private JobRunService jobRunService;

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private OpsAlertService opsAlertService;

    @Autowired
    private JsonMapper jsonMapper;

    // ============================================
    // 1~2. 받기
    // ============================================
    public boolean configured() {

        return qaToken != null && !qaToken.isBlank();

    }

    // 토큰 비교 (시간 차이로 토큰을 알아내지 못하게 고정 시간 비교)
    public boolean tokenOk(String token) {

        if (!configured() || token == null) return false;

        return MessageDigest.isEqual(qaToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));

    }

    @SuppressWarnings("unchecked")
    public Long accept(Map<String, Object> payload) {

        if (!(payload.get("checks") instanceof List<?> raw) || raw.isEmpty()) throw new IllegalArgumentException("점검 결과가 없어요.");
        if (raw.size() > MAX_CHECKS) throw new IllegalArgumentException("점검 화면이 너무 많아요.");

        // 캡처는 따로 떼어서 저장 (목록 조회 때는 안 읽게)
        List<Map<String, Object>> checks = new ArrayList<>();
        Map<String, String> shots = new LinkedHashMap<>();

        for (Object o : raw) {

            if (!(o instanceof Map<?, ?> m)) continue;

            Map<String, Object> c = new LinkedHashMap<>((Map<String, Object>) m);
            String key = cut(str(c.get("key")), 60);
            Object shot = c.remove("screenshot");

            if (shot instanceof String s && !s.isBlank() && s.length() <= MAX_SHOT_CHARS) shots.put(key, s);

            c.put("key", key);
            checks.add(c);

        }

        QaRunVO vo = new QaRunVO();
        vo.setSource(List.of("SCHEDULE", "DEPLOY", "MANUAL").contains(str(payload.get("source"))) ? str(payload.get("source")) : "MANUAL");
        vo.setCommitSha(cut(str(payload.get("commit")), 40));
        vo.setChecksJson(jsonMapper.writeValueAsString(checks));
        vo.setShotsJson(jsonMapper.writeValueAsString(shots));
        qaRunDao.insertRun(vo);

        Long runId = vo.getRunId();

        Thread worker = new Thread(() -> jobRunService.execute("QA_BOT", "MANUAL", () -> analyze(runId, checks, shots)), "qa-bot-" + runId);
        worker.setDaemon(true);
        worker.start();

        return runId;

    }

    // ============================================
    // 3~5. 분석
    // ============================================
    private JobOutcome analyze(Long runId, List<Map<String, Object>> checks, Map<String, String> shots) {

        List<Map<String, Object>> findings = new ArrayList<>(ruleFindings(checks));
        String summary;
        boolean aiFailed = false;

        try {

            Map<String, Object> ai = vision(shots);
            summary = cut(str(ai.get("summary")), 300);

            if (ai.get("issues") instanceof List<?> issues) {
                for (Object o : issues) {
                    if (!(o instanceof Map<?, ?> m)) continue;
                    String sev = str(m.get("severity"));
                    if (!List.of("HIGH", "MEDIUM", "LOW").contains(sev)) sev = "LOW";
                    findings.add(finding(sev, str(m.get("key")), cut(str(m.get("title")), 120), cut(str(m.get("detail")), 200), "AI"));
                }
            }

        } catch (Exception e) {

            aiFailed = true;
            summary = "AI 화면 판정 실패 (" + cut(String.valueOf(e.getMessage()), 120) + ") · 규칙 판정만 했어요";
            log.warn("QA 봇 AI 판정 실패: {}", e.getMessage());

        }

        findings.sort(Comparator.comparingInt(f -> List.of("HIGH", "MEDIUM", "LOW").indexOf(str(f.get("severity")))));

        String status = status(findings);

        QaRunVO vo = new QaRunVO();
        vo.setRunId(runId);
        vo.setStatus(status);
        vo.setSummary(summary);
        vo.setFindingsJson(jsonMapper.writeValueAsString(findings));
        qaRunDao.updateRun(vo);

        QaRunVO keep = new QaRunVO();
        keep.setKeep(KEEP_RUNS);
        qaRunDao.deleteOld(keep);

        if (!"PASS".equals(status)) {

            StringBuilder body = new StringBuilder("AI QA 봇이 운영 사이트에서 문제를 찾았어요. (" + status + ")\n" + summary + "\n\n");
            findings.stream().filter(f -> !"LOW".equals(f.get("severity"))).limit(10)
                    .forEach(f -> body.append("- [").append(f.get("severity")).append("] ").append(f.get("key")).append(" · ")
                            .append(f.get("title")).append('\n'));
            body.append("\n캡처와 함께 보기: ").append(siteUrl).append("/admin/qa");

            opsAlertService.alert("QA_BOT_" + status, "QA 봇: " + ("FAIL".equals(status) ? "심각한 문제" : "확인할 문제") + " 발견", body.toString());

        }

        long serious = findings.stream().filter(f -> "HIGH".equals(f.get("severity"))).count();

        return JobOutcome.of(checks.size(), aiFailed ? 1 : 0, status + " · 화면 " + checks.size() + "개 · 문제 " + findings.size() + "개 (심각 " + serious + ")");

    }

    // 규칙 판정 (순수 계산, 테스트 가능)
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> ruleFindings(List<Map<String, Object>> checks) {

        List<Map<String, Object>> out = new ArrayList<>();

        for (Map<String, Object> c : checks) {

            String key = str(c.get("key"));

            for (Object e : list(c.get("pageErrors"))) {
                out.add(finding("HIGH", key, "페이지 오류", cut(str(e), 200), "RULE"));
            }

            for (Object r : list(c.get("failedRequests"))) {
                if (!(r instanceof Map<?, ?> m)) continue;
                int status = toInt(m.get("status"));
                String url = cut(str(m.get("url")), 160);
                if (status >= 500 || status == 0) out.add(finding("HIGH", key, "요청 실패 " + (status == 0 ? "(연결 안 됨)" : status), url, "RULE"));
                else if (status == 404) out.add(finding("MEDIUM", key, "없는 주소 404", url, "RULE"));
            }

            List<Object> consoleErrors = list(c.get("consoleErrors"));
            if (!consoleErrors.isEmpty()) {
                out.add(finding("MEDIUM", key, "콘솔 에러 " + consoleErrors.size() + "개", cut(str(consoleErrors.get(0)), 200), "RULE"));
            }

            int overflow = toInt(c.get("overflowPx"));
            if (overflow > OVERFLOW_PX) out.add(finding("MEDIUM", key, "가로로 " + overflow + "px 넘침", "화면 폭보다 넓은 요소가 있어 가로 스크롤이 생겨요", "RULE"));

            int loadMs = toInt(c.get("loadMs"));
            if (loadMs > SLOW_MS) out.add(finding("MEDIUM", key, "느림 " + String.format("%.1f", loadMs / 1000.0) + "초", "화면이 다 뜰 때까지 " + SLOW_MS / 1000 + "초 넘게 걸렸어요", "RULE"));

            if (c.get("flow") instanceof Map<?, ?> flow && !Boolean.TRUE.equals(flow.get("ok"))) {
                out.add(finding("HIGH", key, "기능 흐름 실패: " + str(flow.get("name")), cut(str(flow.get("message")), 200), "RULE"));
            }

        }

        return out;

    }

    static String status(List<Map<String, Object>> findings) {

        if (findings.stream().anyMatch(f -> "HIGH".equals(f.get("severity")))) return "FAIL";
        if (findings.stream().anyMatch(f -> "MEDIUM".equals(f.get("severity")))) return "WARN";

        return "PASS";

    }

    // Claude가 캡처 보기
    @SuppressWarnings("unchecked")
    private Map<String, Object> vision(Map<String, String> shots) {

        if (shots.isEmpty()) return Map.of("summary", "캡처 없음", "issues", List.of());
        if (!claudeApiClient.isConfigured()) throw new IllegalStateException("AI 키가 설정되지 않았습니다.");

        List<Map<String, Object>> content = new ArrayList<>();

        for (Map.Entry<String, String> e : shots.entrySet()) {
            content.add(Map.of("type", "text", "text", "캡처: " + e.getKey()));
            content.add(Map.of("type", "image", "source", Map.of("type", "base64", "media_type", "image/jpeg", "data", e.getValue())));
        }

        content.add(Map.of("type", "text", "text", "위 캡처들에서 사용자가 알아챌 화면 문제를 찾아줘. key는 캡처 이름 그대로."));

        Map<String, Object> response = claudeApiClient.createMessage(VISION_SYSTEM, List.of(REPORT_TOOL),
                List.of(Map.of("role", "user", "content", content)), Map.of("type", "tool", "name", "report_issues"), 2000);

        if (response != null && response.get("content") instanceof List<?> blocks) {
            for (Object b : blocks) {
                if (b instanceof Map<?, ?> m && "tool_use".equals(m.get("type")) && m.get("input") instanceof Map<?, ?> input) {
                    return (Map<String, Object>) input;
                }
            }
        }

        throw new IllegalStateException("AI가 보고서 양식으로 답하지 않았습니다.");

    }

    // ============================================
    // 관리자 화면
    // ============================================
    public List<Map<String, Object>> runs() {

        List<Map<String, Object>> list = new ArrayList<>();
        for (QaRunVO r : qaRunDao.selectRuns(KEEP_RUNS)) list.add(toMap(r, false));

        return list;

    }

    public Map<String, Object> run(Long runId) {

        QaRunVO r = qaRunDao.selectRun(runId);
        if (r == null) throw new IllegalArgumentException("없는 점검 기록입니다.");

        return toMap(r, true);

    }

    private Map<String, Object> toMap(QaRunVO r, boolean detail) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", r.getRunId());
        m.put("source", r.getSource());
        m.put("commit", r.getCommitSha());
        m.put("status", r.getStatus());
        m.put("summary", r.getSummary());
        m.put("insAt", r.getInsAt());

        List<Object> findings = parseList(r.getFindingsJson());
        m.put("findingCount", findings.size());
        m.put("findings", findings);

        if (detail) {
            m.put("checks", parseList(r.getChecksJson()));
            m.put("shots", parseObject(r.getShotsJson()));
        }

        return m;

    }

    // ============================================
    // 작은 도구들
    // ============================================
    private static Map<String, Object> finding(String severity, String key, String title, String detail, String by) {

        Map<String, Object> f = new LinkedHashMap<>();
        f.put("severity", severity);   // HIGH 심각 / MEDIUM 주의 / LOW 참고
        f.put("key", key);
        f.put("title", title);
        f.put("detail", detail);
        f.put("by", by);               // RULE 규칙 / AI 화면 판정

        return f;

    }

    private List<Object> parseList(String json) {

        if (json == null || json.isBlank()) return List.of();

        try {
            @SuppressWarnings("unchecked")
            List<Object> l = jsonMapper.readValue(json, List.class);
            return l;
        } catch (Exception e) {
            return List.of();
        }

    }

    private Object parseObject(String json) {

        if (json == null || json.isBlank()) return Map.of();

        try {
            return jsonMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }

    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {

        return o instanceof List<?> l ? (List<Object>) l : List.of();

    }

    private static int toInt(Object o) {

        if (o instanceof Number n) return n.intValue();

        try {
            return o == null ? 0 : (int) Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }

    }

    private static String str(Object o) {

        return o == null ? "" : String.valueOf(o);

    }

    private static String cut(String s, int max) {

        if (s == null) return null;

        return s.length() <= max ? s : s.substring(0, max);

    }
}
