package com.sklabs1037.sklabs1037.me.service;

import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.me.dao.MeDao;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

/**
 * 🤖 AI 주간 코치
 *
 * 흐름
 *   1. 매주 월요일 08:10 (WEEKLY_COACH): 지난주에 들어온 사람마다 지난주·지지난주 숫자를 모아
 *   2. Claude가 write_coach 도구로 한 줄 제목 · 돌아보기 · 잘한 점 · 이번 주 팁 · 미션 3개(종류·목표 숫자·이유)
 *   3. 서버가 확인: 미션 종류는 정해진 9개 중에서만, 목표 숫자는 종류별 범위로 자름, 모자라면 기본 미션으로 채움
 *      (AI 판단, 규칙은 서버 → 화면의 진행률은 항상 실제 기록으로 계산)
 *   4. 🔔 알림 "이번 주 AI 코치 리포트" → /activity 맨 위
 *   - 월요일에 없던 사람도 "지금 받아보기"로 그 주 리포트를 한 번 받을 수 있음 (한 주에 한 번)
 *   - 미션 3개를 다 채우면 🎖️ 미션 클리어 배지
 *
 * AI에게 투자 판단(종목 추천, 사라/팔아라)은 시키지 않음: 습관·공부 코치
 */
@Slf4j
@Service
public class CoachService {

    static final int MISSIONS = 3;
    private static final int JOB_LIMIT = 300;

    /** 미션 종류 (목표 숫자 범위, 진행률 계산 방법) */
    record Mission(String key, String icon, String format, int min, int max, ToIntFunction<RangeStats> metric, String link) {
        String title(int target) {
            return String.format(format, target);
        }
    }

    static final List<Mission> CATALOG = List.of(
            new Mission("ATTEND", "📅", "이번 주 %d일 출석", 2, 7, RangeStats::attendDays, "/"),
            new Mission("PREDICT", "🎯", "오를까 내릴까 %d개 예측", 3, 30, RangeStats::predictVotes, "/predict"),
            new Mission("PREDICT_HIT", "✅", "예측 %d개 맞히기", 2, 15, RangeStats::predictHits, "/predict"),
            new Mission("QUIZ", "📰", "뉴스 퀴즈 %d일 풀기", 1, 7, RangeStats::quizPlays, "/quiz"),
            new Mission("QUIZ_SCORE", "🧠", "뉴스 퀴즈 정답 %d개", 3, 35, RangeStats::quizScore, "/quiz"),
            new Mission("TRADE", "💰", "모의투자 %d번 거래", 1, 20, RangeStats::trades, "/mock"),
            new Mission("BOARD", "💬", "토론방 글·댓글 %d개", 1, 10, s -> s.boardPosts() + s.comments(), "/stock"),
            new Mission("AI_CHAT", "🤖", "AI 비서에게 %d번 질문", 1, 10, RangeStats::aiChats, "/ai"),
            new Mission("BEAUTY", "🔬", "뷰티 랩 성분 분석 %d번", 1, 5, RangeStats::beautyLogs, "/beauty")
    );

    private static final String SYSTEM = """
            너는 SKLabs(모의투자·예측 게임·뉴스 퀴즈·종목 토론·AI 비서·뷰티 랩이 있는 사이트)의 친근한 주간 코치다.
            사용자의 지난주 활동 숫자를 보고 짧은 주간 리포트와 이번 주 미션 3개를 만든다.

            규칙
            - 주어진 숫자만 근거로 말한다. 없는 기록을 지어내지 않는다. 활동이 거의 없으면 부담 없이 시작할 수 있게 격려한다.
            - 투자 판단은 하지 않는다: 종목 추천, "사라/팔아라", 수익 보장 같은 말 금지. 습관·공부·기록 관점으로만 코칭한다.
            - 반말 금지, 존댓말(~해요체). 이모지는 문장마다 넣지 말고 headline에 1개 정도.
            - headline: 40자 이내 한 줄. review: 지난주 돌아보기 2~3문장. strength: 잘한 점 1문장. tip: 이번 주 팁 1~2문장.
            - missions: 서로 다른 종류 3개. 지난주보다 조금 높은 현실적인 목표 (지난주 0이면 작게 시작). why는 30자 이내.
            - 반드시 write_coach 도구로만 답한다.
            """;

