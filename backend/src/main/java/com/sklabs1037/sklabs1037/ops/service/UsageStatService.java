package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.ops.dao.UsageStatDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 📊 사용 통계 (방문자 · 화면 열기 · 이벤트)
 *
 * - 브라우저가 화면을 열 때마다 POST /api/track { p: 주소, v: 브라우저 무작위 번호 }
 *   → 메모리에 더해 두고 1분마다 DB에 한 번에 저장 (요청마다 DB를 두드리지 않게)
 * - 주소는 첫 칸만 (/stock?symbol=005930 → /stock), 관리자 화면 · 봇(검색 로봇 · QA 봇의 헤드리스 브라우저)은 세지 않음
 * - 방문자 번호는 서버 비밀값과 섞어 해시한 앞 16자만 저장 (원래 번호 · IP · 회원 번호는 저장 안 함)
 * - 이벤트: 체험 시작(demo_login) · 체험 중 가입(demo_signup) 등
 */
@Slf4j
@Service
public class UsageStatService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_PENDING_VISITORS = 20_000;

    @Autowired
    private UsageStatDao usageStatDao;

    @Value("${stats.salt:sklabs-usage-stat}")
    private String salt;

    private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<LocalDate, Set<String>> visitors = new ConcurrentHashMap<>();
    private volatile LocalDate cleanedDay;

    // ============================================
    // 받기
    // ============================================
    public void pageView(String path, String vid, String userAgent) {

        if (isBot(userAgent)) return;
        String key = pathKey(path);
        if (key == null) return;

        LocalDate day = LocalDate.now(KST);
        counts.computeIfAbsent(day + "|PV|" + key, k -> new LongAdder()).increment();

        if (vid != null && vid.matches("[A-Za-z0-9-]{8,64}")) {
            Set<String> set = visitors.computeIfAbsent(day, d -> ConcurrentHashMap.newKeySet());
            if (set.size() < MAX_PENDING_VISITORS) set.add(hash(vid));
        }

    }

    public void event(String key) {

        if (key == null || !key.matches("[a-z_]{1,40}")) return;
        counts.computeIfAbsent(LocalDate.now(KST) + "|EVENT|" + key, k -> new LongAdder()).increment();

    }

    // ============================================
    // 1분마다 DB에 (실패하면 버림 · 통계라서 다음 분부터 다시)
    // ============================================
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void flush() {

        try {

            for (String k : new ArrayList<>(counts.keySet())) {
                LongAdder a = counts.remove(k);
                long n = a == null ? 0 : a.sum();
                if (n == 0) continue;
                String[] p = k.split("\\|", 3);
                usageStatDao.addStat(LocalDate.parse(p[0]), p[1], p[2], n);
            }

            for (LocalDate day : new ArrayList<>(visitors.keySet())) {
                Set<String> set = visitors.remove(day);
                if (set == null || set.isEmpty()) continue;
                List<String> all = new ArrayList<>(set);
                for (int i = 0; i < all.size(); i += 500) usageStatDao.addVisitors(day, all.subList(i, Math.min(all.size(), i + 500)));
            }

            LocalDate today = LocalDate.now(KST);
            if (!today.equals(cleanedDay)) {
                cleanedDay = today;
                usageStatDao.deleteOld();
                usageStatDao.deleteOldVisitors();
            }

        } catch (Exception e) {

            log.warn("사용 통계 저장 실패 (이번 분은 버림): {}", e.getMessage());

        }

    }

    // ============================================
    // 관리자 화면
    // ============================================
    public Map<String, Object> dashboard() {

        flush();   // 방금 들어온 것까지

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("daily", usageStatDao.selectDaily(30));
        m.put("pages", usageStatDao.selectPages(7));
        m.put("signups", usageStatDao.selectSignups(30));

        Map<String, Object> events = new LinkedHashMap<>();
        for (Map<String, Object> e : usageStatDao.selectEvents(7)) events.put(String.valueOf(e.get("key")), e.get("cnt"));
        m.put("events", events);

        try {
            m.put("features", usageStatDao.selectFeatures());
        } catch (Exception e) {
            log.warn("기능별 사용 집계 실패: {}", e.getMessage());
            m.put("features", List.of());
        }
        return m;

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================
    // 주소 → 첫 칸 (/stock?symbol=1 → /stock), 관리자 · 이상한 주소는 null
    static String pathKey(String path) {

        if (path == null || path.isEmpty() || path.length() > 200 || path.charAt(0) != '/') return null;
        String p = path.split("[?#]", 2)[0];
        String first = p.length() <= 1 ? "/" : "/" + p.substring(1).split("/", 2)[0];
        if (first.equals("/admin") || !first.matches("/[a-z0-9-]{0,30}")) return null;
        return first;

    }

    static boolean isBot(String ua) {

        if (ua == null || ua.isBlank()) return true;
        String u = ua.toLowerCase(Locale.ROOT);
        return u.contains("bot") || u.contains("crawler") || u.contains("spider") || u.contains("headless") || u.contains("lighthouse");

    }

    private String hash(String vid) {

        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((salt + ":" + vid).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(vid.hashCode());
        }

    }
}
