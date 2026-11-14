package com.sklabs1037.sklabs1037.user.keep;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 🔑 로그인 유지 규칙 (순수 계산 → 테스트 가능)
 * - 표(token): 무작위 32바이트 → URL에 써도 되는 글자 43자. 브라우저 쿠키에만 있음
 * - DB에는 표의 SHA-256(16진수 64자)만 저장
 * - 쿠키: HttpOnly(스크립트가 못 읽음) · SameSite=Lax · https면 Secure · 30일
 */
public final class KeepLoginRules {

    private KeepLoginRules() {
    }

    public static final String COOKIE = "SKL_KEEP";
    public static final int DAYS = 30;
    public static final int MAX_AGE_SECONDS = DAYS * 24 * 60 * 60;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String newToken() {

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    }

    public static String hash(String token) {

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

    }

    /** 요청의 Cookie 머리글에서 우리 표 꺼내기 (없거나 모양이 이상하면 null) */
    public static String tokenFrom(String cookieHeader) {

        if (cookieHeader == null) return null;

        for (String part : cookieHeader.split(";")) {
            String p = part.strip();
            if (!p.startsWith(COOKIE + "=")) continue;
            String value = p.substring(COOKIE.length() + 1);
            return value.matches("[A-Za-z0-9_-]{43}") ? value : null;
        }
        return null;

    }

    /** Set-Cookie 값 (maxAge 0 = 지우기) */
    public static String cookie(String token, int maxAgeSeconds, boolean secure) {

        return COOKIE + "=" + (token == null ? "" : token)
                + "; Max-Age=" + maxAgeSeconds
                + "; Path=/; HttpOnly; SameSite=Lax"
                + (secure ? "; Secure" : "");

    }
}
