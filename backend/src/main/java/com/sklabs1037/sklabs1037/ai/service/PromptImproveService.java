package com.sklabs1037.sklabs1037.ai.service;

import com.sklabs1037.sklabs1037.ai.dao.AiPromptDao;
import com.sklabs1037.sklabs1037.ai.vo.AiPromptVO;
import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import com.sklabs1037.sklabs1037.ops.service.OpsAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 🔁 스스로 고치는 AI 비서
 *
 * 매일 04:10 (PromptImproveScheduler → OpsJobs "AI_IMPROVE"), 또는 관리자 "지금 실행"
 *   1. 재료 모으기 : 지난번 개선 이후 👎 받은 대화 (3개 미만이면 쉼)
 *   2. 분석·개선안 : Claude가 실패 패턴을 찾고 시스템 프롬프트를 고침 (propose_prompt 도구)
 *                   → 서버 확인: 안전 규칙 문구가 남아 있는지, 길이, 실제로 바뀌었는지
 *   3. 채점        : 👎 질문 4개 + 👍 질문 2개를 옛·새 프롬프트로 각각 다시 답하게 하고
 *                   AI 심판이 A/B 순서를 섞어서 1~10점 (judge_answers 도구, 자리 편향 줄이기)
 *   4. 후보 저장   : 새 평균이 0.5점 이상 높고 👍 질문에서 1점 넘게 떨어지지 않으면 "추천"
 *                   → 관리자 메일, /admin/ai-improve 에서 사람이 보고 적용 / 거절 / 되돌리기
 *
 * - 자동 적용은 하지 않음 (사람이 최종 확인) → 어떤 버전이 언제 적용됐는지 기록, 버전별 👎 비율 비교
 */
@Slf4j
@Service
public class PromptImproveService {

    static final int MIN_NEW_DOWN = 3;
    private static final int MAX_DOWN = 20;
    private static final int EVAL_DOWN = 4;
    private static final int EVAL_UP = 2;
    private static final int MAX_PROMPT = 4000;
    private static final double RECOMMEND_MARGIN = 0.5;
    private static final double UP_REGRESSION = 1.0;

    // 새 프롬프트에 반드시 남아 있어야 하는 안전 규칙 (AI가 지우면 개선안 거절)
    static final List<String> REQUIRED_PHRASES = List.of("투자 판단은 본인 책임입니다", "지어내지", "도구");

    private static final String PROPOSE_SYSTEM = """
            너는 한국 주식 분석 AI 비서의 시스템 프롬프트를 고치는 프롬프트 엔지니어다.
            사용자에게 👎를 받은 대화들을 보고 공통 원인을 찾아, 시스템 프롬프트를 최소한으로 고친다.

            규칙
            - 기존 규칙(숫자는 도구로 조회한 값만, 투자 권유 금지, "투자 판단은 본인 책임입니다." 문구, 답변 형식)은 지우지 않는다.
            - 특정 질문 하나의 정답을 박아 넣지 말고, 같은 종류의 질문 전체에 통하는 행동 규칙으로 쓴다.
            - 도구 이름은 프롬프트에 이미 나온 것만 쓴다. 없는 도구를 만들지 않는다.
            - 데이터가 없거나 도구가 못 하는 일 때문에 생긴 문제는 fixable=false로 표시하고 프롬프트로 억지로 고치지 않는다.
            - 새 프롬프트는 전문(全文)으로, 4000자 이내.
            - 반드시 propose_prompt 도구로만 답한다.
            """;

