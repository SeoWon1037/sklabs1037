package com.sklabs1037.sklabs1037.plan.service;

import com.sklabs1037.sklabs1037.couple.service.CoupleService;
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
 * - 💕 함께(shared): 커플인 상대의 목록 · 알림에도 나옴. 만들 때 상대에게 바로 알림. 지울 수 있는 사람은 만든 사람
 * - 🔁 매주 반복: 12주치를 미리 여러 줄로 만들어 둠(REPEAT_ID로 묶음) → 목록 · 알림 코드는 반복을 몰라도 됨
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

    @Autowired
    private CoupleService coupleService;

    // ============================================
    // 추가 · 목록 · 삭제
    // ============================================
    public Map<String, Object> add(Long userId, Object title, Object date, Object time, Object place) {

        return add(userId, title, date, time, place, false, false);

    }

    public Map<String, Object> add(Long userId, Object title, Object date, Object time, Object place, boolean shared, boolean weekly) {

        LocalDateTime now = LocalDateTime.now(KST);
        String cleanTitle = PlanRules.title(title);
        String cleanPlace = PlanRules.place(place);
        LocalDateTime at = PlanRules.when(date, time, now);

        Map<String, Object> partner = shared ? partner(userId) : null;
        if (shared && partner == null) throw new IllegalArgumentException("함께하는 약속은 커플이 된 뒤에 만들 수 있어요. (커플 화면에서 신청)");

        List<LocalDateTime> times = weekly ? PlanRules.weekly(at, PlanRules.REPEAT_WEEKS) : List.of(at);

        if (planDao.countUpcoming(userId, now.toLocalDate().atStartOfDay()) + times.size() > PlanRules.MAX_UPCOMING) {
            throw new IllegalArgumentException("앞으로의 약속은 " + PlanRules.MAX_UPCOMING + "개까지 넣을 수 있어요.");
        }

        Map<String, Object> first = null;
        Long repeatId = null;

        for (LocalDateTime t : times) {
            Map<String, Object> row = new HashMap<>();
            row.put("userId", userId);
            row.put("title", cleanTitle);
            row.put("place", cleanPlace);
            row.put("planAt", t);
            row.put("shared", shared ? "Y" : "N");
            row.put("repeatId", repeatId);
            planDao.insertPlan(row);

            if (first == null) {
                first = row;
                if (weekly) {
                    // 묶음 번호 = 첫 약속의 번호
                    repeatId = ((Number) row.get("id")).longValue();
                    planDao.updateRepeatId(repeatId, repeatId);
                    row.put("repeatId", repeatId);
                }
            }
        }

        first.put("ownerId", userId);
        Map<String, Object> view = view(first, userId, now.toLocalDate());
        if (weekly) view.put("text", "매주 " + view.get("weekday") + " · " + view.get("text") + "부터 " + PlanRules.REPEAT_WEEKS + "주");

        if (partner != null) {
            notificationService.notify(((Number) partner.get("id")).longValue(), "PLAN",
                    "💕 " + partner.get("myName") + "님이 함께하는 약속을 잡았어요", String.valueOf(view.get("text")), "/plans");
        }

        return view;

    }

    public List<Map<String, Object>> upcoming(Long userId) {

        LocalDate today = LocalDate.now(KST);
        return planDao.selectMine(userId, partnerId(userId), today.atStartOfDay(), null).stream().map(p -> view(p, userId, today)).toList();

    }

    // all: 반복 약속이면 그 약속부터 뒤의 것까지 모두
    public int delete(Long userId, Long id, boolean all) {

        int deleted = id == null ? 0 : (all ? planDao.deleteRepeat(userId, id) : 0);
        if (deleted == 0 && id != null) deleted = planDao.deletePlan(userId, id);
        if (deleted == 0) throw new IllegalArgumentException("지울 약속을 찾지 못했어요. (상대가 만든 약속은 만든 사람만 지울 수 있어요)");
        return deleted;

    }

    public void delete(Long userId, Long id) {

        delete(userId, id, false);

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

        List<Map<String, Object>> rows = planDao.selectMine(userId, partnerId(userId), d.atStartOfDay(), d.plusDays(1).atStartOfDay());
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

        // 회원별로 묶기 (순서 유지). 함께하는 약속은 커플인 상대에게도
        Map<Long, List<Map<String, Object>>> byUser = new LinkedHashMap<>();
        Map<Long, Long> partnerCache = new HashMap<>();

        for (Map<String, Object> p : planDao.selectAllBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay())) {
            p.put("planAt", toDateTime(p.get("planAt")));
            Long owner = ((Number) p.get("userId")).longValue();
            byUser.computeIfAbsent(owner, k -> new ArrayList<>()).add(p);

            if ("Y".equals(p.get("shared"))) {
                Long partner = partnerCache.computeIfAbsent(owner, k -> Objects.requireNonNullElse(partnerId(k), -1L));
                if (partner > 0) byUser.computeIfAbsent(partner, k -> new ArrayList<>()).add(p);
            }
        }

        int failed = 0;
        for (Map.Entry<Long, List<Map<String, Object>>> e : byUser.entrySet()) {
            try {
                List<Map<String, Object>> plans = new ArrayList<>(e.getValue());
                plans.sort(Comparator.comparing(p -> (LocalDateTime) p.get("planAt")));
                PlanRules.Message msg = PlanRules.reminder(plans, dayWord);
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
    private Map<String, Object> partner(Long userId) {

        try {
            return coupleService.partner(userId);
        } catch (Exception e) {
            log.warn("커플 확인 실패 (내 약속만 사용): {}", e.getMessage());
            return null;
        }

    }

    private Long partnerId(Long userId) {

        Map<String, Object> p = partner(userId);
        return p == null ? null : ((Number) p.get("id")).longValue();

    }

    static Map<String, Object> view(Map<String, Object> p, Long me, LocalDate today) {

        LocalDateTime at = toDateTime(p.get("planAt"));
        String place = p.get("place") == null ? null : String.valueOf(p.get("place"));
        boolean mine = p.get("ownerId") == null || me.equals(((Number) p.get("ownerId")).longValue());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.get("id"));
        m.put("title", p.get("title"));
        m.put("place", place);
        m.put("date", at.toLocalDate().toString());
        m.put("time", at.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) ? null : at.toLocalTime().toString().substring(0, 5));
        m.put("day", PlanRules.dayText(at.toLocalDate(), today));
        m.put("weekday", PlanRules.weekday(at.getDayOfWeek()));
        m.put("shared", "Y".equals(p.get("shared")));       // 💕 커플과 함께
        m.put("mine", mine);                                 // 내가 만든 약속인지 (아니면 상대가 만든 함께 약속 → 못 지움)
        m.put("repeat", p.get("repeatId") != null);          // 🔁 매주 반복 묶음
        m.put("text", PlanRules.dayText(at.toLocalDate(), today) + " " + PlanRules.line(at, String.valueOf(p.get("title")), place));
        return m;

    }

    static LocalDateTime toDateTime(Object v) {

        if (v instanceof LocalDateTime t) return t;
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(v).replace(' ', 'T'));

    }
}
