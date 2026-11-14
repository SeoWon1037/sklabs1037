package com.sklabs1037.sklabs1037.translate;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 🌐 번역 규칙 (순수 계산)
class TranslateRulesTest {

    private static Map<String, Object> ai(String translated, String reading, String source) {
        Map<String, Object> m = new HashMap<>();
        m.put("translated", translated);
        m.put("reading", reading);
        m.put("source_lang", source);
        return m;
    }

    @Test
    void 번역할_글은_다듬고_500자에서_자른다() {

        assertEquals("화장실이 어디예요?", TranslateRules.text("  화장실이 어디예요?  "));
        assertEquals("첫 줄\n\n둘째 줄", TranslateRules.text("첫 줄\r\n\r\n\r\n\r\n둘째 줄"));
        assertEquals(500, TranslateRules.text("가".repeat(900)).length());

    }

    @Test
    void 빈_글은_받지_않는다() {

        assertThrows(IllegalArgumentException.class, () -> TranslateRules.text("   "));
        assertThrows(IllegalArgumentException.class, () -> TranslateRules.text(null));

    }

    @Test
    void 번역할_언어는_목록에서만() {

        assertEquals("en", TranslateRules.target(" EN "));
        assertEquals("ja", TranslateRules.target("ja"));
        assertThrows(IllegalArgumentException.class, () -> TranslateRules.target("klingon"));
        assertThrows(IllegalArgumentException.class, () -> TranslateRules.target(null));

    }

    @Test
    void AI_답을_정리한다() {

        Map<String, Object> r = TranslateRules.result(ai(" トイレはどこですか？ ", "토이레와 도코데스카", "KO"), "ja");
        assertEquals("トイレはどこですか？", r.get("translated"));
        assertEquals("토이레와 도코데스카", r.get("reading"));
        assertEquals("ko", r.get("source"));
        assertEquals("한국어", r.get("sourceName"));
        assertEquals("일본어", r.get("targetName"));

    }

    @Test
    void 한국어로_번역하면_발음은_뺀다() {

        assertNull(TranslateRules.result(ai("화장실이 어디예요?", "화장실이 어디예요", "en"), "ko").get("reading"));
        assertNull(TranslateRules.result(ai("Hello", "", "ko"), "en").get("reading"));
        assertNull(TranslateRules.result(ai("Hello", "Hello", "ko"), "en").get("reading"));
        assertNull(TranslateRules.result(ai("Hello", null, "ko"), "en").get("reading"));

    }

    @Test
    void 모르는_원문_언어는_기타() {

        Map<String, Object> r = TranslateRules.result(ai("안녕하세요", "", "fr"), "ko");
        assertEquals("other", r.get("source"));
        assertEquals("기타", r.get("sourceName"));
        assertEquals("other", TranslateRules.result(ai("안녕하세요", "", null), "ko").get("source"));

    }

    @Test
    void 번역문이_비면_실패_너무_길면_자른다() {

        assertThrows(IllegalStateException.class, () -> TranslateRules.result(ai("  ", "", "ko"), "en"));
        assertThrows(IllegalStateException.class, () -> TranslateRules.result(null, "en"));
        assertEquals(2000, String.valueOf(TranslateRules.result(ai("a".repeat(5000), "", "ko"), "en").get("translated")).length());

    }
}
