package com.sklabs1037.sklabs1037.ops.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 💾 DB 백업 상태 판정
 */
class BackupServiceTest {

    private static final long H = 3600_000L;
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void 하루_안에_성공한_백업이_있으면_OK() {

        assertEquals("OK", BackupService.status(NOW - 3 * H, true, NOW - 3 * H, NOW));
        assertEquals("STALE", BackupService.status(NOW - 27 * H, true, NOW - 27 * H, NOW));
        assertEquals("NONE", BackupService.status(null, null, null, NOW));

    }

    @Test
    void 마지막_시도가_실패면_FAILED() {

        assertEquals("FAILED", BackupService.status(NOW - 25 * H, false, NOW - H, NOW));
        assertEquals("FAILED", BackupService.status(null, false, NOW - H, NOW));
        // 실패 뒤 다시 성공했으면 OK
        assertEquals("OK", BackupService.status(NOW - H, false, NOW - 2 * H, NOW));

    }
}
