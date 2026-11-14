package com.sklabs1037.sklabs1037.plan.service;

import com.sklabs1037.sklabs1037.client.WeatherClient;
import com.sklabs1037.sklabs1037.plan.service.VoiceBriefRules.Weather;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🔊 음성 브리핑 문장 테스트
 */
class VoiceBriefRulesTest {

    @Test
    void 지역은_모르면_서울() {

        assertEquals("서울", VoiceBriefRules.region(null).name());
        assertEquals("서울", VoiceBriefRules.region("BUSAN").name());
        assertEquals("수원", VoiceBriefRules.region("suwon").name());
        assertEquals("수원", VoiceBriefRules.regionByName("수원 날씨").name());
        assertEquals("의정부", VoiceBriefRules.regionByName("의정부").name());
        assertEquals("서울", VoiceBriefRules.regionByName("부산").name());
        assertEquals(10, VoiceBriefRules.REGIONS.size());

    }

    @Test
    void 맑은_날_아침_브리핑() {

        Weather w = new Weather(0, 3.4, 11.6, 0, 5.0, 25.0);

        String text = VoiceBriefRules.morning("서원", "서울", w, null);

        assertEquals("서원님, 좋은 아침이에요. 오늘 서울은 맑아요. 기온은 최저 3도, 최고 12도예요. 미세먼지는 좋음이에요.", text);

    }

    @Test
    void 비_오는_날은_우산과_출퇴근길을_말한다() {

        Weather w = new Weather(63, 8.0, 12.0, 80, 9.0, 90.0);

        String text = VoiceBriefRules.morning(null, "의정부", w, "오늘 약속이 있어요. 저녁 7시 회식.");

        assertTrue(text.startsWith("좋은 아침이에요. 오늘 의정부는 비가 와요."), text);
        assertTrue(text.contains("비 올 확률이 80퍼센트예요. 우산을 챙기세요. 비 때문에 출퇴근길이 평소보다 막힐 수 있어요."), text);
        assertTrue(text.contains("미세먼지는 나쁨이라 마스크를 챙기세요."), text);
        assertTrue(text.endsWith("오늘 약속이 있어요. 저녁 7시 회식."), text);

    }

    @Test
    void 눈_오는_날과_일교차() {

        List<String> snow = VoiceBriefRules.weatherLines("서울", new Weather(73, -5.0, 6.0, 70, null, null));

        assertTrue(snow.contains("눈 때문에 길이 미끄럽고 출퇴근길이 평소보다 막힐 수 있어요"), snow.toString());
        assertTrue(snow.contains("일교차가 커서 겉옷을 챙기세요"), snow.toString());
        assertTrue(snow.stream().noneMatch(l -> l.contains("미세먼지")));

    }

    @Test
    void 비_확률이_낮으면_막힌다는_말을_하지_않는다() {

        List<String> lines = VoiceBriefRules.weatherLines("서울", new Weather(3, 5.0, 10.0, 40, null, null));

        assertTrue(lines.contains("비 올 확률이 40퍼센트라 작은 우산이 있으면 좋아요"), lines.toString());
        assertTrue(lines.stream().noneMatch(l -> l.contains("막힐")));

    }

    @Test
    void 날씨를_못_받으면_그렇다고_말한다() {

        assertEquals("좋은 아침이에요. 지금은 날씨 정보를 가져오지 못했어요.", VoiceBriefRules.morning("", "서울", null, null));
        assertTrue(VoiceBriefRules.weatherLines("서울", new Weather(null, null, null, null, null, null)).isEmpty());

    }

    @Test
    void 날씨_응답을_읽는다() {

        Map<String, Object> forecast = Map.of(
                "current", Map.of("temperature_2m", 10.3),
                "daily", Map.of("weather_code", List.of(3), "temperature_2m_max", List.of(11.2), "temperature_2m_min", List.of(8.6), "precipitation_probability_max", List.of(35)));
        Map<String, Object> air = Map.of("current", Map.of("pm10", 41.0));

        Weather w = WeatherClient.parse(forecast, air);

        assertEquals(new Weather(3, 8.6, 11.2, 35, 10.3, 41.0), w);
        assertEquals(new Weather(null, null, null, null, null, null), WeatherClient.parse(Map.of(), null));
        assertEquals("보통", VoiceBriefRules.dust(w.pm10()));

    }

    @Test
    void 관심_종목_등락률() {

        List<Map<String, Object>> rows = List.of(
                row("삼성전자", 208500, 206500),
                row("SK하이닉스", 1233000, 1241000),
                row("시세없음", null, 1000),
                row("어제없음", 1000, null));

        List<Map<String, Object>> c = VoiceBriefRules.changes(rows);

        assertEquals(2, c.size());
        assertEquals(new BigDecimal("0.97"), c.get(0).get("rate"));
        assertEquals(new BigDecimal("-0.64"), c.get(1).get("rate"));

    }

    @Test
    void 장_마감_문장은_많이_움직인_순서() {

        List<Map<String, Object>> c = VoiceBriefRules.changes(List.of(
                row("삼성전자", 208500, 206500), row("SK하이닉스", 1233000, 1241000), row("삼성전자우", 171100, 167300), row("그대로", 1000, 1000)));

        String text = VoiceBriefRules.close("서원", c);

        assertEquals("서원님, 오늘 장이 끝났어요. 관심 종목 4개 중 2개가 오르고 1개가 내렸어요. 삼성전자우 2.27퍼센트 상승. 삼성전자 0.97퍼센트 상승. SK하이닉스 0.64퍼센트 하락. 그대로 변동 없음.", text);
        assertNull(VoiceBriefRules.close("서원", List.of()));

    }

    @Test
    void 관심_종목이_많으면_다섯_개까지만_읽는다() {

        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 1; i <= 7; i++) rows.add(row("종목" + i, 1000 + i * 10, 1000));

        String text = VoiceBriefRules.close(null, VoiceBriefRules.changes(rows));

        assertTrue(text.contains("종목7 7퍼센트 상승."), text);
        assertFalse(text.contains("종목2 "), text);
        assertTrue(text.endsWith("나머지는 종목 화면에서 볼 수 있어요."), text);

    }

    @Test
    void 조사는_받침에_따라() {

        assertEquals("은", VoiceBriefRules.josa("서울", "은", "는"));
        assertEquals("는", VoiceBriefRules.josa("의정부", "은", "는"));
        assertEquals("은", VoiceBriefRules.josa("고양", "은", "는"));

    }

    private static Map<String, Object> row(String name, Integer price, Integer prev) {

        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("price", price);
        m.put("prev", prev);
        return m;

    }
}
