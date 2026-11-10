package com.sklabs1037.sklabs1037.ops.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 🐞 화면 오류 보고 다듬기 (순수 계산)
 * - 누구나 보낼 수 있는 주소라서, 받은 글은 길이를 자르고 줄 수를 줄여 저장
 * - 우리 코드 문제가 아닌 것은 받지 않음: 브라우저 확장 프로그램, 다른 사이트 스크립트("Script error."),
 *   인터넷이 끊겨 생긴 실패, 브라우저가 스스로 정리하는 경고
 * - 지문(sig): 같은 오류를 한 줄로 묶기 위한 값 — 숫자 · 빌드마다 바뀌는 파일 이름을 지운 메시지 + 스택 첫 줄
 */
public final class ClientErrorRules {

    public static final Set<String> KINDS = Set.of("ERROR", "PROMISE", "RENDER", "CHUNK");
    public static final Set<String> STATUSES = Set.of("OPEN", "DONE", "IGNORED");

    static final int MESSAGE_MAX = 300;
    static final int STACK_MAX = 1500;
    static final int STACK_LINES = 12;

    // 우리 코드 문제가 아닌 것 (소문자로 비교)
    private static final List<String> NOISE = List.of(
            "script error", "resizeobserver loop", "chrome-extension://", "moz-extension://", "safari-extension://", "safari-web-extension://",
            "failed to fetch", "load failed", "networkerror", "network request failed", "the internet connection appears to be offline",
            "aborterror", "the operation was aborted", "the user aborted a request", "non-error promise rejection captured");

    private ClientErrorRules() {
    }

    public record Report(String sig, String kind, String message, String stack, String path, String ua) {
    }

    // 받은 보고 → 저장할 것 (받지 않을 것이면 null)
    public static Report clean(Map<String, Object> in, String userAgent) {

        if (in == null) return null;

        String kind = text(in.get("kind"), 10).toUpperCase();
        if (!KINDS.contains(kind)) kind = "ERROR";

        String message = text(in.get("message"), MESSAGE_MAX);
        String stack = stack(in.get("stack"));
        if (message.isEmpty()) return null;
        // 화면 코드를 못 받은 것(CHUNK)은 메시지가 "Failed to fetch …"라서 잡음 검사를 건너뜀 (배포 직후가 아닌데 계속 쌓이면 진짜 문제)
        if (!"CHUNK".equals(kind) && (noise(message) || noise(stack))) return null;

        String path = path(in.get("path"));
        String ua = text(userAgent, 200);

        return new Report(sig(kind, message, stack), kind, message, stack.isEmpty() ? null : stack, path, ua.isEmpty() ? null : ua);

    }

    static boolean noise(String s) {

        String t = s == null ? "" : s.toLowerCase();
        return NOISE.stream().anyMatch(t::contains);

    }

    // 주소는 경로만 (?뒤 · #뒤는 버림), 숫자로 된 조각은 :id 로 (/market/37 → /market/:id)
    static String path(Object raw) {

        String p = text(raw, 300);
        int cut = indexOfAny(p, '?', '#');
        if (cut >= 0) p = p.substring(0, cut);
        if (!p.startsWith("/")) return "/";
        p = p.replaceAll("/\\d+(?=/|$)", "/:id");
        return p.length() > 120 ? p.substring(0, 120) : p;

    }

    // 스택: 12줄까지, 줄마다 앞뒤 공백 정리, 우리 사이트 주소는 떼어냄
    static String stack(Object raw) {

        String s = raw == null ? "" : String.valueOf(raw).replace("\r", "");
        StringBuilder sb = new StringBuilder();
        int lines = 0;
        for (String line : s.split("\n")) {
            String t = line.strip().replaceAll("https?://[^/\\s)]+", "");
            if (t.isEmpty()) continue;
            if (lines++ >= STACK_LINES) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(t.length() > 200 ? t.substring(0, 200) : t);
        }
        return sb.length() > STACK_MAX ? sb.substring(0, STACK_MAX) : sb.toString();

    }

    // 같은 오류 묶기: 숫자 → #, 빌드마다 바뀌는 파일 이름(Name-AbC123.js) → Name-*.js
    static String normalize(String s) {

        return (s == null ? "" : s)
                .replaceAll("([A-Za-z]+)-[A-Za-z0-9_-]{6,12}\\.(js|css)", "$1-*.$2")
                .replaceAll("\\d+", "#")
                .replaceAll("\\s+", " ")
                .strip()
                .toLowerCase();

    }

    static String sig(String kind, String message, String stack) {

        String first = "";
        for (String line : (stack == null ? "" : stack).split("\n")) {
            if (line.contains("at ") || line.contains("@")) {   // 스택의 첫 "위치" 줄
                first = line;
                break;
            }
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((kind + "|" + normalize(message) + "|" + normalize(first)).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

    }

    // 한 줄 글: 제어문자를 공백으로, 앞뒤 정리, 길이 제한
    static String text(Object raw, int max) {

        String s = raw == null ? "" : String.valueOf(raw).replaceAll("[\\p{Cntrl}]+", " ").strip();
        return s.length() > max ? s.substring(0, max) : s;

    }

    private static int indexOfAny(String s, char a, char b) {

        int i = s.indexOf(a), j = s.indexOf(b);
        return i < 0 ? j : j < 0 ? i : Math.min(i, j);

    }
}
