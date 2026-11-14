package com.sklabs1037.sklabs1037.plan.service;

import com.sklabs1037.sklabs1037.calendar.service.MarketCalendar;
import com.sklabs1037.sklabs1037.client.WeatherClient;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.mock.service.MockPriceService;
import com.sklabs1037.sklabs1037.mock.vo.MockTradeVO;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import com.sklabs1037.sklabs1037.plan.dao.PlanDao;
import com.sklabs1037.sklabs1037.plan.service.VoiceBriefRules.Region;
import com.sklabs1037.sklabs1037.plan.service.VoiceBriefRules.Weather;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🔊 음성 브리핑
 * - 아침 (작업 VOICE_MORNING, 매일 07:00): 오늘 날씨(지역 선택) + 오늘 약속 + 관심 종목 뉴스 제목 한 개 → 알림 + 📲 푸시
 * - 장 마감 (작업 VOICE_CLOSE, 거래일 15:40): 내 관심 종목이 오늘 얼마나 움직였는지 → 알림 + 📲 푸시 (모의투자 내용은 넣지 않음)
 * - 알림을 누르면 /ai?brief=morning|close|plan → GET /api/voice/brief 로 "지금 기준" 문장을 다시 만들어 소리로 읽어 줌
 *   (웹사이트는 꺼져 있을 때 스스로 소리를 낼 수 없어서, 알림 → 한 번 누르기 → 읽기 순서)
 * - 문장은 VoiceBriefRules가 만듦 (AI 호출 없음)
 * - 교통 실시간 정보는 아직 없음 (공공 API 키가 생기면 여기 morning에 한 줄 추가)
 */
