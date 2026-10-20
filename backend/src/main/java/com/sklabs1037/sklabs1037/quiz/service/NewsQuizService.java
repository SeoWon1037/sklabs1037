package com.sklabs1037.sklabs1037.quiz.service;

import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import com.sklabs1037.sklabs1037.quiz.dao.NewsQuizDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.*;

/**
 * 📰 오늘의 뉴스 퀴즈
 *
 * 출제 (매일 07:40, NewsQuizScheduler → OpsJobs "NEWS_QUIZ")
 *   1. 최근 30시간 기사 제목 최대 40개 (스포츠 제외, 같은 제목 한 번) + 번호
 *   2. Claude가 make_quiz 도구로 5문제: 질문·보기 4개·정답·해설·근거 기사 번호
 *      - 제목에 나온 사실만으로 풀 수 있게, 예측·투자 판단 문제 금지
 *   3. 서버 확인 (clean): 보기 4개가 서로 다른지, 정답 번호, 근거 기사가 실제 목록에 있는지 → 맞지 않는 문제는 버림
 *      보기 순서는 날짜로 섞음 (AI가 정답을 첫 번째에 두는 버릇 방지)
 *
 * 풀기
 *   - 문제를 줄 때는 정답·해설을 빼고, 제출하면 서버가 채점 (화면에서 정답을 미리 볼 수 없게)
 *   - 로그인하면 하루 한 번 기록 → 이번 주 랭킹, 연속 참여 / 로그인 안 하면 채점만
 */
@Slf4j
@Service
public class NewsQuizService {

    static final int QUESTIONS = 5;
    private static final int NEWS_HOURS = 30;
    private static final int NEWS_LIMIT = 40;
    private static final int MIN_NEWS = 8;

    private static final String SYSTEM = """
            너는 경제 뉴스 퀴즈 출제자다. 주어진 기사 제목 목록만 보고 오늘의 뉴스 퀴즈 5문제를 낸다.

            규칙
            - 제목에 실제로 나온 사실(누가·무엇을·얼마나)만으로 풀 수 있는 문제만 낸다. 제목에 없는 숫자·사실을 지어내지 않는다.
            - 주가 예측, "사야 할까" 같은 투자 판단 문제는 내지 않는다.
            - 보기는 4개, 서로 다르고 그럴듯하게. 정답은 하나.
            - 해설은 1~2문장, 근거가 된 기사를 sources에 번호로 적는다.
            - 5문제가 서로 다른 기사를 다루게 하고, 너무 쉬운 문제(질문에 답이 그대로 있는 문제)는 피한다.
            - 반드시 make_quiz 도구로만 답한다.
            """;

