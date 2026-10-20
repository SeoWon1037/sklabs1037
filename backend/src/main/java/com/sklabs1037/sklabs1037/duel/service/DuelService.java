package com.sklabs1037.sklabs1037.duel.service;

import com.sklabs1037.sklabs1037.duel.dao.DuelDao;
import com.sklabs1037.sklabs1037.me.dao.MeDao;
import com.sklabs1037.sklabs1037.me.service.BadgeService;
import com.sklabs1037.sklabs1037.me.service.Codes;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.me.service.RangeStats;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * ⚔️ 친구와 1:1 대결
 *
 * 흐름
 *   1. 만들기 → 초대 링크 /duel/코드 (3일 안에 아무도 안 받으면 만료)
 *   2. 친구가 링크를 열고 "대결 받기" → 그날부터 7일
 *   3. 점수 = 🎯 예측 적중 ×10 + 📰 퀴즈 정답 ×5 + 📅 출석 하루 ×3 (기간 안의 실제 기록, 화면에서 실시간)
 *   4. 끝난 다음 날 00:30 (DUEL_SETTLE) 최종 점수 저장 · 승자 ⚔️ 배지 · 둘 다 🔔 알림
 *      (마지막 날 예측은 21:10에 채점되니 다음 날 정산)
 *   - 한 사람이 동시에 3개까지 (기다리는 중 + 진행 중)
 */
@Slf4j
@Service
public class DuelService {

    static final int DAYS = 7;
    static final int MAX_OPEN = 3;
    static final int HIT_POINT = 10;
    static final int QUIZ_POINT = 5;
    static final int ATTEND_POINT = 3;

    @Autowired
    private DuelDao duelDao;

    @Autowired
    private MeDao meDao;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private BadgeService badgeService;

    // ============================================
    // 만들기 / 받기 / 취소
    // ============================================
    public Map<String, Object> create(Long userId) {

        if (duelDao.countOpen(userId) >= MAX_OPEN) throw new IllegalArgumentException("대결은 동시에 " + MAX_OPEN + "개까지 할 수 있어요.");

        String code = Codes.random(10);
        duelDao.insertDuel(code, userId);

        return Map.of("code", code);

    }

    public Map<String, Object> accept(String code, Long userId) {

        Map<String, Object> duel = duelDao.selectByCode(code);
        if (duel == null) throw new IllegalArgumentException("대결을 찾을 수 없어요.");
        if (userId.equals(num(duel.get("hostId")))) throw new IllegalArgumentException("내가 만든 대결은 친구가 받아야 시작돼요.");
        if (!"WAITING".equals(duel.get("status"))) throw new IllegalArgumentException("이미 시작했거나 끝난 대결이에요.");
        if (duelDao.countOpen(userId) >= MAX_OPEN) throw new IllegalArgumentException("대결은 동시에 " + MAX_OPEN + "개까지 할 수 있어요.");

        LocalDate today = MockMarketHours.now().toLocalDate();
        if (duelDao.accept(code, userId, today, today.plusDays(DAYS - 1)) == 0) throw new IllegalArgumentException("초대 기간(3일)이 지났거나 이미 다른 사람이 받았어요.");

        String guest = Objects.toString(meDao.selectNickname(userId), "친구");
        notificationService.notify(num(duel.get("hostId")), "DUEL", "⚔️ " + guest + "님이 대결을 받았어요",
                "오늘부터 7일 승부! 예측·퀴즈·출석으로 점수를 모아요", "/duel/" + code, "DUEL_START:" + code);

        return view(code, userId);

    }

    public void cancel(String code, Long userId) {

        if (duelDao.cancel(code, userId) == 0) throw new IllegalArgumentException("취소할 수 없는 대결이에요.");

    }

    // ============================================
    // 보기
    // ============================================
    public Map<String, Object> view(String code, Long viewerId) {

        Map<String, Object> duel = duelDao.selectByCode(code);
        if (duel == null) return null;

        LocalDate today = MockMarketHours.now().toLocalDate();
        Map<String, Object> out = new LinkedHashMap<>(duel);
        String status = String.valueOf(duel.get("status"));

        // 진행 중·끝난 대결은 기간 안의 실제 기록으로 점수 (끝난 대결은 저장된 최종 점수가 기준)
        if (duel.get("startDate") != null) {
            LocalDate start = LocalDate.parse(String.valueOf(duel.get("startDate")));
            LocalDate end = LocalDate.parse(String.valueOf(duel.get("endDate")));
            LocalDate to = end.isBefore(today) ? end : today;
            out.put("host", breakdown(stats(num(duel.get("hostId")), start, to)));
            out.put("guest", breakdown(stats(num(duel.get("guestId")), start, to)));
            out.put("daysLeft", "ACTIVE".equals(status) ? Math.max(0, ChronoUnit.DAYS.between(today, end) + 1) : 0);
        }

        Long hostId = num(duel.get("hostId"));
        Long guestId = num(duel.get("guestId"));
        String role = viewerId == null ? "VIEWER" : viewerId.equals(hostId) ? "HOST" : viewerId.equals(guestId) ? "GUEST" : "VIEWER";

        out.put("role", role);
        out.put("loggedIn", viewerId != null);
        out.put("canAccept", "WAITING".equals(status) && viewerId != null && !viewerId.equals(hostId));
        out.put("rules", Map.of("hit", HIT_POINT, "quiz", QUIZ_POINT, "attend", ATTEND_POINT, "days", DAYS));

        // 다른 사람에게 보여줄 필요 없는 내부 번호는 빼기
        out.remove("id");
        out.remove("hostId");
        out.remove("guestId");
        Object winner = duel.get("winnerId");
        out.remove("winnerId");
        out.put("winner", winner == null ? null : num(winner).equals(hostId) ? "HOST" : "GUEST");

        return out;

    }

