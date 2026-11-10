package com.sklabs1037.sklabs1037.market.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// 💰 가격 제안 · 📊 시세 낱말 · 📍 지역 규칙 (DB 없이 순수 계산만)
class MarketOfferRulesTest {

    @Test
    void 제안은_판매가보다_싸고_절반_이상() {

        assertEquals(80_000L, MarketRules.offerPrice(100_000L, 80_000));
        assertEquals(50_000L, MarketRules.offerPrice(100_000L, "50,000원"));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.offerPrice(100_000L, 100_000));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.offerPrice(100_000L, 120_000));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.offerPrice(100_000L, 49_900));

    }

    @Test
    void 제안_가격은_100원_단위로_내린다() {

        assertEquals(79_900L, MarketRules.offerPrice(100_000L, 79_999));

    }

    @Test
    void 나눔이나_이상한_값에는_제안할_수_없다() {

        assertThrows(IllegalArgumentException.class, () -> MarketRules.offerPrice(0L, 100));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.offerPrice(null, 100));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.offerPrice(100_000L, "싸게 주세요"));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.offerPrice(100_000L, -5));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.offerPrice(100L, 50));   // 100원 단위로 내리면 0원

    }

    @Test
    void 시세_낱말은_제목의_첫_낱말() {

        assertEquals("아이폰", MarketRules.guideWord("아이폰 15 프로 256GB"));
        assertEquals("15", MarketRules.guideWord("★ 15 프로"));
        assertEquals("에어팟", MarketRules.guideWord("[급처] a 에어팟"));   // "급처" 같은 꾸밈말과 한 글자 낱말은 건너뜀

    }

    @Test
    void 시세_낱말이_없으면_null() {

        assertNull(MarketRules.guideWord("a b ★"));
        assertNull(MarketRules.guideWord(null));

    }

    @Test
    void 지역_필터_정리() {

        assertEquals("서울 강남구", MarketRules.region("  서울   강남구 "));
        assertNull(MarketRules.region("%"));
        assertNull(MarketRules.region(null));
        assertEquals(20, MarketRules.region("가".repeat(40)).length());

    }
}
