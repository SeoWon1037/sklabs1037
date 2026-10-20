package com.sklabs1037.sklabs1037.me.service;

import com.sklabs1037.sklabs1037.league.service.AiLeagueService;
import com.sklabs1037.sklabs1037.me.dao.MeDao;
import com.sklabs1037.sklabs1037.me.service.BadgeService.Badge;
import com.sklabs1037.sklabs1037.me.service.BadgeService.Stats;
import com.sklabs1037.sklabs1037.mock.service.IMockTradeService;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import com.sklabs1037.sklabs1037.predict.dao.PredictDao;
import com.sklabs1037.sklabs1037.predict.service.PredictService;
import com.sklabs1037.sklabs1037.predict.vo.PredictVO;
import com.sklabs1037.sklabs1037.quiz.dao.NewsQuizDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 🏅 내 활동 / 📅 오늘의 SKLabs
 *
 * activity(): 여러 기능에 흩어진 내 기록을 한 화면으로
 *   모의투자(거래 수·수익률) · 예측(적중률·최고 연속) · 퀴즈(참여·만점·최고 연속) · 토론(글·좋아요·댓글)
 *   · 탐험(투자 성향·AI 대화·성분 분석·관심 종목·알림) · 출석 → 배지 판정(BadgeService)
 *
 * today(): 로그인하고 메인을 열면 출석 도장 + 오늘 할 일 체크리스트
 *   🎯 예측 (이번 라운드 몇 문제 골랐나) · 📰 퀴즈 (오늘 문제 풀었나) · 💬 토론 한 줄 · 🤖 AI 매매 일지 보기(화면에서 기억)
 */
@Slf4j
@Service
public class ActivityService {

    @Autowired
    private MeDao meDao;

    @Autowired
    private BadgeService badgeService;

    @Autowired
    private PredictDao predictDao;

    @Autowired
    private NewsQuizDao newsQuizDao;

    @Autowired
    private IMockTradeService mockTradeService;

    @Autowired
    private AiLeagueService aiLeagueService;

    // ============================================
    // 내 활동
    // ============================================
    @SuppressWarnings("unchecked")
    public Map<String, Object> activity(Long userId) {

        Map<String, Object> a = meDao.selectActivity(userId);

        // 모의투자 수익률 (거래한 적 있을 때만 → 계좌를 괜히 만들지 않게)
        int tradeCount = num(a.get("tradeCount"));
        Double returnRate = null;
        if (tradeCount > 0) {
            Map<String, Object> summary = (Map<String, Object>) mockTradeService.getAccount(userId).get("account");
            returnRate = summary.get("returnRate") == null ? null : ((Number) summary.get("returnRate")).doubleValue();
        }

        Double bestAi = bestAiReturn();
        boolean beatAi = returnRate != null && bestAi != null && returnRate > bestAi;

        // 예측: 채점된 기록 (최신순)
        PredictVO key = new PredictVO();
        key.setUserId(userId);
        key.setLimit(500);
        List<Boolean> predictResults = predictDao.selectMyGraded(key).stream().map(PredictVO::getCorrect).toList();
        int predictHits = (int) predictResults.stream().filter(Boolean.TRUE::equals).count();

        List<LocalDate> quizDates = newsQuizDao.selectMyDates(userId);
        List<LocalDate> attendDays = meDao.selectAttendDays(userId);

        Stats s = new Stats(
                tradeCount, returnRate, beatAi,
                num(a.get("predictVotes")), predictResults.size(), predictHits, bestRun(predictResults),
                num(a.get("quizPlays")), num(a.get("quizPerfect")), longestDays(quizDates),
                num(a.get("boardPosts")), num(a.get("likesReceived")), num(a.get("comments")),
                num(a.get("aiChats")), num(a.get("beautyLogs")), num(a.get("watchCount")), num(a.get("alertCount")),
                a.get("personaType") != null, attendDays.size(), longestDays(attendDays));

        List<String> fresh = badgeService.award(userId, s);

        // 배지 목록 (받은 것 + 아직 못 받은 것)
        Map<String, Object> earnedAt = new HashMap<>();
        for (Map<String, Object> row : meDao.selectBadges(userId)) earnedAt.put(String.valueOf(row.get("code")), row.get("earnedAt"));

        List<Map<String, Object>> badges = new ArrayList<>();
        for (Badge b : BadgeService.BADGES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", b.code());
            m.put("icon", b.icon());
            m.put("name", b.name());
            m.put("desc", b.desc());
            m.put("group", b.group());
            m.put("earnedAt", earnedAt.get(b.code()));
            badges.add(m);
        }

        Map<String, Object> mock = new LinkedHashMap<>();
        mock.put("tradeCount", tradeCount);
        mock.put("returnRate", returnRate);
        mock.put("bestAiReturn", bestAi);

        Map<String, Object> predict = new LinkedHashMap<>();
        predict.put("votes", s.predictVotes());
        predict.put("graded", s.predictGraded());
        predict.put("hits", s.predictHits());
        predict.put("bestStreak", s.predictBestStreak());

        Map<String, Object> quiz = new LinkedHashMap<>();
        quiz.put("plays", s.quizPlays());
        quiz.put("perfect", s.quizPerfect());
        quiz.put("totalScore", num(a.get("quizScore")));
        quiz.put("bestStreak", s.quizBestStreak());

        Map<String, Object> board = new LinkedHashMap<>();
        board.put("posts", s.boardPosts());
        board.put("likesReceived", s.likesReceived());
        board.put("comments", s.comments());

        Map<String, Object> explore = new LinkedHashMap<>();
        explore.put("personaType", a.get("personaType"));
        explore.put("aiChats", s.aiChats());
        explore.put("beautyLogs", s.beautyLogs());
        explore.put("watchCount", s.watchCount());
        explore.put("alertCount", s.alertCount());

        Map<String, Object> attend = new LinkedHashMap<>();
        attend.put("days", s.attendDays());
        attend.put("streak", currentStreak(attendDays, MockMarketHours.now().toLocalDate()));
        attend.put("bestStreak", s.attendBestStreak());
        attend.put("recent", attendDays.stream().limit(35).map(LocalDate::toString).toList());   // 최근 5주 달력

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mock", mock);
        result.put("predict", predict);
        result.put("quiz", quiz);
        result.put("board", board);
        result.put("explore", explore);
        result.put("attend", attend);
        result.put("badges", badges);
        result.put("earnedCount", earnedAt.size());
        result.put("newBadges", fresh);

        return result;

    }

