package com.sklabs1037.sklabs1037.me.service;

import java.security.SecureRandom;

/**
 * 공유 링크용 짧은 코드 (📸 공유 카드 /s/코드, ⚔️ 대결 /duel/코드)
 * - 헷갈리는 글자(0 O 1 l I)는 뺌, 10자면 추측으로 찾기 어려움
 */
public final class Codes {

    private static final String CHARS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Codes() {
    }

    public static String random(int length) {

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        return sb.toString();

    }
}
