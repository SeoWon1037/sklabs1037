package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.calendar.service.MarketCalendar;
import com.sklabs1037.sklabs1037.ops.dao.JobRunDao;
import com.sklabs1037.sklabs1037.ops.vo.JobRunVO;
import com.sklabs1037.sklabs1037.qa.dao.QaRunDao;
import com.sklabs1037.sklabs1037.qa.vo.QaRunVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.*;
import java.util.*;

/**
 * 🟢 서비스 상태 (누구나 보는 /status 화면)
 * - 지금 사이트가 제대로 도는지 사용자에게 투명하게: 웹 · DB · 주식 시세 · 뉴스 · AI 기능 · 최근 자동 점검(QA 봇)
 * - 상태: OK 정상 / WARN 늦어짐·일부 멈춤 / DOWN 장애
 * - 30초 동안 같은 결과를 재사용 (많이 열어도 DB 부담 없게), 내부 정보(에러 내용·주소)는 내보내지 않음
 */
@Slf4j
@Service
public class StatusService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long CACHE_MS = 30_000;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JobRunDao jobRunDao;

    @Autowired
    private QaRunDao qaRunDao;

    @Autowired
    private AiUsageService aiUsageService;

    private volatile Map<String, Object> cached;
    private volatile long cachedAt;

    public Map<String, Object> status() {

        long now = System.currentTimeMillis();
        Map<String, Object> c = cached;
        if (c != null && now - cachedAt < CACHE_MS) return c;

        Map<String, Object> out = build(LocalDateTime.now(KST));
        cached = out;
        cachedAt = now;
        return out;

    }

    private Map<String, Object> build(LocalDateTime now) {

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("web", "웹사이트", "OK", "정상 응답 중"));

        boolean dbOk = dbOk();
        items.add(item("db", "데이터베이스", dbOk ? "OK" : "DOWN", dbOk ? "정상 연결" : "연결이 안 돼요. 곧 복구할게요."));

        JobRunVO h = null;
        if (dbOk) {
            try {
                h = jobRunDao.selectDataHealth();
            } catch (Exception e) {
                log.warn("상태 화면: 데이터 상태 읽기 실패: {}", e.getMessage());
            }
        }

        // 주식 시세
        LocalDate last = h == null ? null : h.getLastTradeDate();
        String stock = stockStatus(last, now);
        items.add(item("stock", "주식 시세", stock, last == null ? "확인할 수 없어요"
                : last.getMonthValue() + "/" + last.getDayOfMonth() + " 시세까지 반영" + ("OK".equals(stock) ? "" : " · 업데이트가 늦어지고 있어요")));

        // 뉴스
        LocalDateTime news = h == null ? null : h.getLastNewsAt();
        String newsSt = news == null ? "WARN" : Duration.between(news, now).toHours() < 8 ? "OK" : "WARN";
        items.add(item("news", "뉴스 · AI 요약", newsSt, news == null ? "확인할 수 없어요" : "마지막 기사 " + ago(news, now)));

        // AI
        boolean aiBlocked = aiUsageService.blocked();
        items.add(item("ai", "AI 기능 (비서 · 뷰티 랩)", aiBlocked ? "WARN" : "OK", aiBlocked ? "사용량이 많아 잠시 멈췄어요 (내일 다시 열려요)" : "정상"));

        // 자동 화면 점검 (QA 봇)
        List<Map<String, Object>> checks = new ArrayList<>();
        try {
            for (QaRunVO r : dbOk ? qaRunDao.selectRuns(5) : List.<QaRunVO>of()) {
                if ("ANALYZING".equals(r.getStatus())) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("status", r.getStatus());
                m.put("source", r.getSource());
                m.put("at", r.getInsAt());
                checks.add(m);
            }
        } catch (Exception e) {
            log.warn("상태 화면: QA 기록 읽기 실패: {}", e.getMessage());
        }
        String qa = checks.isEmpty() ? "OK" : switch (String.valueOf(checks.get(0).get("status"))) {
            case "FAIL" -> "WARN";
            default -> "OK";
        };
        items.add(item("qa", "자동 화면 점검 (AI QA 봇)", qa, checks.isEmpty() ? "기록 없음"
                : "PASS".equals(checks.get(0).get("status")) ? "최근 점검 통과" : "WARN".equals(checks.get(0).get("status")) ? "최근 점검: 작은 문제 확인 중" : "최근 점검: 문제를 고치는 중"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("overall", overall(items));
        out.put("items", items);
        out.put("checks", checks);
        out.put("tradingToday", MarketCalendar.isTradingDay(now.toLocalDate()));
        out.put("at", now.atZone(KST).toInstant().toEpochMilli());
        return out;

    }

    // 시세가 제때 들어왔나 (순수 계산, 테스트 가능)
    // - 기대하는 날: 오늘이 거래일이고 16:00가 지났으면 오늘, 아니면 그 전 거래일
    // - 기대하는 날 시세가 있으면 OK, 한 거래일 늦으면 WARN, 더 늦으면 DOWN
    static String stockStatus(LocalDate last, LocalDateTime now) {

        if (last == null) return "WARN";
        LocalDate expect = now.toLocalDate();
        if (!(MarketCalendar.isTradingDay(expect) && now.toLocalTime().isAfter(LocalTime.of(16, 0)))) expect = prevTradingDay(expect);
        if (!last.isBefore(expect)) return "OK";
        return last.isBefore(prevTradingDay(expect)) ? "DOWN" : "WARN";

    }

    static String overall(List<Map<String, Object>> items) {

        if (items.stream().anyMatch(i -> "DOWN".equals(i.get("status")))) return "DOWN";
        if (items.stream().anyMatch(i -> "WARN".equals(i.get("status")))) return "WARN";
        return "OK";

    }

    private static LocalDate prevTradingDay(LocalDate d) {

        LocalDate x = d.minusDays(1);
        while (!MarketCalendar.isTradingDay(x)) x = x.minusDays(1);
        return x;

    }

    private boolean dbOk() {

        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }

    }

    private static Map<String, Object> item(String key, String label, String status, String detail) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("status", status);
        m.put("detail", detail);
        return m;

    }

    private static String ago(LocalDateTime t, LocalDateTime now) {

        long m = Duration.between(t, now).toMinutes();
        if (m < 1) return "방금";
        if (m < 60) return m + "분 전";
        if (m < 1440) return (m / 60) + "시간 전";
        return (m / 1440) + "일 전";

    }
}
