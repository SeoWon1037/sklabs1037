package com.sklabs1037.sklabs1037.user.keep;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🔑 로그인 유지 표 · 쿠키 규칙
 */
class KeepLoginRulesTest {

    @Test
    void 표는_43자이고_매번_다르다() {

        String a = KeepLoginRules.newToken();
        String b = KeepLoginRules.newToken();

        assertEquals(43, a.length());
        assertTrue(a.matches("[A-Za-z0-9_-]{43}"), a);
        assertNotEquals(a, b);

    }

    @Test
    void DB에는_SHA256_64자만() {

        // 알려진 값: SHA-256("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", KeepLoginRules.hash("abc"));
        assertEquals(64, KeepLoginRules.hash(KeepLoginRules.newToken()).length());

    }

    @Test
    void 쿠키_머리글에서_우리_표만_꺼낸다() {

        String token = KeepLoginRules.newToken();

        assertEquals(token, KeepLoginRules.tokenFrom("JSESSIONID=ABC123; SKL_KEEP=" + token + "; theme=dark"));
        assertEquals(token, KeepLoginRules.tokenFrom("SKL_KEEP=" + token));
        assertNull(KeepLoginRules.tokenFrom("JSESSIONID=ABC123"));
        assertNull(KeepLoginRules.tokenFrom(null));
        assertNull(KeepLoginRules.tokenFrom("XSKL_KEEP=" + token));

    }

    @Test
    void 모양이_이상한_표는_버린다() {

        assertNull(KeepLoginRules.tokenFrom("SKL_KEEP=short"));
        assertNull(KeepLoginRules.tokenFrom("SKL_KEEP="));
        assertNull(KeepLoginRules.tokenFrom("SKL_KEEP=" + "a".repeat(42) + "'"));
        assertNull(KeepLoginRules.tokenFrom("SKL_KEEP=" + "a".repeat(44)));

    }

    @Test
    void 쿠키는_스크립트가_못_읽고_30일() {

        String c = KeepLoginRules.cookie("TOKEN", KeepLoginRules.MAX_AGE_SECONDS, true);

        assertEquals("SKL_KEEP=TOKEN; Max-Age=2592000; Path=/; HttpOnly; SameSite=Lax; Secure", c);
        assertFalse(KeepLoginRules.cookie("TOKEN", 10, false).contains("Secure"));   // 로컬(http)에서는 Secure 없이

    }

    @Test
    void 지울_때는_빈_값과_0초() {

        assertEquals("SKL_KEEP=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax", KeepLoginRules.cookie(null, 0, false));

    }
}