    private static final Map<String, Object> TOOL = Map.of(
            "name", "write_coach",
            "description", "주간 코치 리포트와 이번 주 미션 3개",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "headline", Map.of("type", "string"),
                            "review", Map.of("type", "string"),
                            "strength", Map.of("type", "string"),
                            "tip", Map.of("type", "string"),
                            "missions", Map.of(
                                    "type", "array", "minItems", 3, "maxItems", 3,
                                    "items", Map.of(
                                            "type", "object",
                                            "properties", Map.of(
                                                    "key", Map.of("type", "string", "enum", CATALOG.stream().map(Mission::key).toList()),
                                                    "target", Map.of("type", "integer", "minimum", 1),
                                                    "why", Map.of("type", "string")),
                                            "required", List.of("key", "target", "why")))),
                    "required", List.of("headline", "review", "strength", "tip", "missions")));

    @Autowired
    private MeDao meDao;

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private BadgeService badgeService;

    @Autowired
    private JsonMapper jsonMapper;

    // 같은 사람이 버튼을 연달아 눌러 AI를 두 번 부르지 않게
    private final Set<Long> making = ConcurrentHashMap.newKeySet();

    public static LocalDate weekStart(LocalDate day) {

        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    }

    // ============================================
    // 화면: 이번 주 리포트 + 미션 진행률
    // ============================================
    @SuppressWarnings("unchecked")
    public Map<String, Object> view(Long userId) {

        LocalDate today = MockMarketHours.now().toLocalDate();
        LocalDate week = weekStart(today);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weekStart", week.toString());
        result.put("weekEnd", week.plusDays(6).toString());
        result.put("aiReady", claudeApiClient.isConfigured());

        Map<String, Object> row = meDao.selectCoach(userId, week);

        if (row == null) {
            result.put("report", null);
            return result;
        }

        Map<String, Object> report = jsonMapper.readValue(String.valueOf(row.get("reportJson")), LinkedHashMap.class);
        RangeStats now = RangeStats.of(meDao.selectRangeStats(userId, week, today));

        List<Map<String, Object>> missions = progress((List<Map<String, Object>>) report.get("missions"), now);
        boolean allDone = !missions.isEmpty() && missions.stream().allMatch(m -> Boolean.TRUE.equals(m.get("done")));
        if (allDone) badgeService.awardOne(userId, "COACH_CLEAR");

        report.put("missions", missions);
        result.put("report", report);
        result.put("lastWeek", jsonMapper.readValue(String.valueOf(row.get("statsJson")), LinkedHashMap.class));
        result.put("allDone", allDone);
        result.put("createdAt", row.get("insAt"));

        return result;

    }

    // "지금 받아보기" (이번 주 리포트가 없을 때 한 번)
    public Map<String, Object> create(Long userId) {

        if (!claudeApiClient.isConfigured()) throw new IllegalStateException("AI 키가 설정되지 않았어요.");

        LocalDate week = weekStart(MockMarketHours.now().toLocalDate());
        if (meDao.selectCoach(userId, week) == null) generate(userId, week, false);

        return view(userId);

    }

    // ============================================
    // 월요일 작업 (WEEKLY_COACH)
    // ============================================
    public JobOutcome runWeekly() {

        if (!claudeApiClient.isConfigured()) throw new IllegalStateException("AI 키가 설정되지 않았습니다.");

        LocalDate week = weekStart(MockMarketHours.now().toLocalDate());
        List<Long> users = meDao.selectActiveUsers(week.minusDays(7), week.minusDays(1), JOB_LIMIT);

        int made = 0;
        int failed = 0;

        for (Long userId : users) {

            if (meDao.selectCoach(userId, week) != null) continue;

            try {
                if (generate(userId, week, true)) made++;
            } catch (Exception e) {
                failed++;
                log.warn("AI 주간 코치 실패 user={} {}", userId, e.getMessage());
            }

        }

        return JobOutcome.of(made + failed, failed, week + " 주 리포트 " + made + "명 (지난주 활동 " + users.size() + "명)");

    }

    // ============================================
    // 리포트 만들기
    // ============================================
    @SuppressWarnings("unchecked")
    private boolean generate(Long userId, LocalDate week, boolean notify) {

        if (!making.add(userId)) return false;

        try {

            RangeStats last = RangeStats.of(meDao.selectRangeStats(userId, week.minusDays(7), week.minusDays(1)));
            RangeStats before = RangeStats.of(meDao.selectRangeStats(userId, week.minusDays(14), week.minusDays(8)));
            String nickname = Objects.toString(meDao.selectNickname(userId), "회원");

            String prompt = "닉네임: " + nickname + "\n"
                    + "지난주(" + week.minusDays(7) + " ~ " + week.minusDays(1) + "): " + describe(last) + "\n"
                    + "지지난주: " + describe(before) + "\n"
                    + "미션 종류: " + catalogText() + "\n"
                    + "이번 주(" + week + " 시작) 리포트와 미션 3개를 만들어줘.";

            Map<String, Object> response = claudeApiClient.createMessage(SYSTEM, List.of(TOOL),
                    List.of(Map.of("role", "user", "content", prompt)), Map.of("type", "tool", "name", "write_coach"), 1500);

            Map<String, Object> input = Map.of();
            if (response != null && response.get("content") instanceof List<?> blocks) {
                for (Object b : blocks) {
                    if (b instanceof Map<?, ?> m && "tool_use".equals(m.get("type")) && m.get("input") instanceof Map<?, ?> in) {
                        input = (Map<String, Object>) in;
                    }
                }
            }

            Map<String, Object> report = clean(input, last);

            int saved = meDao.insertCoach(userId, week, jsonMapper.writeValueAsString(last.toMap()), jsonMapper.writeValueAsString(report));

            if (saved > 0 && notify) {
                notificationService.notify(userId, "COACH", "🤖 이번 주 AI 코치 리포트가 왔어요",
                        String.valueOf(report.get("headline")), "/activity", "COACH:" + week);
            }

            return saved > 0;

        } finally {

            making.remove(userId);

        }

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================

    // AI 답 정리: 글자 수 자르기, 미션 종류·목표 확인, 모자라면 기본 미션
    static Map<String, Object> clean(Map<String, Object> input, RangeStats last) {

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("headline", text(input.get("headline"), 40, "이번 주도 SKLabs와 함께 가볍게 시작해요"));
        out.put("review", text(input.get("review"), 300, "지난주 기록을 바탕으로 이번 주 미션을 골라봤어요."));
        out.put("strength", text(input.get("strength"), 120, "SKLabs를 다시 찾아와 준 것부터 좋은 시작이에요."));
        out.put("tip", text(input.get("tip"), 200, "하루 5분, 예측 하나와 뉴스 퀴즈 하나로 시작해보세요."));

        List<Map<String, Object>> missions = new ArrayList<>();
        Set<String> used = new HashSet<>();

        if (input.get("missions") instanceof List<?> raw) {
            for (Object o : raw) {
                if (!(o instanceof Map<?, ?> m) || missions.size() >= MISSIONS) continue;
                Mission def = find(String.valueOf(m.get("key")));
                if (def == null || !used.add(def.key())) continue;
                int target = m.get("target") instanceof Number n ? n.intValue() : def.min();
                missions.add(mission(def, target, text(m.get("why"), 40, "")));
            }
        }

        // 기본 미션: 출석(지난주 +1) → 퀴즈 3일 → 예측 10개
        int[][] fallback = {{0, Math.max(3, last.attendDays() + 1)}, {3, 3}, {1, 10}};
        for (int[] f : fallback) {
            if (missions.size() >= MISSIONS) break;
            Mission def = CATALOG.get(f[0]);
            if (used.add(def.key())) missions.add(mission(def, f[1], "꾸준함이 제일 중요해요"));
        }

        out.put("missions", missions);
        return out;

    }

    // 저장된 미션 + 이번 주 실제 기록 → 진행률
    static List<Map<String, Object>> progress(List<Map<String, Object>> saved, RangeStats now) {

        List<Map<String, Object>> list = new ArrayList<>();
        if (saved == null) return list;

        for (Map<String, Object> m : saved) {
            Mission def = find(String.valueOf(m.get("key")));
            if (def == null) continue;
            int target = m.get("target") instanceof Number n ? n.intValue() : def.min();
            int current = def.metric().applyAsInt(now);
            Map<String, Object> row = new LinkedHashMap<>(m);
            row.put("current", Math.min(current, target));
            row.put("done", current >= target);
            list.add(row);
        }

        return list;

    }

    private static Map<String, Object> mission(Mission def, int target, String why) {

        int t = Math.max(def.min(), Math.min(def.max(), target));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", def.key());
        m.put("icon", def.icon());
        m.put("title", def.title(t));
        m.put("target", t);
        m.put("why", why);
        m.put("link", def.link());
        return m;

    }

    static Mission find(String key) {

        return CATALOG.stream().filter(c -> c.key().equals(key)).findFirst().orElse(null);

    }

    private static String text(Object value, int max, String fallback) {

        String s = value == null ? "" : String.valueOf(value).strip();
        if (s.isEmpty()) return fallback;
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;

    }

    private static String describe(RangeStats s) {

        return "출석 " + s.attendDays() + "일, 예측 " + s.predictVotes() + "개(채점 " + s.predictGraded() + "개 중 " + s.predictHits() + "개 적중), "
                + "뉴스 퀴즈 " + s.quizPlays() + "일(정답 " + s.quizScore() + "개), 모의투자 거래 " + s.trades() + "번, "
                + "토론 글 " + s.boardPosts() + "개·댓글 " + s.comments() + "개, AI 비서 질문 " + s.aiChats() + "번, 성분 분석 " + s.beautyLogs() + "번";

    }

    private static String catalogText() {

        StringBuilder sb = new StringBuilder();
        for (Mission m : CATALOG) {
            sb.append(m.key()).append("(").append(m.format().replace("%d", "N")).append(", 목표 ")
                    .append(m.min()).append("~").append(m.max()).append(") ");
        }
        return sb.toString().strip();

    }
}
