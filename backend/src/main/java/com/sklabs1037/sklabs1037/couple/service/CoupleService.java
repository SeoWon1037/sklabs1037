package com.sklabs1037.sklabs1037.couple.service;

import com.sklabs1037.sklabs1037.couple.dao.CoupleDao;
import com.sklabs1037.sklabs1037.me.dao.MeDao;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 💕 커플 맺기
 *
 * 흐름
 *   1. 신청: 상대 닉네임 + 시작일(신청한 사람이 정함, 오늘이나 과거) → 🔔 상대에게 알림
 *   2. 상대가 수락하면 커플 (둘 다 다른 커플이 없을 때만) / 거절 · 신청 취소도 가능
 *   3. 로그인하면 "○○님과 200일 ♥" (시작일 = 1일, 우리나라에서 세는 방식) + 다가오는 기념일 D-day
 *   4. 둘 중 한 사람이 "커플 끊기"를 하면 끝, 상대에게 알림
 *   5. 💗 하트 날리기: 커플끼리만, 누르면 상대에게 🔔 알림 (안 읽은 하트 알림은 "오늘 n번째" 한 줄로 합침)
 *      1.5초에 한 번 · 하루 300개까지
 *
 * - 커플 정보는 두 사람에게만 보임 (다른 사람 화면에는 안 나옴)
 * - 보낸 신청은 한 번에 1개까지 (아무에게나 여러 번 보내지 않게)
 */
@Slf4j
@Service
public class CoupleService {

    @Autowired
    private CoupleDao coupleDao;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MeDao meDao;

    static final long HEART_GAP_MS = 1500;
    static final int HEART_DAILY = 300;

    // 연타 막기 (사람마다 마지막으로 보낸 시각)
    private final Map<Long, Long> lastHeart = new java.util.concurrent.ConcurrentHashMap<>();

    // ============================================
    // 보기
    // ============================================
    public Map<String, Object> mine(Long userId) {

        LocalDate today = today();
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> active = coupleDao.selectActive(userId);
        if (active != null) {
            LocalDate start = LocalDate.parse(String.valueOf(active.get("startDate")));
            Map<String, Object> c = new LinkedHashMap<>(active);
            c.put("days", days(start, today));
            c.put("next", nextMilestone(start, today));
            Long id = num(active.get("id"));
            Long partnerId = partnerOf(id, userId);
            c.put("heartsSent", coupleDao.selectHeartCounts(id, userId));
            c.put("heartsReceived", partnerId == null ? Map.of("total", 0, "today", 0) : coupleDao.selectHeartCounts(id, partnerId));
            result.put("couple", c);
        } else {
            result.put("couple", null);
        }

        result.put("sent", coupleDao.selectSent(userId));
        result.put("received", coupleDao.selectReceived(userId));
        result.put("today", today.toString());
        return result;

    }

