package com.sklabs1037.sklabs1037.translate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 🌐 번역 규칙 (순수 계산)
 * - 번역은 AI가 하지만, 무엇을 받고 무엇을 내보낼지는 서버가 정함 ("AI는 정리, 판단은 서버 규칙")
 *   · 받는 글: 500자까지, 번역할 언어는 아래 목록에서만
 *   · AI 답: 길이 제한, 줄바꿈 정리, 원문 언어는 목록에 있을 때만 (없으면 "기타")
 */
public final class TranslateRules {

    public static final int TEXT_MAX = 500;
    static final int RESULT_MAX = 2000;
    static final int READING_MAX = 2000;

    // 언어 코드 → 한국어 이름 (화면의 언어 버튼 순서)
    public static final Map<String, String> LANGS = new LinkedHashMap<>();

    static {
        LANGS.put("ko", "한국어");
        LANGS.put("en", "영어");
        LANGS.put("ja", "일본어");
        LANGS.put("zh", "중국어");
        LANGS.put("es", "스페인어");
        LANGS.put("vi", "베트남어");
    }

    private TranslateRules() {
    }

    // 번역할 글 정리 (비었으면 예외, 500자에서 자름)
    public static String text(Object raw) {

        String t = raw == null ? "" : String.valueOf(raw).replace("\r\n", "\n").replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").replaceAll("\\n{3,}", "\n\n").strip();
        if (t.isEmpty()) throw new IllegalArgumentException("번역할 말을 입력해주세요.");
        return t.length() > TEXT_MAX ? t.substring(0, TEXT_MAX) : t;

    }

    // 번역할 언어 (목록에 없으면 예외)
    public static String target(Object raw) {

        String code = raw == null ? "" : String.valueOf(raw).strip().toLowerCase();
        if (!LANGS.containsKey(code)) throw new IllegalArgumentException("번역할 언어를 골라주세요.");
        return code;

    }

    // AI 답 정리 → { translated, reading(없으면 null), source(언어 코드 또는 other), sourceName }
    public static Map<String, Object> result(Map<?, ?> in, String target) {

        String translated = clip(in == null ? null : in.get("translated"), RESULT_MAX);
        if (translated.isEmpty()) throw new IllegalStateException("번역하지 못했어요. 잠시 후 다시 시도해주세요.");

        String reading = clip(in.get("reading"), READING_MAX);
        String source = String.valueOf(in.get("source_lang") == null ? "" : in.get("source_lang")).strip().toLowerCase();
        if (!LANGS.containsKey(source)) source = "other";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("translated", translated);
        // 한글 발음은 한국어로 번역했을 때는 필요 없음, 번역문과 똑같으면 뺌
        out.put("reading", "ko".equals(target) || reading.isEmpty() || reading.equals(translated) ? null : reading);
        out.put("source", source);
        out.put("sourceName", LANGS.getOrDefault(source, "기타"));
        out.put("target", target);
        out.put("targetName", LANGS.get(target));
        return out;

    }

    private static String clip(Object raw, int max) {

        String s = raw == null ? "" : String.valueOf(raw).replace("\r\n", "\n").strip();
        return s.length() > max ? s.substring(0, max) : s;

    }
}
