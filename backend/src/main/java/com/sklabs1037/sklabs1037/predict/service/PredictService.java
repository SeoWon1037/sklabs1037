package com.sklabs1037.sklabs1037.predict.service;

import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import com.sklabs1037.sklabs1037.predict.dao.PredictDao;
import com.sklabs1037.sklabs1037.predict.vo.PredictVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * 🎯 오를까 내릴까 (매일 예측 게임)
 *
 * 라운드 = 거래일 D 하나
 *   - 문제: 시가총액 상위 30종목 중 날짜로 섞어 고른 5종목 (그날 누구에게나 같은 문제)
 *   - 질문: D 종가가 그 전 거래일 종가보다 오를까, 내릴까
 *   - 투표: D 09:00(장 시작) 전까지, 마감 전에는 바꿀 수 있음
 *   - 채점: 평일 21:10 (PredictScheduler → OpsJobs "PREDICT_GRADE"), 20:30 시세 수집이 끝난 뒤
 *           보합이면 점수 없음, 3일 지나도 종가가 없으면(휴장) 무효
 *   - 비교: 모멘텀 봇(5일 수익률 + → UP), 사람들 다수결
 *
 * 지금 투표할 라운드: 평일 09:00 전이면 오늘, 아니면 다음 평일
 */
@Slf4j
@Service
public class PredictService {

    private static final int CANDIDATES = 30;
    private static final int PER_ROUND = 5;
    private static final LocalTime DEADLINE = LocalTime.of(9, 0);
    private static final LocalTime GRADE_AFTER = LocalTime.of(20, 45);   // 오늘 문제는 이 시각 이후에만 채점 (진짜 종가)
    private static final int VOID_AFTER_DAYS = 3;
    private static final int MIN_GRADED_ALL = 10;
    private static final int MIN_GRADED_WEEK = 3;

    @Autowired
    private PredictDao predictDao;

    // 🔔 채점이 끝난 날마다 투표한 사람에게 결과 알림
    @Autowired
    private NotificationService notificationService;

    // ============================================
    // 날짜 규칙 (순수 계산, 테스트 가능)
    // ============================================
    public static LocalDate roundDate(ZonedDateTime now) {

        LocalDate day = now.toLocalDate();

        if (isWeekday(day) && now.toLocalTime().isBefore(DEADLINE)) return day;

        do {
            day = day.plusDays(1);
        } while (!isWeekday(day));

        return day;

    }

    // 이 날짜의 문제는 날짜로 섞어서 고름 → 서버를 다시 켜도, 누가 먼저 열어도 같은 5종목
    static List<PredictVO> pick(LocalDate day, List<PredictVO> candidates) {

        List<PredictVO> list = new ArrayList<>(candidates);
        Collections.shuffle(list, new Random(day.toEpochDay() * 31 + 7));

        return list.subList(0, Math.min(PER_ROUND, list.size()));

    }

    static String grade(BigDecimal base, BigDecimal result) {

        int c = result.compareTo(base);

        return c > 0 ? "UP" : c < 0 ? "DOWN" : "FLAT";

    }

    // 최근 기록 → 지금 연속 적중 수 (최신부터 틀릴 때까지)
    static int streak(List<PredictVO> recent) {

        int n = 0;
        for (PredictVO v : recent) {
            if (!Boolean.TRUE.equals(v.getCorrect())) break;
            n++;
        }

        return n;

    }

    private static boolean isWeekday(LocalDate d) {

        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;

    }

