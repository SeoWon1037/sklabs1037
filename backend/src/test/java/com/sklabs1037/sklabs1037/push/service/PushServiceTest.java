package com.sklabs1037.sklabs1037.push.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📲 푸시 구독 검사
 * - 브라우저 푸시 서비스 주소(https)만 받음 → 서버가 아무 주소로나 요청을 보내지 않게
 */
class PushServiceTest {

    @Test
    void 푸시_서비스_주소만_허용() {

        assertNotNull(PushService.validEndpoint("https://fcm.googleapis.com/fcm/send/abc:def"));
        assertNotNull(PushService.validEndpoint("https://web.push.apple.com/QGuQyavXu"));
        assertNotNull(PushService.validEndpoint("https://updates.push.services.mozilla.com/wpush/v2/gAAA"));
        assertNotNull(PushService.validEndpoint("https://wns2-pn1p.notify.windows.com/w/?token=x"));

        assertThrows(IllegalArgumentException.class, () -> PushService.validEndpoint("http://fcm.googleapis.com/x"));
        assertThrows(IllegalArgumentException.class, () -> PushService.validEndpoint("https://evil.com/fcm.googleapis.com"));
        assertThrows(IllegalArgumentException.class, () -> PushService.validEndpoint("https://fcm.googleapis.com.evil.com/x"));
        assertThrows(IllegalArgumentException.class, () -> PushService.validEndpoint("https://169.254.169.254/latest"));
        assertThrows(IllegalArgumentException.class, () -> PushService.validEndpoint(null));

    }

    @Test
    void Topic은_영문_숫자만_32자() {

        assertEquals("HEART", PushService.topic("HEART"));
        assertEquals("sklabs", PushService.topic("💗"));
        assertEquals(32, PushService.topic("A".repeat(40)).length());

    }

    @Test
    void 기기_이름() {

        assertEquals("Android · Chrome", PushService.device("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36"));
        assertEquals("iPhone · Safari", PushService.device("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1"));
        assertEquals("Windows · Edge", PushService.device("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130.0 Safari/537.36 Edg/130.0"));

    }
}