    private static final Map<String, Object> PROPOSE_TOOL = Map.of(
            "name", "propose_prompt",
            "description", "👎 대화의 실패 패턴과 고친 시스템 프롬프트를 낸다.",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "patterns", Map.of("type", "array", "maxItems", 5, "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "problem", Map.of("type", "string", "description", "무엇이 문제였나 (한 문장)"),
                                            "chatIds", Map.of("type", "array", "items", Map.of("type", "integer")),
                                            "fixable", Map.of("type", "boolean", "description", "프롬프트로 고칠 수 있는 문제인가"),
                                            "fix", Map.of("type", "string", "description", "프롬프트에서 어떻게 고쳤나 (고칠 수 없으면 무엇이 필요한가)")),
                                    "required", List.of("problem", "fixable", "fix"))),
                            "changes", Map.of("type", "array", "maxItems", 6, "items", Map.of("type", "string"),
                                    "description", "바꾼 점 요약 (한 줄씩)"),
                            "newPrompt", Map.of("type", "string", "description", "고친 시스템 프롬프트 전문")),
                    "required", List.of("patterns", "changes", "newPrompt")));

    private static final String JUDGE_SYSTEM = """
            너는 한국 주식 분석 AI 비서의 답변을 비교 채점하는 심판이다.
            같은 질문에 대한 답변 A와 B를 1~10점으로 채점한다.

            기준 (중요한 순)
            1. 질문에 실제로 답했는가 (엉뚱한 답, 되묻기만 하기, 회피는 감점)
            2. 숫자·사실을 도구로 조회한 근거로 말했는가 (사용 도구 목록 참고, 도구 없이 숫자를 말하면 크게 감점)
            3. 규칙: 사라/팔라 권유 금지, 끝에 "투자 판단은 본인 책임입니다." 문구, 마크다운 표·제목 금지
            4. 간결하고 읽기 쉬운가
            - 너는 숫자가 실제로 맞는지 확인할 수 없다. 길이나 말투가 아니라 위 기준으로만 본다.
            - 원래 사용자가 👎를 줬던 질문이면, 그 불만이 고쳐졌는지 특히 본다.
            - 반드시 judge_answers 도구로만 답한다.
            """;

    private static final Map<String, Object> JUDGE_TOOL = Map.of(
            "name", "judge_answers",
            "description", "답변 A·B 채점 결과",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "scoreA", Map.of("type", "integer", "minimum", 1, "maximum", 10),
                            "scoreB", Map.of("type", "integer", "minimum", 1, "maximum", 10),
                            "winner", Map.of("type", "string", "enum", List.of("A", "B", "TIE")),
                            "reason", Map.of("type", "string", "description", "판정 이유 (100자 이내)")),
                    "required", List.of("scoreA", "scoreB", "winner", "reason")));

    @Autowired
    private AiPromptDao aiPromptDao;

    @Autowired
    private PromptStore promptStore;

    @Autowired
    private IAiChatService aiChatService;

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private OpsAlertService opsAlertService;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${app.frontend-url}")
    private String siteUrl;

    // ============================================
    // 개선 한 번 (작업 AI_IMPROVE)
    // ============================================
    public JobOutcome run() {

        // 1. 재료
        AiPromptVO param = new AiPromptVO();
        param.setSinceAt(aiPromptDao.selectLastRunAt());
        param.setLimit(MAX_DOWN);

        List<AiPromptVO> downs = aiPromptDao.selectDownChats(param);

        if (downs.size() < MIN_NEW_DOWN) {
            return JobOutcome.of(0, 0, "새 👎 " + downs.size() + "개 (" + MIN_NEW_DOWN + "개 이상 모이면 개선해요)");
        }

        if (!claudeApiClient.isConfigured()) throw new IllegalStateException("AI 키가 설정되지 않았습니다.");

        PromptStore.Active active = promptStore.active();

        // 2. 분석·개선안
        Map<String, Object> proposal = callTool(PROPOSE_SYSTEM, PROPOSE_TOOL, proposePrompt(active, downs), 6000);
        String newPrompt = str(proposal.get("newPrompt")).trim();

        AiPromptVO vo = new AiPromptVO();
        vo.setVersion(aiPromptDao.selectMaxVersion() + 1);
        vo.setBaseVersion(active.version());
        vo.setContent(newPrompt.isEmpty() ? "(빈 프롬프트)" : newPrompt);
        vo.setAnalysisJson(json(proposal.get("patterns")));
        vo.setChangesJson(json(proposal.get("changes")));
        vo.setDownCount(downs.size());
        vo.setRecommendYn("N");

        String invalid = validate(active.content(), newPrompt);

        if (invalid != null) {
            vo.setStatus("REJECTED");
            vo.setEvalJson(json(List.of(Map.of("error", invalid))));
            aiPromptDao.insertPrompt(vo);
            return JobOutcome.of(1, 1, "v" + vo.getVersion() + " 개선안 자동 거절: " + invalid);
        }

        // 3. 채점 (👎 질문 + 👍 질문)
        List<AiPromptVO> evalSet = new ArrayList<>(downs.subList(0, Math.min(EVAL_DOWN, downs.size())));
        evalSet.addAll(aiPromptDao.selectUpChats(EVAL_UP));

        List<Map<String, Object>> evals = new ArrayList<>();
        int failed = 0;

        for (AiPromptVO q : evalSet) {

            try {
                evals.add(evaluate(q, active.content(), newPrompt));
            } catch (Exception e) {
                failed++;
                log.warn("프롬프트 채점 실패 (대화 {}): {}", q.getChatId(), e.getMessage());
            }

        }

        // 4. 점수 · 추천
        Score s = score(evals);

        vo.setStatus("CANDIDATE");
        vo.setEvalJson(json(evals));
        vo.setScoreOld(s.oldAvg());
        vo.setScoreNew(s.newAvg());
        vo.setRecommendYn(s.recommend() ? "Y" : "N");
        aiPromptDao.insertPrompt(vo);

        String summary = "v" + vo.getVersion() + " (v" + active.version() + " 개선) 옛 " + s.oldAvg() + "점 → 새 " + s.newAvg() + "점"
                + (s.recommend() ? " · 추천" : " · 보류") + " (채점 " + evals.size() + "문항)";

        opsAlertService.send("[SKLabs] AI 비서 개선안 " + summary,
                "👎 " + downs.size() + "개를 분석해서 시스템 프롬프트 개선안을 만들었어요.\n" + summary
                        + "\n\n확인하고 적용하기: " + siteUrl + "/admin/ai-improve");

        return JobOutcome.of(evalSet.size(), failed, summary);

    }

    // 질문 하나: 옛·새 프롬프트로 각각 답 → A/B 순서를 섞어서 심판
    private Map<String, Object> evaluate(AiPromptVO q, String oldPrompt, String newPrompt) {

        Map<String, Object> oldAnswer = aiChatService.answerWith(oldPrompt, q.getQuestion(), q.getUserId());
        Map<String, Object> newAnswer = aiChatService.answerWith(newPrompt, q.getQuestion(), q.getUserId());

        boolean newIsA = ThreadLocalRandom.current().nextBoolean();
        Map<String, Object> a = newIsA ? newAnswer : oldAnswer;
        Map<String, Object> b = newIsA ? oldAnswer : newAnswer;

        String input = "질문: " + q.getQuestion() + "\n"
                + ("DOWN".equals(q.getRating()) ? "(원래 사용자가 👎를 준 질문. 그때 받은 답 앞부분: " + cut(q.getAnswer(), 400) + ")\n" : "")
                + "\n[답변 A] 사용 도구: " + a.get("tools") + "\n" + cut(str(a.get("answer")), 2500)
                + "\n\n[답변 B] 사용 도구: " + b.get("tools") + "\n" + cut(str(b.get("answer")), 2500);

        Map<String, Object> j = callTool(JUDGE_SYSTEM, JUDGE_TOOL, input, 600);

        int scoreA = clampScore(j.get("scoreA"));
        int scoreB = clampScore(j.get("scoreB"));
        String winner = str(j.get("winner"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chatId", q.getChatId());
        m.put("kind", q.getRating());                         // DOWN: 고칠 질문 / UP: 망치면 안 되는 질문
        m.put("question", cut(q.getQuestion(), 300));
        m.put("oldScore", newIsA ? scoreB : scoreA);
        m.put("newScore", newIsA ? scoreA : scoreB);
        m.put("winner", "TIE".equals(winner) ? "TIE" : ("A".equals(winner) == newIsA ? "NEW" : "OLD"));
        m.put("reason", cut(str(j.get("reason")), 200));
        m.put("oldAnswer", cut(str(oldAnswer.get("answer")), 1500));
        m.put("newAnswer", cut(str(newAnswer.get("answer")), 1500));
        m.put("oldTools", oldAnswer.get("tools"));
        m.put("newTools", newAnswer.get("tools"));

        return m;

    }

    record Score(BigDecimal oldAvg, BigDecimal newAvg, boolean recommend) {
    }

    // 평균 점수 + 추천 여부 (순수 계산, 테스트 가능)
    static Score score(List<Map<String, Object>> evals) {

        if (evals.isEmpty()) return new Score(BigDecimal.ZERO, BigDecimal.ZERO, false);

        double oldSum = 0;
        double newSum = 0;
        boolean upBroken = false;

        for (Map<String, Object> e : evals) {

            int o = ((Number) e.get("oldScore")).intValue();
            int n = ((Number) e.get("newScore")).intValue();
            oldSum += o;
            newSum += n;

            if ("UP".equals(e.get("kind")) && n < o - UP_REGRESSION) upBroken = true;

        }

        BigDecimal oldAvg = BigDecimal.valueOf(oldSum / evals.size()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newAvg = BigDecimal.valueOf(newSum / evals.size()).setScale(2, RoundingMode.HALF_UP);

        boolean recommend = !upBroken && newAvg.subtract(oldAvg).doubleValue() >= RECOMMEND_MARGIN;

        return new Score(oldAvg, newAvg, recommend);

    }

    // 새 프롬프트 확인 (문제 있으면 이유, 괜찮으면 null)
    static String validate(String oldPrompt, String newPrompt) {

        if (newPrompt == null || newPrompt.isBlank()) return "새 프롬프트가 비어 있어요.";
        if (newPrompt.length() > MAX_PROMPT) return "새 프롬프트가 " + MAX_PROMPT + "자를 넘어요.";
        if (newPrompt.strip().equals(oldPrompt.strip())) return "바뀐 내용이 없어요.";

        for (String phrase : REQUIRED_PHRASES) {
            if (oldPrompt.contains(phrase) && !newPrompt.contains(phrase)) return "안전 규칙 문구(\"" + phrase + "\")가 빠졌어요.";
        }

        return null;

    }

    private static String proposePrompt(PromptStore.Active active, List<AiPromptVO> downs) {

        StringBuilder sb = new StringBuilder();
        sb.append("## 지금 시스템 프롬프트 (버전 ").append(active.version()).append(")\n")
                .append(active.content()).append("\n\n## 👎 받은 대화 ").append(downs.size()).append("개\n");

        for (AiPromptVO d : downs) {
            sb.append("\n### 대화 #").append(d.getChatId()).append('\n')
                    .append("질문: ").append(cut(d.getQuestion(), 400)).append('\n')
                    .append("사용 도구: ").append(d.getToolsUsed() == null ? "(없음)" : d.getToolsUsed()).append('\n')
                    .append("답변: ").append(cut(d.getAnswer(), 900)).append('\n');
        }

        sb.append("\n공통 원인을 찾고, 시스템 프롬프트를 고쳐줘.");

        return sb.toString();

    }

    // ============================================
    // 관리자 화면
    // ============================================
    public Map<String, Object> overview() {

        PromptStore.Active active = promptStore.active();

        List<Map<String, Object>> prompts = new ArrayList<>();
        for (AiPromptVO p : aiPromptDao.selectPrompts(20)) prompts.add(toMap(p, true));

        Map<Integer, Map<String, Object>> stats = new TreeMap<>(Comparator.reverseOrder());
        for (AiPromptVO s : aiPromptDao.selectVersionStats()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("version", s.getVersion());
            m.put("chatCount", s.getChatCount());
            m.put("ratedCount", s.getRatedCount());
            m.put("upCount", s.getUpCount());
            m.put("downCount", s.getDownRatedCount());
            stats.put(s.getVersion(), m);
        }

        AiPromptVO param = new AiPromptVO();
        param.setSinceAt(aiPromptDao.selectLastRunAt());
        param.setLimit(MAX_DOWN);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeVersion", active.version());
        result.put("activeContent", active.content());
        result.put("basePrompt", AiChatServiceImpl.BASE_PROMPT);
        result.put("prompts", prompts);
        result.put("versionStats", new ArrayList<>(stats.values()));
        result.put("newDownCount", aiPromptDao.selectDownChats(param).size());
        result.put("minNewDown", MIN_NEW_DOWN);

        return result;

    }

    // 적용 (후보 → 적용, 또는 예전 버전으로 되돌리기)
    public void activate(int version, Long adminId) {

        if (version == 0) {
            aiPromptDao.retireActive(new AiPromptVO());
            promptStore.invalidate();
            return;
        }

        AiPromptVO p = aiPromptDao.selectByVersion(version);
        if (p == null) throw new IllegalArgumentException("없는 버전입니다.");
        if ("ACTIVE".equals(p.getStatus())) throw new IllegalArgumentException("이미 적용 중인 버전이에요.");
        if ("REJECTED".equals(p.getStatus()) && p.getScoreNew() == null) throw new IllegalArgumentException("자동 거절된 개선안은 적용할 수 없어요.");

        aiPromptDao.retireActive(new AiPromptVO());

        AiPromptVO u = new AiPromptVO();
        u.setVersion(version);
        u.setStatus("ACTIVE");
        u.setDecidedBy(adminId);
        aiPromptDao.updateStatus(u);

        promptStore.invalidate();

    }

    public void reject(int version, Long adminId) {

        AiPromptVO p = aiPromptDao.selectByVersion(version);
        if (p == null || !"CANDIDATE".equals(p.getStatus())) throw new IllegalArgumentException("거절할 수 있는 개선안이 아니에요.");

        AiPromptVO u = new AiPromptVO();
        u.setVersion(version);
        u.setStatus("REJECTED");
        u.setDecidedBy(adminId);
        aiPromptDao.updateStatus(u);

    }

    private Map<String, Object> toMap(AiPromptVO p, boolean withDetail) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", p.getVersion());
        m.put("baseVersion", p.getBaseVersion());
        m.put("status", p.getStatus());
        m.put("scoreOld", p.getScoreOld());
        m.put("scoreNew", p.getScoreNew());
        m.put("recommend", "Y".equals(p.getRecommendYn()));
        m.put("downCount", p.getDownCount());
        m.put("insAt", p.getInsAt());
        m.put("decidedAt", p.getDecidedAt());

        if (withDetail) {
            m.put("content", p.getContent());
            m.put("patterns", parse(p.getAnalysisJson()));
            m.put("changes", parse(p.getChangesJson()));
            m.put("evals", parse(p.getEvalJson()));
        }

        return m;

    }

    // ============================================
    // 작은 도구들
    // ============================================
    @SuppressWarnings("unchecked")
    private Map<String, Object> callTool(String system, Map<String, Object> tool, String userText, int maxTokens) {

        String name = String.valueOf(tool.get("name"));
        Map<String, Object> response = claudeApiClient.createMessage(system, List.of(tool),
                List.of(Map.of("role", "user", "content", userText)), Map.of("type", "tool", "name", name), maxTokens);

        Object content = response == null ? null : response.get("content");

        if (content instanceof List<?> list) {
            for (Object block : list) {
                if (block instanceof Map<?, ?> b && "tool_use".equals(b.get("type")) && name.equals(b.get("name"))
                        && b.get("input") instanceof Map<?, ?> input) {
                    return (Map<String, Object>) input;
                }
            }
        }

        throw new IllegalStateException("AI가 " + name + " 양식으로 답하지 않았습니다.");

    }

    private String json(Object o) {

        try {
            return o == null ? null : jsonMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }

    }

    private Object parse(String json) {

        if (json == null || json.isBlank()) return List.of();

        try {
            return jsonMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return List.of();
        }

    }

    private static int clampScore(Object o) {

        try {
            return Math.max(1, Math.min(10, (int) Math.round(Double.parseDouble(String.valueOf(o)))));
        } catch (NumberFormatException e) {
            return 5;
        }

    }

    private static String str(Object o) {

        return o == null ? "" : String.valueOf(o);

    }

    private static String cut(String s, int max) {

        if (s == null) return "";

        return s.length() <= max ? s : s.substring(0, max) + "…";

    }
}
