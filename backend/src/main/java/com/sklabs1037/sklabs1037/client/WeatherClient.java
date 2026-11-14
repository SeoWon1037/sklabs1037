package com.sklabs1037.sklabs1037.client;

import com.sklabs1037.sklabs1037.plan.service.VoiceBriefRules.Weather;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 🌤️ 날씨 (Open-Meteo, https://open-meteo.com) — 키 없이 쓰는 공개 API. 메인 화면 날씨 칸과 같은 곳
 * - 예보:   GET api.open-meteo.com/v1/forecast?latitude&longitude&current=temperature_2m
 *           &daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=Asia/Seoul&forecast_days=1
 * - 대기질: GET air-quality-api.open-meteo.com/v1/air-quality?latitude&longitude&current=pm10
 * - 대기질은 못 받아도 날씨만으로 답함. 응답에 없는 값은 null (문장에서 그 줄만 빠짐)
 */
@Slf4j
@Component
public class WeatherClient {

    private static final String FORECAST = "https://api.open-meteo.com/v1/forecast";
    private static final String AIR = "https://air-quality-api.open-meteo.com/v1/air-quality";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JsonMapper jsonMapper;

    public Weather today(double lat, double lon) throws Exception {

        String at = "?latitude=" + lat + "&longitude=" + lon + "&timezone=Asia%2FSeoul";

        Map<String, Object> forecast = get("forecast", FORECAST + at
                + "&current=temperature_2m&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=1");

        Map<String, Object> air = null;
        try {
            air = get("air", AIR + at + "&current=pm10");
        } catch (Exception e) {
            log.warn("대기질 조회 실패 (날씨만 사용): {}", e.getMessage());
        }

        return parse(forecast, air);

    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String action, String uri) throws Exception {

        HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("User-Agent", "SKLabs/1.0 (+https://sklabs1037.com)")
                .GET()
                .build();

        long start = ApiCallLog.start();
        HttpResponse<String> res;

        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            ApiCallLog.record("WEATHER", action, false, null, ApiCallLog.elapsedMs(start), RestClients.errorText(e));
            throw e;
        }

        boolean ok = res.statusCode() == 200;
        ApiCallLog.record("WEATHER", action, ok, res.statusCode(), ApiCallLog.elapsedMs(start), ok ? null : "HTTP " + res.statusCode());
        if (!ok) throw new IllegalStateException("날씨 API " + res.statusCode());

        return jsonMapper.readValue(res.body(), Map.class);

    }

    /** 응답 → 오늘 날씨 (순수 계산) */
    public static Weather parse(Map<String, Object> forecast, Map<String, Object> air) {

        Map<?, ?> daily = forecast == null ? null : forecast.get("daily") instanceof Map<?, ?> m ? m : null;
        Map<?, ?> current = forecast == null ? null : forecast.get("current") instanceof Map<?, ?> m ? m : null;
        Map<?, ?> airNow = air == null ? null : air.get("current") instanceof Map<?, ?> m ? m : null;

        Double code = first(daily, "weather_code");
        Double rain = first(daily, "precipitation_probability_max");

        return new Weather(
                code == null ? null : (int) Math.round(code),
                first(daily, "temperature_2m_min"),
                first(daily, "temperature_2m_max"),
                rain == null ? null : (int) Math.round(rain),
                num(current == null ? null : current.get("temperature_2m")),
                num(airNow == null ? null : airNow.get("pm10")));

    }

    // daily의 값은 날짜별 배열 → 첫 칸(오늘)
    private static Double first(Map<?, ?> daily, String key) {

        if (daily == null || !(daily.get(key) instanceof List<?> l) || l.isEmpty()) return null;
        return num(l.get(0));

    }

    private static Double num(Object v) {

        return v instanceof Number n ? n.doubleValue() : null;

    }
}
