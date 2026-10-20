package com.sklabs1037.sklabs1037.couple.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📸 기념일 칸 · 그림 파일 확인
 */
class CouplePhotoRulesTest {

    @Test
    void 지난_기념일만_열림() {

        LocalDate start = LocalDate.parse("2026-04-04");

        assertTrue(CouplePhotoService.milestones(start, LocalDate.parse("2026-07-11")).isEmpty());   // 99일째

        List<Map<String, Object>> ms = CouplePhotoService.milestones(start, LocalDate.parse("2027-04-04"));
        assertEquals(List.of("100일", "200일", "300일", "1주년"), ms.stream().map(m -> m.get("label")).toList());
        assertEquals("2026-07-12", ms.get(0).get("date"));

    }

    @Test
    void 진짜_그림만() {

        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] webp = "RIFF0000WEBPVP8 ".getBytes();
        byte[] text = "<script>alert</script>".getBytes();

        assertEquals("image/jpeg", CouplePhotoService.imageType(jpeg));
        assertEquals("image/webp", CouplePhotoService.imageType(webp));
        assertNull(CouplePhotoService.imageType(text));

        String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
        assertTrue(java.util.Arrays.equals(jpeg, CouplePhotoService.decode(dataUrl)));
        assertThrows(IllegalArgumentException.class, () -> CouplePhotoService.decode("data:image/jpeg;base64,@@@"));

    }
}