    // ============================================
    // 화면
    // ============================================
    public Map<String, Object> overview(Long userId) {

        ZonedDateTime now = MockMarketHours.now();
        LocalDate round = roundDate(now);

        ensureRound(round);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("round", Map.of(
                "date", round.toString(),
                "deadline", round.atTime(DEADLINE).atZone(MockMarketHours.KST).toInstant().toEpochMilli()));
        result.put("items", items(round, userId, false));

        // 지난 라운드 결과 (가장 최근 채점된 날)
        LocalDate last = predictDao.selectLastGradedDate();
        if (last != null) {
            result.put("last", Map.of("date", last.toString(), "items", items(last, userId, true)));
        }

        result.put("loggedIn", userId != null);
        if (userId != null) result.put("me", myStats(userId));

        result.put("leaderboard", Map.of(
                "week", leaderboard(MockMarketHours.weekStart(now.toLocalDate()), MIN_GRADED_WEEK),
                "all", leaderboard(null, MIN_GRADED_ALL)));
        result.put("minGradedAll", MIN_GRADED_ALL);
        result.put("bot", record(predictDao.selectBotRecord()));
        result.put("crowd", record(predictDao.selectCrowdRecord()));

        return result;

    }

    public void vote(Long userId, Long itemId, String pick) {

        if (!"UP".equals(pick) && !"DOWN".equals(pick)) throw new IllegalArgumentException("오를까, 내릴까 중에 골라주세요.");

        PredictVO item = itemId == null ? null : predictDao.selectItem(itemId);
        if (item == null) throw new IllegalArgumentException("없는 문제예요.");

        ZonedDateTime deadline = item.getTradeDate().atTime(DEADLINE).atZone(MockMarketHours.KST);
        if (!MockMarketHours.now().isBefore(deadline)) throw new IllegalArgumentException("이미 마감된 문제예요. (장 시작 09:00 마감)");

        PredictVO v = new PredictVO();
        v.setItemId(itemId);
        v.setUserId(userId);
        v.setPick(pick);
        predictDao.upsertVote(v);

    }

    // ============================================
    // 채점 (작업 PREDICT_GRADE)
    // ============================================
    public JobOutcome gradeAll() {

        ZonedDateTime now = MockMarketHours.now();
        LocalDate today = now.toLocalDate();
        LocalDate upTo = now.toLocalTime().isBefore(GRADE_AFTER) ? today.minusDays(1) : today;

        int graded = 0;
        int waiting = 0;
        int failed = 0;
        Set<LocalDate> gradedDates = new TreeSet<>();

        for (PredictVO item : predictDao.selectUngraded(upTo)) {

            try {

                PredictVO close = predictDao.selectClose(item);
                PredictVO prev = predictDao.selectPrevClose(item);

                PredictVO g = new PredictVO();
                g.setItemId(item.getItemId());

                if (close == null || prev == null) {

                    // 아직 시세가 없음 → 며칠 기다렸다가 무효 (휴장일)
                    if (item.getTradeDate().isAfter(today.minusDays(VOID_AFTER_DAYS))) {
                        waiting++;
                        continue;
                    }
                    g.setResult("VOID");

                } else {

                    g.setBaseDate(prev.getBaseDate());
                    g.setBaseClose(prev.getBaseClose());
                    g.setResultClose(close.getResultClose());
                    g.setResult(grade(prev.getBaseClose(), close.getResultClose()));

                }

                if (predictDao.gradeItem(g) > 0) {
                    predictDao.gradeVotes(g);
                    graded++;
                    gradedDates.add(item.getTradeDate());
                }

            } catch (Exception e) {

                failed++;
                log.warn("예측 채점 실패 (문제 {}): {}", item.getItemId(), e.getMessage());

            }

        }

        for (LocalDate d : gradedDates) notificationService.predictGraded(d);

        return JobOutcome.of(graded + failed, failed, "채점 " + graded + "문제" + (waiting > 0 ? " · 시세 기다림 " + waiting : ""));

    }

