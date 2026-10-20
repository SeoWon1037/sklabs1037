package com.sklabs1037.sklabs1037.me.service;

import com.sklabs1037.sklabs1037.me.dao.MeDao;
import com.sklabs1037.sklabs1037.me.service.BadgeService.Badge;
import com.sklabs1037.sklabs1037.mock.service.IMockTradeService;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.*;

/**
 * 📸 내 성적 공유 카드
 *
 * - "카드 만들기"를 누른 순간의 숫자를 저장하고 짧은 코드를 줌 → https://sklabs1037.com/s/코드
 * - 링크를 연 사람은 저장된 스냅샷만 봄 (닉네임 · 이번 주 예측·퀴즈·출석 · 모의투자 수익률 · 배지)
 *   이메일·계정 정보는 넣지 않음
 * - 하루 20장까지
 */
@Slf4j
@Service
public class ShareService {

    private static final int DAILY_LIMIT = 20;

    @Autowired
    private MeDao meDao;

    @Autowired
    private IMockTradeService mockTradeService;

    @Autowired
    private JsonMapper jsonMapper;

    @SuppressWarnings("unchecked")
    public Map<String, Object> create(Long userId) {

        if (meDao.countSharesToday(userId) >= DAILY_LIMIT) throw new IllegalArgumentException("카드는 하루 " + DAILY_LIMIT + "장까지 만들 수 있어요.");

        LocalDate today = MockMarketHours.now().toLocalDate();
        LocalDate week = CoachService.weekStart(today);
        RangeStats s = RangeStats.of(meDao.selectRangeStats(userId, week, today));
        Map<String, Object> all = meDao.selectActivity(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nickname", meDao.selectNickname(userId));
        data.put("from", week.toString());
        data.put("to", today.toString());
        data.put("predictVotes", s.predictVotes());
        data.put("predictGraded", s.predictGraded());
        data.put("predictHits", s.predictHits());
        data.put("quizPlays", s.quizPlays());
        data.put("quizScore", s.quizScore());
        data.put("attendDays", s.attendDays());
        data.put("attendStreak", ActivityService.currentStreak(meDao.selectAttendDays(userId), today));
        data.put("personaType", all.get("personaType"));

        // 모의투자 수익률 (거래한 적 있을 때만)
        Double returnRate = null;
        if (all.get("tradeCount") instanceof Number n && n.intValue() > 0) {
            Map<String, Object> account = (Map<String, Object>) mockTradeService.getAccount(userId).get("account");
            if (account != null && account.get("returnRate") instanceof Number r) returnRate = r.doubleValue();
        }
        data.put("returnRate", returnRate);

        // 배지: 개수 + 최근 받은 3개
        List<Map<String, Object>> rows = new ArrayList<>(meDao.selectBadges(userId));
        rows.sort(Comparator.comparingLong((Map<String, Object> r) -> r.get("earnedAt") instanceof Number n ? n.longValue() : 0L).reversed());
        List<Map<String, Object>> recent = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Badge b = BadgeService.find(String.valueOf(r.get("code")));
            if (b == null || recent.size() >= 3) continue;
            recent.add(Map.of("icon", b.icon(), "name", b.name()));
        }
        data.put("badgeCount", rows.size());
        data.put("badgeTotal", BadgeService.BADGES.size());
        data.put("badges", recent);
        data.put("createdAt", System.currentTimeMillis());

        String code = Codes.random(10);
        meDao.insertShare(code, userId, jsonMapper.writeValueAsString(data));

        return Map.of("code", code, "card", data);

    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String code) {

        if (code == null || !code.matches("[A-Za-z0-9]{6,16}")) return null;

        String json = meDao.selectShare(code);
        return json == null ? null : jsonMapper.readValue(json, LinkedHashMap.class);

    }
}
