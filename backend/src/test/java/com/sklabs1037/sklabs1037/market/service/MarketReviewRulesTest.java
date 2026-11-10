package com.sklabs1037.sklabs1037.market.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// ⭐ 거래 후기 · 매너 온도 · 🔔 키워드 · 📉 가격 내림 규칙 (DB 없이 순수 계산만)
class MarketReviewRulesTest {

    @Test
    void 매너_온도는_36점5도에서_시작한다() {

        assertEquals("36.5", String.valueOf(MarketRules.temperature(0, 0, 0)));

    }

    @Test
    void 좋아요는_올리고_별로예요는_더_크게_내린다() {

        assertEquals("37.5", String.valueOf(MarketRules.temperature(2, 0, 0)));
        assertEquals("36.6", String.valueOf(MarketRules.temperature(0, 1, 0)));
        assertEquals("35.5", String.valueOf(MarketRules.temperature(0, 0, 1)));
        assertEquals("36.0", String.valueOf(MarketRules.temperature(1, 0, 1)));

    }

    @Test
    void 매너_온도는_0도와_99점9도를_넘지_않는다() {

        assertEquals("99.9", String.valueOf(MarketRules.temperature(1000, 0, 0)));
        assertEquals("0.0", String.valueOf(MarketRules.temperature(0, 0, 1000)));

    }

    @Test
    void 후기에서_고른_말은_목록에_있는_것만_3개까지_남긴다() {

        String tags = MarketRules.reviewTags(3, List.of("응답이 빨라요", "내가 지어낸 말", "응답이 빨라요", "시간 약속을 잘 지켜요",
                "친절하고 매너가 좋아요", "상품 상태가 설명과 같아요"));
        assertEquals("응답이 빨라요,시간 약속을 잘 지켜요,친절하고 매너가 좋아요", tags);

    }

    @Test
    void 별로예요에는_아쉬운_점만_고를_수_있다() {

        assertNull(MarketRules.reviewTags(1, List.of("응답이 빨라요")));
        assertEquals("응답이 느려요", MarketRules.reviewTags(1, List.of("응답이 느려요")));
        assertNull(MarketRules.reviewTags(3, "목록이 아님"));

    }

    @Test
    void 후기_한마디는_300자까지_비면_null() {

        assertNull(MarketRules.reviewComment("   "));
        assertNull(MarketRules.reviewComment(null));
        assertEquals(300, MarketRules.reviewComment("가".repeat(500)).length());
        assertEquals("감사합니다", MarketRules.reviewComment("  감사합니다 "));

    }

    @Test
    void 알림_키워드는_2자에서_20자() {

        assertEquals("아이폰 15", MarketRules.alertKeyword("  아이폰   15 "));
        assertEquals("에어팟", MarketRules.alertKeyword("%에어팟%"));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.alertKeyword("a"));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.alertKeyword("%"));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.alertKeyword("가".repeat(21)));

    }

    @Test
    void 가격_내림은_전보다_쌀_때만() {

        assertTrue(MarketRules.priceDropped(10000L, 9000));
        assertTrue(MarketRules.priceDropped(10000L, 0));
        assertFalse(MarketRules.priceDropped(10000L, 10000));
        assertFalse(MarketRules.priceDropped(10000L, 12000));
        assertFalse(MarketRules.priceDropped(null, 100));

    }

    @Test
    void 가격_글자() {

        assertEquals("나눔", MarketService.priceText(0L));
        assertEquals("12,000원", MarketService.priceText(12000L));

    }
}