    // 메인 · 상단에 쓰는 짧은 정보 (커플이 아니면 null)
    public Map<String, Object> summary(Long userId) {

        Map<String, Object> active = coupleDao.selectActive(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("received", coupleDao.selectReceived(userId).size());

        if (active == null) {
            result.put("couple", null);
            return result;
        }

        LocalDate today = today();
        LocalDate start = LocalDate.parse(String.valueOf(active.get("startDate")));
        result.put("couple", Map.of(
                "partnerName", String.valueOf(active.get("partnerName")),
                "days", days(start, today),
                "next", nextMilestone(start, today)));
        return result;

    }

    // ============================================
    // 신청 · 수락 · 거절 · 취소 · 끊기
    // ============================================
    public void request(Long userId, String nickname, String startDate) {

        String name = nickname == null ? "" : nickname.strip();
        if (name.isEmpty()) throw new IllegalArgumentException("상대 닉네임을 적어주세요.");

        LocalDate start = parseStart(startDate, today());

        List<Map<String, Object>> found = coupleDao.selectUsersByNickname(name);
        if (found.isEmpty()) throw new IllegalArgumentException("'" + name + "' 닉네임을 가진 회원이 없어요. 띄어쓰기까지 똑같이 적어주세요.");
        if (found.size() > 1) throw new IllegalArgumentException("같은 닉네임이 여러 명이에요. 상대에게 닉네임을 조금 바꿔 달라고 해주세요.");

        Long partnerId = ((Number) found.get(0).get("id")).longValue();
        if (partnerId.equals(userId)) throw new IllegalArgumentException("나 자신에게는 신청할 수 없어요.");
        if (coupleDao.countActive(userId) > 0) throw new IllegalArgumentException("이미 커플이에요.");
        if (coupleDao.countActive(partnerId) > 0) throw new IllegalArgumentException("상대가 이미 다른 분과 커플이에요.");
        if (coupleDao.countPendingBetween(userId, partnerId) > 0) throw new IllegalArgumentException("두 분 사이에 이미 기다리는 신청이 있어요.");
        if (coupleDao.countSentPending(userId) > 0) throw new IllegalArgumentException("보낸 신청이 있어요. 먼저 그 신청을 취소해주세요.");

        coupleDao.insertRequest(userId, partnerId, start);

        String me = myName(userId);
        notificationService.notify(partnerId, "COUPLE", "💌 " + me + "님이 커플 신청을 보냈어요",
                "시작일 " + start + " · 수락하면 함께한 날이 보여요", "/couple");

    }

    public void accept(Long userId, Long id) {

        Map<String, Object> c = coupleDao.selectCouple(id);
        if (c == null || !userId.equals(num(c.get("partnerId"))) || !"PENDING".equals(c.get("status"))) {
            throw new IllegalArgumentException("수락할 수 없는 신청이에요.");
        }

        if (coupleDao.accept(id, userId) == 0) throw new IllegalArgumentException("둘 중 한 분이 이미 커플이라 수락할 수 없어요.");

        Long requesterId = num(c.get("requesterId"));
        coupleDao.cancelOthers(requesterId, userId);

        notificationService.notify(requesterId, "COUPLE", "💕 " + myName(userId) + "님이 커플 신청을 수락했어요",
                "오늘부터 함께한 날을 세어 드릴게요", "/couple");

    }

    public void decline(Long userId, Long id) {

        Map<String, Object> c = coupleDao.selectCouple(id);
        if (coupleDao.decline(id, userId) == 0) throw new IllegalArgumentException("거절할 수 없는 신청이에요.");

        notificationService.notify(num(c.get("requesterId")), "COUPLE", "💌 보낸 커플 신청이 닫혔어요",
                myName(userId) + "님이 신청을 받지 않았어요", "/couple");

    }

    public void cancel(Long userId, Long id) {

        if (coupleDao.cancel(id, userId) == 0) throw new IllegalArgumentException("취소할 수 없는 신청이에요.");

    }

    public void end(Long userId) {

        Map<String, Object> active = coupleDao.selectActive(userId);
        if (active == null) throw new IllegalArgumentException("지금 커플이 아니에요.");

        Long id = num(active.get("id"));
        Map<String, Object> c = coupleDao.selectCouple(id);
        if (coupleDao.end(id, userId) == 0) throw new IllegalArgumentException("커플을 끊지 못했어요.");

        Long other = userId.equals(num(c.get("requesterId"))) ? num(c.get("partnerId")) : num(c.get("requesterId"));
        notificationService.notify(other, "COUPLE", "💔 커플 연결이 끝났어요", myName(userId) + "님이 커플 연결을 끊었어요", "/couple");

    }

    // 💗 하트 날리기
    public Map<String, Object> heart(Long userId) {

        Map<String, Object> active = coupleDao.selectActive(userId);
        if (active == null) throw new IllegalArgumentException("커플끼리만 하트를 보낼 수 있어요.");

        long now = System.currentTimeMillis();
        Long last = lastHeart.put(userId, now);
        if (last != null && now - last < HEART_GAP_MS) throw new IllegalArgumentException("하트가 너무 빨라요. 천천히 보내주세요 💗");

        Long id = num(active.get("id"));
        if (((Number) coupleDao.selectHeartCounts(id, userId).get("today")).intValue() >= HEART_DAILY) {
            throw new IllegalArgumentException("오늘은 하트를 " + HEART_DAILY + "개 다 보냈어요. 내일 또 보내주세요 💗");
        }

        coupleDao.insertHeart(id, userId);
        Map<String, Object> counts = coupleDao.selectHeartCounts(id, userId);

        Long partnerId = partnerOf(id, userId);
        if (partnerId != null) {
            coupleDao.deleteUnreadHeartNotifications(partnerId);
            notificationService.notify(partnerId, "HEART", "💗 " + active.get("myName") + "님이 하트를 보냈어요",
                    "오늘 " + counts.get("today") + "번째 하트예요", "/couple");
        }

        return Map.of("sent", counts);

    }

    private Long partnerOf(Long coupleId, Long userId) {

        Map<String, Object> c = coupleDao.selectCouple(coupleId);
        if (c == null) return null;
        return userId.equals(num(c.get("requesterId"))) ? num(c.get("partnerId")) : num(c.get("requesterId"));

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================

    // 시작일 = 1일
    static long days(LocalDate start, LocalDate today) {

        return ChronoUnit.DAYS.between(start, today) + 1;

    }

    // 다가오는 기념일: 100일 단위 · 1주년 단위 중 가장 가까운 것 (오늘이면 dDay 0)
    static Map<String, Object> nextMilestone(LocalDate start, LocalDate today) {

        long d = days(start, today);

        // 100일 단위: N00일 = 시작일 + (N00 - 1)일
        long nextHundred = Math.max(100, ((d + 99) / 100) * 100);
        LocalDate hundredDate = start.plusDays(nextHundred - 1);

        // 주년: 시작일과 같은 날짜
        long yearN = ChronoUnit.YEARS.between(start, today);
        if (yearN == 0 || start.plusYears(yearN).isBefore(today)) yearN++;
        LocalDate yearDate = start.plusYears(yearN);

        boolean hundredFirst = !hundredDate.isAfter(yearDate);
        LocalDate date = hundredFirst ? hundredDate : yearDate;
        String label = hundredFirst ? nextHundred + "일" : yearN + "주년";

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("date", date.toString());
        m.put("dDay", ChronoUnit.DAYS.between(today, date));
        return m;

    }

    static LocalDate parseStart(String text, LocalDate today) {

        LocalDate start;
        try {
            start = LocalDate.parse(text == null ? "" : text.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("시작일을 골라주세요.");
        }

        if (start.isAfter(today)) throw new IllegalArgumentException("시작일은 오늘이나 지난 날짜로 골라주세요.");
        if (start.isBefore(today.minusYears(80))) throw new IllegalArgumentException("시작일을 다시 확인해주세요.");
        return start;

    }

    private String myName(Long userId) {

        return Objects.toString(meDao.selectNickname(userId), "회원");

    }

    private static LocalDate today() {

        return MockMarketHours.now().toLocalDate();

    }

    private static Long num(Object o) {

        return o instanceof Number n ? n.longValue() : null;

    }
}
