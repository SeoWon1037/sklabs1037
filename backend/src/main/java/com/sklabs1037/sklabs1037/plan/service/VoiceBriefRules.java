package com.sklabs1037.sklabs1037.plan.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 🔊 음성 브리핑 문장 만들기 (순수 계산 → 테스트 가능)
 * - AI를 쓰지 않음: 날씨 숫자 · 내 약속 · 관심 종목 등락을 정해진 문장에 넣기만 함 (지어낼 틈이 없음, 비용 0)
 * - 교통: 실시간 소통 정보는 아직 연결하지 않음. 비 · 눈 예보가 있을 때만 "평소보다 막힐 수 있어요"라고 말함 (예측이 아니라 주의)
 * - 모의투자 내용은 넣지 않음 (서원 요청)
 */
public final class VoiceBriefRules {

    private VoiceBriefRules() {
    }

    public record Region(String code, String name, double lat, double lon) {
    }

    /** 날씨 지역: 서울 · 경기권 (시청 근처 좌표) */
    public static final Map<String, Region> REGIONS = regions(
            new Region("SEOUL", "서울", 37.5665, 126.9780),
            new Region("INCHEON", "인천", 37.4563, 126.7052),
            new Region("SUWON", "수원", 37.2636, 127.0286),
            new Region("SEONGNAM", "성남", 37.4200, 127.1267),
            new Region("GOYANG", "고양", 37.6584, 126.8320),
            new Region("YONGIN", "용인", 37.2411, 127.1776),
            new Region("BUCHEON", "부천", 37.5034, 126.7660),
            new Region("ANYANG", "안양", 37.3943, 126.9568),
            new Region("HWASEONG", "화성", 37.1995, 126.8312),
            new Region("UIJEONGBU", "의정부", 37.7381, 127.0338));

    public static Region region(Object code) {

        Region r = code == null ? null : REGIONS.get(String.valueOf(code).strip().toUpperCase());
        return r == null ? REGIONS.get("SEOUL") : r;

    }

    /** 이름으로 찾기 ("수원 날씨" → 수원). 없으면 서울 */
    public static Region regionByName(Object text) {

        String s = text == null ? "" : String.valueOf(text);
        for (Region r : REGIONS.values()) if (s.contains(r.name())) return r;
        return region(text);

    }

    /** 오늘 날씨 (없는 값은 null) */
    public record Weather(Integer code, Double min, Double max, Integer rainProb, Double now, Double pm10) {
    }

    /** 날씨 코드(WMO) → 말 (화면 WeatherBox와 같은 기준) */
    public static String sky(Integer code) {

        if (code == null) return null;
        if (code == 0) return "맑아요";
        if (code <= 2) return "구름이 조금 있어요";
        if (code == 3) return "흐려요";
        if (code <= 48) return "안개가 껴요";
        if (code <= 57) return "이슬비가 와요";
        if (code <= 67) return "비가 와요";
        if (code <= 77) return "눈이 와요";
        if (code <= 82) return "소나기가 와요";
        if (code <= 86) return "눈 소나기가 와요";
        return "천둥 번개를 동반한 비가 와요";

    }

    static boolean wet(Weather w) {

        return w != null && ((w.code() != null && w.code() >= 51) || (w.rainProb() != null && w.rainProb() >= 60));

    }

    static boolean snowy(Weather w) {

        return w != null && w.code() != null && ((w.code() >= 71 && w.code() <= 77) || w.code() == 85 || w.code() == 86);

    }

    /** 미세먼지 등급 (환경부 PM10 기준: 30 · 80 · 150) */
    public static String dust(Double pm10) {

        if (pm10 == null) return null;
        return pm10 <= 30 ? "좋음" : pm10 <= 80 ? "보통" : pm10 <= 150 ? "나쁨" : "매우 나쁨";

    }

    /** 날씨 문장들 (날씨를 못 받았으면 빈 목록) */
    public static List<String> weatherLines(String regionName, Weather w) {

        List<String> lines = new ArrayList<>();
        if (w == null) return lines;

        String sky = sky(w.code());
        if (sky != null) lines.add("오늘 " + regionName + josa(regionName, "은", "는") + " " + sky);
        if (w.min() != null && w.max() != null) lines.add("기온은 최저 " + round(w.min()) + "도, 최고 " + round(w.max()) + "도예요");
        else if (w.now() != null) lines.add("지금 기온은 " + round(w.now()) + "도예요");

        if (snowy(w)) lines.add("눈 때문에 길이 미끄럽고 출퇴근길이 평소보다 막힐 수 있어요");
        else if (wet(w)) lines.add((w.rainProb() != null ? "비 올 확률이 " + w.rainProb() + "퍼센트예요. " : "") + "우산을 챙기세요. 비 때문에 출퇴근길이 평소보다 막힐 수 있어요");
        else if (w.rainProb() != null && w.rainProb() >= 30) lines.add("비 올 확률이 " + w.rainProb() + "퍼센트라 작은 우산이 있으면 좋아요");

        if (w.max() != null && w.min() != null && w.max() - w.min() >= 10) lines.add("일교차가 커서 겉옷을 챙기세요");

        String dust = dust(w.pm10());
        if (dust != null) lines.add("미세먼지는 " + dust + (dust.contains("나쁨") ? "이라 마스크를 챙기세요" : "이에요"));

        return lines;

    }