    // ============================================
    // 오늘의 SKLabs (메인 카드)
    // ============================================
    public Map<String, Object> today(Long userId) {

        LocalDate today = MockMarketHours.now().toLocalDate();

        // 출석 도장 (하루 한 번)
        boolean firstVisit = meDao.insertAttendance(userId, today) > 0;
        List<LocalDate> attendDays = meDao.selectAttendDays(userId);

        LocalDate round = PredictService.roundDate(MockMarketHours.now());
        int items = meDao.countPredictItems(round);
        int votes = meDao.countPredictVotes(userId, round);

        boolean quizToday = newsQuizDao.countQuiz(today) > 0;
        boolean quizDone = quizToday && newsQuizDao.selectPlay(today, userId) != null;

        boolean posted = meDao.countBoardPostsToday(userId, today) > 0;

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(task("predict", "🎯", "오를까 내릴까 예측", items == 0 ? "문제 준비 중" : votes + " / " + items + " 골랐어요",
                items > 0 && votes >= items, "/predict"));
        tasks.add(task("quiz", "📰", "오늘의 뉴스 퀴즈", !quizToday ? "아침 7시 40분에 나와요" : quizDone ? "오늘 퀴즈 완료" : "5문제 풀기",
                quizDone, "/quiz"));
        tasks.add(task("league", "🤖", "AI 매매 일지 보기", "AI는 오늘 뭘 샀을까?", false, "/league"));   // 완료 여부는 화면이 기억
        tasks.add(task("board", "💬", "토론에 한 줄", posted ? "오늘 글을 남겼어요" : "관심 종목에 의견 남기기", posted, "/stock?tab=board"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", today.toString());
        result.put("firstVisit", firstVisit);
        result.put("attendStreak", currentStreak(attendDays, today));
        result.put("attendDays", attendDays.size());
        result.put("tasks", tasks);

        return result;

    }

    // ============================================
    // 계산 (순수, 테스트 가능)
    // ============================================

    // 최신순 true/false 목록에서 가장 긴 연속 true
    static int bestRun(List<Boolean> results) {

        int best = 0;
        int run = 0;
        for (Boolean b : results) {
            run = Boolean.TRUE.equals(b) ? run + 1 : 0;
            best = Math.max(best, run);
        }

        return best;

    }

    // 날짜 목록(순서 상관없음)에서 가장 긴 연속 일수
    static int longestDays(List<LocalDate> days) {

        TreeSet<LocalDate> set = new TreeSet<>(days);
        int best = 0;
        int run = 0;
        LocalDate prev = null;

        for (LocalDate d : set) {
            run = (prev != null && prev.plusDays(1).equals(d)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = d;
        }

        return best;

    }

    // 오늘(또는 어제)까지 이어진 연속 일수
    static int currentStreak(List<LocalDate> days, LocalDate today) {

        Set<LocalDate> set = new HashSet<>(days);
        LocalDate d = set.contains(today) ? today : today.minusDays(1);
        int n = 0;

        while (set.contains(d)) {
            n++;
            d = d.minusDays(1);
        }

        return n;

    }

    // ============================================
    // 내부
    // ============================================
    @SuppressWarnings("unchecked")
    private Double bestAiReturn() {

        try {
            List<Map<String, Object>> traders = (List<Map<String, Object>>) aiLeagueService.getLeague().get("traders");
            return traders.stream()
                    .filter(t -> t.get("rank") != null && t.get("returnRate") != null)   // 매매를 시작한 AI만
                    .map(t -> ((Number) t.get("returnRate")).doubleValue())
                    .max(Double::compare).orElse(null);
        } catch (Exception e) {
            log.debug("AI 리그 성적 조회 실패: {}", e.getMessage());
            return null;
        }

    }

    private static Map<String, Object> task(String key, String icon, String title, String sub, boolean done, String link) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("icon", icon);
        m.put("title", title);
        m.put("sub", sub);
        m.put("done", done);
        m.put("link", link);

        return m;

    }

    private static int num(Object o) {

        return o instanceof Number n ? n.intValue() : 0;

    }
}
