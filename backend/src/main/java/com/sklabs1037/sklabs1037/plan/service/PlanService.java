package com.sklabs1037.sklabs1037.plan.service;

import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import com.sklabs1037.sklabs1037.plan.dao.PlanDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 🗓️ 내 약속
 * - 추가: 내 약속 화면(/plans) 또는 AI 챗봇("토요일 저녁 7시 강남에서 민지랑 약속 잡아줘" → add_plan 도구)
 * - 알림(작업 PLAN_REMIND, 매일 09:00): 내일 약속 → "하루 전" 알림, 오늘 약속 → "당일" 알림
 *   · 사이트 알림 + 📲 푸시, 누르면 /ai?brief=plan 에서 소리로 읽어 줌
 *   · 한 사람에 하루 한 개씩 묶음 (중복 방지 키 PLAN:날짜:D1 / PLAN:날짜:D0 → 작업이 두 번 돌아도 한 번만)
 * - 날짜 · 시간은 모두 한국 시간
 */
@Slf4j
@Service
public class PlanService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private PlanDao planDao;

    @Autowired
    private NotificationService notificationService;

    // ============================================
    // 추가 · 목록 · 삭제
    // ============================================
    public Map<String, Object> add(Long userId, Object title, Object date, Object time, Object place) {

        LocalDateTime now = LocalDateTime.now(KST);
        String cleanTitle = PlanRules.title(title);
        String cleanPlace = PlanRules.place(place);
        LocalDateTime at = PlanRules.when(date, time, now);

        if (planDao.countUpcoming(userId, now.toLocalDate().atStartOfDay()) >= PlanRules.MAX_UPCOMING) {
            throw new IllegalArgumentException("앞으로의 약속은 " + PlanRules.MAX_UPCOMING + "개까지 넣을 수 있어요.");
        }

        Map<String, Object> row = new HashMap<>();
        row.put("userId", userId);
        row.put("title", cleanTitle);
        row.put("place", cleanPlace);
        row.put("planAt", at);
        planDao.insertPlan(row);

        row.remove("userId");
        return view(row, now.toLocalDate());

    }

    public List<Map<String, Object>> upcoming(Long userId) {

        LocalDate today = LocalDate.now(KST);
        return planDao.selectMine(userId, today.atStartOfDay(), null).stream().map(p -> view(p, today)).toList();

    }

    public void delete(Long userId, Long id) {

        if (id == null || planDao.deletePlan(userId, id) == 0) throw new IllegalArgumentException("지울 약속을 찾지 못했어요.");

    }

    /** 오늘 · 내일 약속을 소리로 읽을 문장 (알림을 눌렀을 때) */
    public String speech(Long userId) {

        LocalDate today = LocalDate.now(KST);
        List<Map<String, Object>> todays = day(userId, today);
        List<Map<String, Object>> tomorrows = day(userId, today.plusDays(1));

        if (todays.isEmpty() && tomorrows.isEmpty()) return "오늘과 내일은 약속이 없어요.";

        List<String> parts = new ArrayList<>();
        if (!todays.isEmpty()) parts.add(PlanRules.speech(todays, "오늘"));
        if (!tomorrows.isEmpty()) parts.add(PlanRules.speech(tomorrows, "내일"));
        return String.join(" ", parts);

    }

    /** 오늘 약속 문장 (아침 브리핑용, 없으면 null) */
    public String todaySpeech(Long userId) {

        List<Map<String, Object>> todays = day(userId, LocalDate.now(KST));
        return todays.isEmpty() ? null : PlanRules.speech(todays, "오늘");

    }

    private List<Map<String, Object>> day(Long userId, LocalDate d) {

        List<Map<String, Object>> rows = planDao.selectMine(userId, d.atStartOfDay(), d.plusDays(1).atStartOfDay());
        rows.forEach(r -> r.put("planAt", toDateTime(r.get("planAt"))));
        return rows;

    }

    // ============================================
    // 매일 09:00 알림
    // ============================================
    public JobOutcome remindAll() {

        LocalDate today = LocalDate.now(KST);
        int[] sent = {0, 0};
        int failed = 0;

        failed += remind(today.plusDays(1), "내일", "D1", sent, 0);
        failed += remind(today, "오늘", "D0", sent, 1);

        int old = planDao.deleteOld(today.minusDays(90).atStartOfDay());

        return JobOutcome.of(sent[0] + sent[1] + failed, failed,
                "하루 전 알림 " + sent[0] + "명 · 당일 알림 " + sent[1] + "명" + (old > 0 ? " · 지난 약속 " + old + "개 정리" : ""));

    }

    private int remind(LocalDate day, String dayWord, String tag, int[] sent, int slot) {

        // 회원별로 묶기 (순서 유지)
        Map<Long, List<Map<String, Object>>> byUser = new LinkedHashMap<>();
        for (Map<String, Object> p : planDao.selectAllBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay())) {
            p.put("planAt", toDateTime(p.get("planAt")));
            byUser.computeIfAbsent(((Number) p.get("userId")).longValue(), k -> new ArrayList<>()).add(p);
        }

        int failed = 0;
        for (Map.Entry<Long, List<Map<String, Object>>> e : byUser.entrySet()) {
            try {
                PlanRules.Message msg = PlanRules.reminder(e.getValue(), dayWord);
                notificationService.notify(e.getKey(), "PLAN", msg.title(), msg.body(), "/ai?brief=plan", "PLAN:" + day + ":" + tag);
                sent[slot]++;
            } catch (Exception ex) {
                failed++;
                log.warn("약속 알림 실패: {}", ex.getMessage());
            }
        }
        return failed;

    }

    // ============================================
    // 공통
    // ============================================
    private static Map<String, Object> view(Map<String, Object> p, LocalDate today) {

        LocalDateTime at = toDateTime(p.get("planAt"));
        String place = p.get("place") == null ? null : String.valueOf(p.get("place"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.get("id"));
        m.put("title", p.get("title"));
        m.put("place", place);
        m.put("date", at.toLocalDate().toString());
        m.put("time", at.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) ? null : at.toLocalTime().toString().substring(0, 5));
        m.put("day", PlanRules.dayText(at.toLocalDate(), today));
        m.put("weekday", PlanRules.weekday(at.getDayOfWeek()));
        m.put("text", PlanRules.dayText(at.toLocalDate(), today) + " " + PlanRules.line(at, String.valueOf(p.get("title")), place));
        return m;

    }

    static LocalDateTime toDateTime(Object v) {

        if (v instanceof LocalDateTime t) return t;
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(v).replace(' ', 'T'));

    }
}