    private static final Map<String, Object> TOOL = Map.of(
            "name", "make_quiz",
            "description", "오늘의 뉴스 퀴즈 5문제",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of("questions", Map.of(
                            "type", "array", "minItems", 5, "maxItems", 5,
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "question", Map.of("type", "string"),
                                            "options", Map.of("type", "array", "minItems", 4, "maxItems", 4, "items", Map.of("type", "string")),
                                            "answer", Map.of("type", "integer", "minimum", 0, "maximum", 3, "description", "정답 보기 번호 (0부터)"),
                                            "explanation", Map.of("type", "string"),
                                            "sources", Map.of("type", "array", "items", Map.of("type", "integer"), "description", "근거 기사 번호")),
                                    "required", List.of("question", "options", "answer", "explanation", "sources")))),
                    "required", List.of("questions")));

    @Autowired
    private NewsQuizDao newsQuizDao;

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private JsonMapper jsonMapper;

    // ============================================
    // 출제
    // ============================================
    @SuppressWarnings("unchecked")
    public JobOutcome generateToday() {

        LocalDate today = MockMarketHours.now().toLocalDate();

        if (newsQuizDao.countQuiz(today) > 0) return JobOutcome.of(0, 0, "오늘 퀴즈는 이미 있어요");
        if (!claudeApiClient.isConfigured()) throw new IllegalStateException("AI 키가 설정되지 않았습니다.");

        List<Map<String, Object>> news = newsQuizDao.selectRecentNews(NEWS_HOURS, NEWS_LIMIT);
        if (news.size() < MIN_NEWS) throw new IllegalStateException("최근 기사가 " + news.size() + "개뿐이라 출제하지 않았어요.");

        StringBuilder sb = new StringBuilder("## 최근 기사 제목\n");
        for (int i = 0; i < news.size(); i++) {
            Map<String, Object> n = news.get(i);
            sb.append(i + 1).append(". ").append(n.get("title"))
                    .append(n.get("stockName") != null ? " [" + n.get("stockName") + "]" : "")
                    .append(" (").append(n.get("press")).append(", ").append(n.get("pubAt")).append(")\n");
        }
        sb.append("\n").append(today).append(" 오늘의 뉴스 퀴즈 5문제를 내줘.");

        Map<String, Object> response = claudeApiClient.createMessage(SYSTEM, List.of(TOOL),
                List.of(Map.of("role", "user", "content", sb.toString())), Map.of("type", "tool", "name", "make_quiz"), 3000);

        Object raw = null;
        if (response != null && response.get("content") instanceof List<?> blocks) {
            for (Object b : blocks) {
                if (b instanceof Map<?, ?> m && "tool_use".equals(m.get("type")) && m.get("input") instanceof Map<?, ?> input) {
                    raw = input.get("questions");
                }
            }
        }

        List<Map<String, Object>> questions = clean(raw instanceof List<?> l ? (List<Object>) l : List.of(), news, today);

        if (questions.size() < 3) throw new IllegalStateException("쓸 수 있는 문제가 " + questions.size() + "개뿐이라 저장하지 않았어요.");

        newsQuizDao.insertQuiz(today, jsonMapper.writeValueAsString(questions), news.size());

        return JobOutcome.of(questions.size(), QUESTIONS - questions.size(), "오늘 퀴즈 " + questions.size() + "문제 (기사 " + news.size() + "개)");

    }

    // AI 문제 확인·정리 (순수 계산, 테스트 가능)
    static List<Map<String, Object>> clean(List<Object> raw, List<Map<String, Object>> news, LocalDate day) {

        List<Map<String, Object>> out = new ArrayList<>();
        Random random = new Random(day.toEpochDay());

        for (Object o : raw) {

            if (!(o instanceof Map<?, ?> q) || out.size() >= QUESTIONS) continue;

            String question = str(q.get("question")).trim();
            if (question.isEmpty() || question.length() > 200) continue;

            if (!(q.get("options") instanceof List<?> opts) || opts.size() != 4) continue;
            List<String> options = opts.stream().map(x -> str(x).trim()).toList();
            if (options.stream().anyMatch(x -> x.isEmpty() || x.length() > 80) || new HashSet<>(options).size() != 4) continue;

            int answer = q.get("answer") instanceof Number n ? n.intValue() : -1;
            if (answer < 0 || answer > 3) continue;

            // 근거 기사: 목록에 있는 번호만 (1부터)
            List<Map<String, Object>> sources = new ArrayList<>();
            if (q.get("sources") instanceof List<?> src) {
                for (Object s : src) {
                    int idx = s instanceof Number n ? n.intValue() - 1 : -1;
                    if (idx < 0 || idx >= news.size()) continue;
                    Map<String, Object> n = news.get(idx);
                    sources.add(Map.of("title", str(n.get("title")), "link", str(n.get("link")), "press", str(n.get("press"))));
                    if (sources.size() >= 2) break;
                }
            }
            if (sources.isEmpty()) continue;

            // 보기 섞기 (정답 위치 따라가기)
            List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(order, random);

            List<String> shuffled = new ArrayList<>();
            int newAnswer = 0;
            for (int i = 0; i < 4; i++) {
                shuffled.add(options.get(order.get(i)));
                if (order.get(i) == answer) newAnswer = i;
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("question", question);
            m.put("options", shuffled);
            m.put("answer", newAnswer);
            m.put("explanation", cut(str(q.get("explanation")).trim(), 300));
            m.put("sources", sources);
            out.add(m);

        }

        return out;

    }

    // ============================================
    // 풀기
    // ============================================
    public Map<String, Object> today(Long userId) {

        LocalDate today = MockMarketHours.now().toLocalDate();
        Map<String, Object> quiz = newsQuizDao.selectLatestQuiz(today);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loggedIn", userId != null);
        result.put("ranking", newsQuizDao.selectWeekRanking(MockMarketHours.weekStart(today), 10));

        if (quiz == null) {
            result.put("quiz", null);
            return result;
        }

        LocalDate date = toDate(quiz.get("quizDate"));
        List<Map<String, Object>> questions = parse(str(quiz.get("questionsJson")));

        // 정답·해설 빼고 문제만
        List<Map<String, Object>> visible = new ArrayList<>();
        for (Map<String, Object> q : questions) visible.add(Map.of("question", q.get("question"), "options", q.get("options")));

        result.put("quiz", Map.of("date", date.toString(), "today", date.equals(today), "questions", visible));
        result.put("stats", stats(date));

        if (userId != null) {
            Map<String, Object> play = newsQuizDao.selectPlay(date, userId);
            // 이미 풀었으면 결과(정답·해설)까지
            if (play != null) result.put("myResult", grade(questions, parseInts(str(play.get("answersJson")))));
            result.put("streak", streak(newsQuizDao.selectMyDates(userId), date));
        }

        return result;

    }

    public Map<String, Object> submit(Long userId, List<Integer> answers) {

        LocalDate today = MockMarketHours.now().toLocalDate();
        Map<String, Object> quiz = newsQuizDao.selectLatestQuiz(today);
        if (quiz == null) throw new IllegalArgumentException("아직 오늘의 퀴즈가 없어요.");

        LocalDate date = toDate(quiz.get("quizDate"));
        List<Map<String, Object>> questions = parse(str(quiz.get("questionsJson")));

        if (answers == null || answers.size() != questions.size()) throw new IllegalArgumentException("모든 문제에 답해주세요.");

        // 이미 풀었으면 처음 답으로 (다시 풀어 점수 올리기 방지)
        if (userId != null) {
            Map<String, Object> play = newsQuizDao.selectPlay(date, userId);
            if (play != null) answers = parseInts(str(play.get("answersJson")));
        }

        Map<String, Object> result = grade(questions, answers);

        if (userId != null) {
            newsQuizDao.insertPlay(date, userId, (int) result.get("score"), jsonMapper.writeValueAsString(answers));
            result.put("streak", streak(newsQuizDao.selectMyDates(userId), date));
        }

        result.put("stats", stats(date));

        return result;

    }

    // 채점 (순수 계산)
    static Map<String, Object> grade(List<Map<String, Object>> questions, List<Integer> answers) {

        int score = 0;
        List<Map<String, Object>> items = new ArrayList<>();

        for (int i = 0; i < questions.size(); i++) {

            Map<String, Object> q = questions.get(i);
            int correct = ((Number) q.get("answer")).intValue();
            Integer mine = i < answers.size() ? answers.get(i) : null;
            boolean ok = mine != null && mine == correct;
            if (ok) score++;

            Map<String, Object> m = new LinkedHashMap<>(q);
            m.put("mine", mine);
            m.put("correct", ok);
            items.add(m);

        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("score", score);
        r.put("total", questions.size());
        r.put("items", items);

        return r;

    }

    // 연속 참여 (퀴즈 날짜 기준으로 하루도 안 빼고)
    static int streak(List<LocalDate> playedDesc, LocalDate latestQuiz) {

        int n = 0;
        LocalDate expect = latestQuiz;

        for (LocalDate d : playedDesc) {
            if (d.equals(expect)) {
                n++;
                expect = expect.minusDays(1);
            } else if (d.isBefore(expect)) {
                // 오늘 아직 안 풀었으면 어제부터 이어서 셈
                if (n == 0 && d.equals(latestQuiz.minusDays(1))) {
                    n = 1;
                    expect = d.minusDays(1);
                } else {
                    break;
                }
            }
        }

        return n;

    }

    private Map<String, Object> stats(LocalDate date) {

        Map<String, Object> day = newsQuizDao.selectDayStats(date);
        int[] counts = new int[QUESTIONS + 1];
        for (Map<String, Object> row : newsQuizDao.selectScoreCounts(date)) {
            int s = ((Number) row.get("score")).intValue();
            if (s >= 0 && s <= QUESTIONS) counts[s] = ((Number) row.get("count")).intValue();
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("players", day == null ? 0 : day.get("players"));
        m.put("avgScore", day == null ? null : day.get("avgScore"));
        m.put("scoreCounts", Arrays.stream(counts).boxed().toList());

        return m;

    }

    // ============================================
    // 작은 도구들
    // ============================================
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parse(String json) {

        try {
            return jsonMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }

    }

    @SuppressWarnings("unchecked")
    private List<Integer> parseInts(String json) {

        try {
            List<Object> l = jsonMapper.readValue(json, List.class);
            return l.stream().map(o -> o instanceof Number n ? n.intValue() : null).toList();
        } catch (Exception e) {
            return List.of();
        }

    }

    private static LocalDate toDate(Object o) {

        if (o instanceof LocalDate d) return d;
        if (o instanceof java.sql.Date d) return d.toLocalDate();

        return LocalDate.parse(String.valueOf(o).substring(0, 10));

    }

    private static String str(Object o) {

        return o == null ? "" : String.valueOf(o);

    }

    private static String cut(String s, int max) {

        return s.length() <= max ? s : s.substring(0, max);

    }
}