@Slf4j
@Service
public class VoiceBriefService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long WEATHER_CACHE_SECONDS = 20 * 60;

    @Autowired
    private PlanDao planDao;

    @Autowired
    private PlanService planService;

    @Autowired
    private WeatherClient weatherClient;

    @Autowired
    private MockPriceService mockPriceService;

    @Autowired
    private NotificationService notificationService;

    private record CachedWeather(Weather weather, Instant at) {
    }

    private final Map<String, CachedWeather> weatherCache = new ConcurrentHashMap<>();

    // ============================================
    // 설정
    // ============================================
    public Map<String, Object> settings(Long userId) {

        Map<String, Object> s = planDao.selectSetting(userId);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("morning", s != null && "Y".equals(s.get("morning")));
        m.put("close", s != null && "Y".equals(s.get("close")));
        m.put("region", VoiceBriefRules.region(s == null ? null : s.get("region")).code());
        m.put("regions", VoiceBriefRules.REGIONS.values().stream().map(r -> Map.of("code", r.code(), "name", r.name())).toList());
        return m;

    }

    public Map<String, Object> save(Long userId, boolean morning, boolean close, Object region) {

        planDao.upsertSetting(userId, morning ? "Y" : "N", close ? "Y" : "N", VoiceBriefRules.region(region).code());
        return settings(userId);

    }

    // ============================================
    // 지금 기준 문장 (알림을 눌렀을 때 · "지금 들어보기")
    // ============================================
    public Map<String, Object> brief(Long userId, String kind) {

        String nickname = planDao.selectNickname(userId);
        String text;
        String title;

        switch (kind == null ? "" : kind) {
            case "close" -> {
                title = "📈 장 마감 브리핑";
                String t = closeText(userId, nickname);
                text = t != null ? t : "관심 종목의 오늘 시세가 아직 없어요. 종목 화면에서 ☆를 눌러 관심 종목을 추가하면 장 마감 뒤에 알려드릴게요.";
            }
            case "plan" -> {
                title = "🗓️ 약속 알림";
                text = planService.speech(userId);
            }
            default -> {
                title = "🌤️ 아침 브리핑";
                Map<String, Object> s = planDao.selectSetting(userId);
                text = morningText(userId, nickname, VoiceBriefRules.region(s == null ? null : s.get("region")));
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("text", text);
        return m;

    }

    /** 날씨 (지역별 20분 기억). 못 받으면 null */
    public Weather weather(Region region) {

        CachedWeather c = weatherCache.get(region.code());
        if (c != null && Instant.now().isBefore(c.at().plusSeconds(WEATHER_CACHE_SECONDS))) return c.weather();

        try {
            Weather w = weatherClient.today(region.lat(), region.lon());
            weatherCache.put(region.code(), new CachedWeather(w, Instant.now()));
            return w;
        } catch (Exception e) {
            log.warn("날씨 조회 실패 ({}): {}", region.name(), e.getMessage());
            return c == null ? null : c.weather();   // 조금 지난 값이라도
        }

    }

    /** AI 챗봇 get_weather 도구용: 숫자 + 읽을 문장 */
    public Map<String, Object> weatherFor(Object regionText) {

        Region region = VoiceBriefRules.regionByName(regionText);
        Weather w = weather(region);
        if (w == null) throw new IllegalStateException("지금은 날씨 정보를 가져오지 못했어요.");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("region", region.name());
        m.put("date", LocalDate.now(KST).toString());
        m.put("sky", VoiceBriefRules.sky(w.code()));
        m.put("minTemp", w.min());
        m.put("maxTemp", w.max());
        m.put("nowTemp", w.now());
        m.put("rainProbability", w.rainProb());
        m.put("pm10", w.pm10());
        m.put("dust", VoiceBriefRules.dust(w.pm10()));
        m.put("summary", String.join(". ", VoiceBriefRules.weatherLines(region.name(), w)) + ".");
        m.put("supportedRegions", VoiceBriefRules.REGIONS.values().stream().map(Region::name).toList());
        m.put("source", "Open-Meteo 예보");
        return m;

    }

    private String morningText(Long userId, String nickname, Region region) {

        // 관심 종목의 최근 하루 뉴스 제목 한 개 (없거나 못 읽으면 빼고)
        Map<String, Object> news = null;
        try {
            news = planDao.selectWatchNews(userId);
        } catch (Exception e) {
            log.warn("브리핑 뉴스 조회 실패: {}", e.getMessage());
        }

        return VoiceBriefRules.morning(nickname, region.name(), weather(region), planService.todaySpeech(userId),
                news == null ? null : String.valueOf(news.get("stock")), news == null ? null : String.valueOf(news.get("title")));

    }

    // 관심 종목: 지금 가격(토스 현재가, 없으면 오늘 종가) vs 오늘 전 마지막 종가
    private String closeText(Long userId, String nickname) {

        LocalDate today = LocalDate.now(KST);
        List<Map<String, Object>> watch = planDao.selectWatchPrev(userId, today);
        if (watch.isEmpty()) return null;

        List<Long> ids = watch.stream().map(w -> ((Number) w.get("stockId")).longValue()).toList();
        Map<Long, MockTradeVO> live = Map.of();
        try {
            live = mockPriceService.getPrices(ids);
        } catch (Exception e) {
            log.warn("장 마감 브리핑 현재가 조회 실패: {}", e.getMessage());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> w : watch) {
            MockTradeVO p = live.get(((Number) w.get("stockId")).longValue());
            Object price = (p != null && "LIVE".equals(p.getPriceSource())) ? p.getPrice() : w.get("todayClose");   // 오늘 값이 없으면 그 종목은 빠짐
            Map<String, Object> r = new HashMap<>();
            r.put("name", w.get("name"));
            r.put("price", price);
            r.put("prev", w.get("prev"));
            rows.add(r);
        }

        return VoiceBriefRules.close(nickname, VoiceBriefRules.changes(rows));

    }

    // ============================================
    // 작업
    // ============================================
    public JobOutcome morningAll() {

        LocalDate today = LocalDate.now(KST);
        List<Map<String, Object>> users = planDao.selectMorningUsers();
        int sent = 0;
        int failed = 0;

        for (Map<String, Object> u : users) {
            try {
                Long userId = ((Number) u.get("userId")).longValue();
                String text = morningText(userId, u.get("nickname") == null ? null : String.valueOf(u.get("nickname")), VoiceBriefRules.region(u.get("region")));
                notificationService.notify(userId, "BRIEF", "🌤️ 아침 브리핑 · 눌러서 듣기", text, "/ai?brief=morning", "BRIEF:M:" + today);
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("아침 음성 브리핑 실패: {}", e.getMessage());
            }
        }

        return JobOutcome.of(users.size(), failed, "아침 브리핑 " + sent + "명 (켠 회원 " + users.size() + "명)");

    }

    public JobOutcome closeAll() {

        LocalDate today = LocalDate.now(KST);
        if (!MarketCalendar.isTradingDay(today)) return JobOutcome.of(0, 0, "오늘은 장이 열리지 않는 날");

        List<Map<String, Object>> users = planDao.selectCloseUsers();
        int sent = 0;
        int skipped = 0;
        int failed = 0;

        for (Map<String, Object> u : users) {
            try {
                Long userId = ((Number) u.get("userId")).longValue();
                String text = closeText(userId, u.get("nickname") == null ? null : String.valueOf(u.get("nickname")));
                if (text == null) {
                    skipped++;
                    continue;
                }
                notificationService.notify(userId, "BRIEF", "📈 장 마감 브리핑 · 눌러서 듣기", text, "/ai?brief=close", "BRIEF:C:" + today);
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("장 마감 음성 브리핑 실패: {}", e.getMessage());
            }
        }

        return JobOutcome.of(users.size(), failed, "장 마감 브리핑 " + sent + "명 · 관심 종목 시세 없음 " + skipped + "명 (켠 회원 " + users.size() + "명)");

    }
}
