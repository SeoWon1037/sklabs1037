package com.sklabs1037.sklabs1037.ops.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🟢 서비스 상태: 시세가 제때 들어왔는지
 */
class StatusServiceTest {

    @Test
    void 장_마감_전에는_전_거래일_시세면_정상() {

        // 2026-11-09(월) 10:00 → 기대하는 날 = 11/6(금)
        LocalDateTime mon10 = LocalDateTime.of(2026, 11, 9, 10, 0);
        assertEquals("OK", StatusService.stockStatus(LocalDate.of(2026, 11, 6), mon10));
        assertEquals("WARN", StatusService.stockStatus(LocalDate.of(2026, 11, 5), mon10));
        assertEquals("DOWN", StatusService.stockStatus(LocalDate.of(2026, 11, 4), mon10));

    }

    @Test
    void 장_마감_뒤에는_오늘_시세() {

        LocalDateTime mon17 = LocalDateTime.of(2026, 11, 9, 17, 0);
        assertEquals("OK", StatusService.stockStatus(LocalDate.of(2026, 11, 9), mon17));
        assertEquals("WARN", StatusService.stockStatus(LocalDate.of(2026, 11, 6), mon17));

    }

    @Test
    void 휴장일_다음날은_휴장_전_시세면_정상() {

        // 2026-12-25(금) 휴장 → 12/28(월) 10:00 기대하는 날 = 12/24(목)
        assertEquals("OK", StatusService.stockStatus(LocalDate.of(2026, 12, 24), LocalDateTime.of(2026, 12, 28, 10, 0)));

    }

    @Test
    void 전체_상태는_가장_나쁜_것() {

        assertEquals("WARN", StatusService.overall(List.of(Map.of("status", "OK"), Map.of("status", "WARN"))));
        assertEquals("DOWN", StatusService.overall(List.of(Map.of("status", "DOWN"), Map.of("status", "WARN"))));
        assertEquals("OK", StatusService.overall(List.of(Map.of("status", "OK"))));

    }
}
