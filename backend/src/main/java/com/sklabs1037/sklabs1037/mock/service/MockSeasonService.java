package com.sklabs1037.sklabs1037.mock.service;

import com.sklabs1037.sklabs1037.me.service.BadgeService;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.mock.dao.MockTradeDao;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 🏆 모의투자 주간 시즌 (작업 SEASON_CLOSE, 토요일 09:00 — 금요일 장 마감 기록까지 본 뒤)
 * - 그 주 수익률(지난주 마지막 장 마감 대비, MockTradeServiceImpl.weekStandings) TOP 3를 T_MOCK_SEASON에 기록
 * - 사람만 (🤖 AI 리그 참가자 제외), 수익률이 같으면 같은 등수 (3등 안이면 모두)
 * - 1등 👑 주간 챔피언, 1~3등 🏅 주간 TOP 3 배지 + 알림
 * - 다음 주 랭킹은 자동으로 새로 시작 (이번 주 = 지난주 장 마감 대비라서 따로 초기화 없음)
 */
@Service
public class MockSeasonService {

    @Autowired
    private IMockTradeService mockTradeService;

    @Autowired
    private MockTradeDao mockTradeDao;

    @Autowired
    private BadgeService badgeService;

    @Autowired
    private NotificationService notificationService;

    public JobOutcome closeWeek() {

        LocalDate weekStart = MockMarketHours.weekStart(MockMarketHours.now().toLocalDate());
        List<Map<String, Object>> winners = winners(mockTradeService.weekStandings());
        if (winners.isEmpty()) return JobOutcome.of(0, 0, weekStart + " 주: 참가자 없음");

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> w : winners) {

            Map<String, Object> row = new HashMap<>(w);
            row.put("weekStart", weekStart);
            if (mockTradeDao.insertSeasonWinner(row) == 0) continue;   // 이미 기록함 (다시 돌린 경우)

            Long userId = ((Number) w.get("userId")).longValue();
            int rank = (Integer) w.get("rank");
            String rate = signed((BigDecimal) w.get("returnRate"));

            notificationService.notify(userId, "BADGE", (rank == 1 ? "👑 이번 주 모의투자 1등!" : "🏅 이번 주 모의투자 " + rank + "등!"),
                    weekStart.getMonthValue() + "/" + weekStart.getDayOfMonth() + " 주 수익률 " + rate + " · 다음 주도 도전해보세요",
                    "/mock", "SEASON:" + weekStart);
            badgeService.awardOne(userId, "SEASON_TOP3");
            if (rank == 1) badgeService.awardOne(userId, "SEASON_WIN");

            sb.append(rank).append("등 ").append(w.get("nickname")).append(' ').append(rate).append(" / ");

        }

        return JobOutcome.of(winners.size(), 0, weekStart + " 주 마감: " + sb);

    }

    public List<Map<String, Object>> recent(int weeks) {

        return mockTradeDao.selectSeasons(Math.max(1, Math.min(weeks, 26)));

    }

    // 사람만, 수익률 높은 순 → 같은 수익률은 같은 등수, 3등까지 (순수 계산, 테스트 가능)
    static List<Map<String, Object>> winners(List<Map<String, Object>> standings) {

        List<Map<String, Object>> humans = new ArrayList<>(standings.stream()
                .filter(r -> !Boolean.TRUE.equals(r.get("ai")) && r.get("returnRate") instanceof BigDecimal)
                .toList());
        humans.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("returnRate")).reversed());

        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < humans.size(); i++) {
            Map<String, Object> r = new LinkedHashMap<>(humans.get(i));
            boolean tie = i > 0 && ((BigDecimal) r.get("returnRate")).compareTo((BigDecimal) humans.get(i - 1).get("returnRate")) == 0;
            int rank = tie ? (Integer) out.get(out.size() - 1).get("rank") : i + 1;
            if (rank > 3) break;
            r.put("rank", rank);
            out.add(r);
        }
        return out;

    }

    private static String signed(BigDecimal v) {

        return (v.signum() > 0 ? "+" : "") + v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "%";

    }
}