    // ============================================
    // 내부
    // ============================================
    private void ensureRound(LocalDate day) {

        PredictVO key = new PredictVO();
        key.setTradeDate(day);

        if (!predictDao.selectRound(key).isEmpty()) return;

        List<PredictVO> picked = pick(day, predictDao.selectCandidates(CANDIDATES));

        for (int i = 0; i < picked.size(); i++) {
            PredictVO p = picked.get(i);
            PredictVO item = new PredictVO();
            item.setTradeDate(day);
            item.setStockId(p.getStockId());
            item.setSeq(i + 1);
            item.setBotPick(p.getRet5() != null && p.getRet5().signum() > 0 ? "UP" : "DOWN");
            predictDao.insertItem(item);   // 동시에 열어도 UNIQUE 때문에 한 번만
        }

    }

    private List<Map<String, Object>> items(LocalDate day, Long userId, boolean graded) {

        PredictVO key = new PredictVO();
        key.setTradeDate(day);
        key.setUserId(userId);

        List<Map<String, Object>> list = new ArrayList<>();

        for (PredictVO i : predictDao.selectRound(key)) {

            int up = i.getUpCount() == null ? 0 : i.getUpCount();
            int down = i.getDownCount() == null ? 0 : i.getDownCount();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", i.getItemId());
            m.put("symbol", i.getSymbol());
            m.put("name", i.getName());
            m.put("lastClose", i.getLastClose());
            m.put("ret5", i.getRet5());
            m.put("myPick", i.getPick());
            // 투표 전에는 다른 사람 선택을 안 보여줌 (따라 찍기 방지)
            boolean showCrowd = graded || i.getPick() != null;
            m.put("up", showCrowd ? up : null);
            m.put("down", showCrowd ? down : null);
            m.put("votes", up + down);

            if (graded) {
                m.put("result", i.getResult());
                m.put("baseClose", i.getBaseClose());
                m.put("resultClose", i.getResultClose());
                m.put("changeRate", rate(i.getBaseClose(), i.getResultClose()));
                m.put("correct", i.getCorrect());
                m.put("botPick", i.getBotPick());
            }

            list.add(m);

        }

        return list;

    }

    private Map<String, Object> myStats(Long userId) {

        PredictVO key = new PredictVO();
        key.setUserId(userId);
        key.setLimit(200);

        List<PredictVO> recent = predictDao.selectMyGraded(key);
        long hits = recent.stream().filter(v -> Boolean.TRUE.equals(v.getCorrect())).count();

        int best = 0;
        int run = 0;
        for (int i = recent.size() - 1; i >= 0; i--) {
            run = Boolean.TRUE.equals(recent.get(i).getCorrect()) ? run + 1 : 0;
            best = Math.max(best, run);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("graded", recent.size());
        m.put("hits", hits);
        m.put("accuracy", recent.isEmpty() ? null : pct(hits, recent.size()));
        m.put("streak", streak(recent));
        m.put("bestStreak", best);

        return m;

    }

    private List<Map<String, Object>> leaderboard(LocalDate from, int minGraded) {

        PredictVO key = new PredictVO();
        key.setFromDate(from);
        key.setMinGraded(minGraded);
        key.setLimit(10);

        List<Map<String, Object>> list = new ArrayList<>();
        int rank = 0;

        for (PredictVO r : predictDao.selectLeaderboard(key)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", ++rank);
            m.put("nickname", r.getNickname());
            m.put("hits", r.getHits());
            m.put("graded", r.getGraded());
            m.put("accuracy", pct(r.getHits(), r.getGraded()));
            list.add(m);
        }

        return list;

    }

    private static Map<String, Object> record(PredictVO r) {

        int graded = r == null || r.getGraded() == null ? 0 : r.getGraded();
        int hits = r == null || r.getHits() == null ? 0 : r.getHits();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("graded", graded);
        m.put("hits", hits);
        m.put("accuracy", graded == 0 ? null : pct(hits, graded));

        return m;

    }

    private static BigDecimal pct(long part, long total) {

        return total == 0 ? null : BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP);

    }

    private static BigDecimal rate(BigDecimal base, BigDecimal now) {

        if (base == null || now == null || base.signum() == 0) return null;

        return now.subtract(base).multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);

    }
}