    public List<Map<String, Object>> mine(Long userId) {

        List<Map<String, Object>> list = new ArrayList<>();

        for (Map<String, Object> d : duelDao.selectMine(userId, 20)) {
            Long hostId = num(d.get("hostId"));
            boolean host = userId.equals(hostId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", d.get("code"));
            row.put("status", d.get("status"));
            row.put("role", host ? "HOST" : "GUEST");
            row.put("opponent", host ? d.get("guestName") : d.get("hostName"));
            row.put("startDate", d.get("startDate"));
            row.put("endDate", d.get("endDate"));
            row.put("myScore", host ? d.get("hostScore") : d.get("guestScore"));
            row.put("opponentScore", host ? d.get("guestScore") : d.get("hostScore"));
            Object w = d.get("winnerId");
            row.put("result", !"DONE".equals(d.get("status")) ? null : w == null ? "DRAW" : userId.equals(num(w)) ? "WIN" : "LOSE");
            row.put("insAt", d.get("insAt"));
            list.add(row);
        }

        return list;

    }

    // ============================================
    // 정산 (DUEL_SETTLE, 매일 00:30)
    // ============================================
    public JobOutcome settleAll() {

        int expired = duelDao.expireWaiting();
        LocalDate today = MockMarketHours.now().toLocalDate();
        List<Map<String, Object>> due = duelDao.selectToSettle(today);
        int done = 0;
        int failed = 0;

        for (Map<String, Object> d : due) {

            try {

                Long hostId = num(d.get("hostId"));
                Long guestId = num(d.get("guestId"));
                LocalDate start = LocalDate.parse(String.valueOf(d.get("startDate")));
                LocalDate end = LocalDate.parse(String.valueOf(d.get("endDate")));

                int hostScore = score(stats(hostId, start, end));
                int guestScore = score(stats(guestId, start, end));
                Long winner = winner(hostId, hostScore, guestId, guestScore);

                if (duelDao.settle(num(d.get("id")), hostScore, guestScore, winner) == 0) continue;
                done++;

                String code = String.valueOf(d.get("code"));
                notifyResult(hostId, winner, hostScore, guestScore, String.valueOf(d.get("guestName")), code);
                notifyResult(guestId, winner, guestScore, hostScore, String.valueOf(d.get("hostName")), code);
                if (winner != null) badgeService.awardOne(winner, "DUEL_WIN");

            } catch (Exception e) {

                failed++;
                log.warn("대결 정산 실패 code={} {}", d.get("code"), e.getMessage());

            }

        }

        return JobOutcome.of(due.size(), failed, "정산 " + done + "개 / 만료 " + expired + "개");

    }

    private void notifyResult(Long userId, Long winner, int mine, int other, String opponent, String code) {

        String title = winner == null ? "🤝 대결 결과: 비겼어요" : winner.equals(userId) ? "🏆 대결 결과: 이겼어요!" : "⚔️ 대결 결과: 아쉽게 졌어요";
        notificationService.notify(userId, "DUEL", title, opponent + "님과 " + mine + " : " + other, "/duel/" + code, "DUEL_END:" + code);

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================
    static int score(RangeStats s) {

        return s.predictHits() * HIT_POINT + s.quizScore() * QUIZ_POINT + s.attendDays() * ATTEND_POINT;

    }

    static Long winner(Long hostId, int hostScore, Long guestId, int guestScore) {

        if (hostScore == guestScore) return null;
        return hostScore > guestScore ? hostId : guestId;

    }

    static Map<String, Object> breakdown(RangeStats s) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("predictHits", s.predictHits());
        m.put("quizScore", s.quizScore());
        m.put("attendDays", s.attendDays());
        m.put("score", score(s));
        return m;

    }

    private RangeStats stats(Long userId, LocalDate from, LocalDate to) {

        if (userId == null || to.isBefore(from)) return RangeStats.EMPTY;
        return RangeStats.of(meDao.selectRangeStats(userId, from, to));

    }

    private static Long num(Object o) {

        return o instanceof Number n ? n.longValue() : null;

    }
}
