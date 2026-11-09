package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.ops.service.AiUsageService.Prices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 💸 AI 비용 안전장치: 비용 계산 · 기능 이름
 */
class AiUsageServiceTest {

    private static final Prices HAIKU = new Prices(0.10, 0.50, 0.125, 0.01, 100_000, 5.0);

    @Test
    void 토큰_수로_비용_계산() {

        // 입력 2,000 · 출력 500 → 0.0002 + 0.00025 = 0.00045달러
        assertTrue(Math.abs(AiUsageService.cost(2000, 500, 0, 0, HAIKU) - 0.00045) < 1e-12);
        // 캐시 읽기 1만 · 캐시 쓰기 1만 → 0.0001 + 0.00125
        assertTrue(Math.abs(AiUsageService.cost(0, 0, 10_000, 10_000, HAIKU) - 0.00135) < 1e-12);

    }

    @Test
    void 입력이_10만_토큰을_넘으면_5배() {

        double normal = AiUsageService.cost(100_000, 1000, 0, 0, HAIKU);
        double longer = AiUsageService.cost(100_001, 1000, 0, 0, HAIKU);
        assertTrue(longer > normal * 4.9, normal + " → " + longer);

    }

    @Test
    void 기능_이름은_클래스_이름에서() {

        assertEquals("AdCopy", AiUsageService.shortName("com.sklabs1037.sklabs1037.beauty.service.AdCopyService"));
        assertEquals("StockCollect", AiUsageService.shortName("com.sklabs1037.sklabs1037.stock.service.StockCollectServiceImpl"));
        assertEquals("Inquiry", AiUsageService.shortName("com.sklabs1037.sklabs1037.support.service.InquiryService$1"));
        assertEquals("NewsSummary", AiUsageService.shortName("x.NewsSummaryScheduler"));

    }
}
