package com.sklabs1037.sklabs1037.push.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📲 웹 푸시 암호화
 * - RFC 8291 5장 예시(정해진 키·salt)와 결과가 바이트까지 같아야 함
 * - VAPID JWT 서명은 공개키로 검증돼야 함 (DER → R||S 변환 확인)
 */
class WebPushCryptoTest {

    private static byte[] d(String s) {
        return WebPushCrypto.unb64(s);
    }

    @Test
    void RFC8291_예시와_같은_암호문() {

        KeyPair as = new KeyPair(
                WebPushCrypto.publicFromRaw(d("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8")),
                WebPushCrypto.privateFromRaw(d("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")));

        byte[] body = WebPushCrypto.encrypt(
                "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
                d("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
                d("BTBZMqHH6r4Tts7J_aSIgg"),
                as,
                d("DGv6ra1nlYgDCS1FRnbzlw"));

        assertEquals("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
                WebPushCrypto.b64(body));

    }

    @Test
    void 키_왕복() {

        KeyPair k = WebPushCrypto.generate();
        byte[] pub = WebPushCrypto.rawPublic(k.getPublic());
        byte[] pri = WebPushCrypto.rawPrivate(k.getPrivate());

        assertEquals(65, pub.length);
        assertEquals(32, pri.length);
        assertEquals(WebPushCrypto.b64(pub), WebPushCrypto.b64(WebPushCrypto.rawPublic(WebPushCrypto.publicFromRaw(pub))));

    }

    @Test
    void VAPID_JWT_서명_검증() throws Exception {

        KeyPair k = WebPushCrypto.generate();
        String jwt = WebPushCrypto.vapidJwt("https://fcm.googleapis.com", "https://sklabs1037.com", 1_900_000_000L, k.getPrivate());
        String[] p = jwt.split("\\.");
        assertEquals(3, p.length);

        String payload = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"aud\":\"https://fcm.googleapis.com\""));

        byte[] raw = Base64.getUrlDecoder().decode(p[2]);
        assertEquals(64, raw.length);

        // R||S → P1363 형식으로 JDK가 직접 검증
        Signature v = Signature.getInstance("SHA256withECDSAinP1363Format");
        v.initVerify(k.getPublic());
        v.update((p[0] + "." + p[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(v.verify(raw));

    }
}