    /** 아침 브리핑 전체 문장 */
    public static String morning(String nickname, String regionName, Weather w, String planSpeech) {

        List<String> lines = new ArrayList<>();
        lines.add((nickname == null || nickname.isBlank() ? "" : nickname + "님, ") + "좋은 아침이에요");

        List<String> weather = weatherLines(regionName, w);
        if (weather.isEmpty()) lines.add("지금은 날씨 정보를 가져오지 못했어요");
        else lines.addAll(weather);

        String text = String.join(". ", lines) + ".";
        return planSpeech == null || planSpeech.isBlank() ? text : text + " " + planSpeech;

    }

    /** 관심 종목 한 줄: { name, price, prev } → { name, rate } (값이 없으면 빠짐) */
    public static List<Map<String, Object>> changes(List<Map<String, Object>> rows) {

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            BigDecimal price = dec(r.get("price"));
            BigDecimal prev = dec(r.get("prev"));
            if (price == null || prev == null || prev.signum() <= 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", String.valueOf(r.get("name")));
            m.put("rate", price.subtract(prev).multiply(BigDecimal.valueOf(100)).divide(prev, 2, RoundingMode.HALF_UP));
            out.add(m);
        }
        return out;

    }

    static final int MAX_CLOSE = 5;

    /** 장 마감 브리핑: changes()의 결과로 */
    public static String close(String nickname, List<Map<String, Object>> changes) {

        if (changes == null || changes.isEmpty()) return null;

        long up = changes.stream().filter(c -> ((BigDecimal) c.get("rate")).signum() > 0).count();
        long down = changes.stream().filter(c -> ((BigDecimal) c.get("rate")).signum() < 0).count();

        StringBuilder sb = new StringBuilder();
        sb.append(nickname == null || nickname.isBlank() ? "" : nickname + "님, ").append("오늘 장이 끝났어요. ");
        sb.append("관심 종목 ").append(changes.size()).append("개 중 ").append(up).append("개가 오르고 ").append(down).append("개가 내렸어요.");

        // 많이 움직인 순서로 5개까지
        List<Map<String, Object>> top = changes.stream()
                .sorted((a, b) -> ((BigDecimal) b.get("rate")).abs().compareTo(((BigDecimal) a.get("rate")).abs()))
                .limit(MAX_CLOSE).toList();

        for (Map<String, Object> c : top) sb.append(' ').append(c.get("name")).append(' ').append(rateText((BigDecimal) c.get("rate"))).append('.');
        if (changes.size() > top.size()) sb.append(" 나머지는 종목 화면에서 볼 수 있어요.");

        return sb.toString();

    }

    /** 0.97 → "0.97퍼센트 상승", -0.64 → "0.64퍼센트 하락", 0 → "변동 없음" */
    public static String rateText(BigDecimal rate) {

        if (rate.signum() == 0) return "변동 없음";
        return rate.abs().stripTrailingZeros().toPlainString() + "퍼센트 " + (rate.signum() > 0 ? "상승" : "하락");

    }

    /** 받침이 있으면 a, 없으면 b ("서울" → 은, "수원" → 은, "고양" → 은, "부천" → 은, "화성" → 은, "의정부" → 는) */
    static String josa(String word, String withFinal, String withoutFinal) {

        if (word == null || word.isEmpty()) return withFinal;
        char c = word.charAt(word.length() - 1);
        if (c < 0xAC00 || c > 0xD7A3) return withFinal;
        return (c - 0xAC00) % 28 != 0 ? withFinal : withoutFinal;

    }

    private static long round(double v) {

        return Math.round(v);

    }

    private static BigDecimal dec(Object v) {

        if (v == null) return null;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }

    }

    private static Map<String, Region> regions(Region... list) {

        Map<String, Region> m = new LinkedHashMap<>();
        for (Region r : list) m.put(r.code(), r);
        return java.util.Collections.unmodifiableMap(m);

    }
}
