package com.sklabs1037.sklabs1037.brain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 📥 사이트가 넣어 주는 노트 파일(resources/brain/notes/*.md) 읽기 — 순수 계산 (테스트: BrainImportRulesTest)
 *
 * 파일 모양 (맨 위 머리 + --- + 본문):
 *   key: code-brain
 *   title: Spring 코드 · brain 패키지
 *   topics: spring, brain
 *   links: code-ai, code-ops      (선택: 이 노트가 가리키는 다른 노트의 key → 그래프의 선)
 *   ---
 *   ## 요약 …
 *
 * - key  : 소문자 · 숫자 · - 만, 80자까지 (이름표 = 사용자마다 한 노트)
 * - kind : 적지 않으면 RAW (메모로 취급 → "내 메모에서 Spring 정리해줘"의 재료가 됨)
 * - 본문이 MAX_BODY(2만 자)를 넘으면 거절, 주제는 머리에 적은 것만 씀 (코드 속 #{userId} 같은 글자를 태그로 착각하지 않게)
 */
public final class BrainImportRules {

    private BrainImportRules() {
    }

    public record Note(String key, String kind, String title, List<String> topics, List<String> links, String body) {
    }

    private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,79}");

    public static Note parse(String fileName, String text) {

        if (text == null) throw new IllegalArgumentException(fileName + ": 내용이 없어요.");
        String t = text.replace("\r\n", "\n").replace("\r", "\n");
        if (t.startsWith("﻿")) t = t.substring(1);

        int cut = t.indexOf("\n---\n");
        if (cut < 0) throw new IllegalArgumentException(fileName + ": 머리와 본문 사이 --- 줄이 없어요.");

        String key = null;
        String kind = "RAW";
        String title = null;
        String topics = "";
        List<String> links = new ArrayList<>();
        for (String line : t.substring(0, cut).split("\n")) {
            int c = line.indexOf(':');
            if (c < 0) continue;
            String name = line.substring(0, c).trim();
            String value = line.substring(c + 1).trim();
            switch (name) {
                case "key" -> key = value;
                case "kind" -> kind = value.toUpperCase();
                case "title" -> title = value;
                case "topics" -> topics = value;
                case "links" -> {
                    for (String k : value.split(",")) {
                        String one = k.trim();
                        if (KEY.matcher(one).matches() && !links.contains(one)) links.add(one);
                    }
                }
                default -> { }
            }
        }

        if (key == null || !KEY.matcher(key).matches()) throw new IllegalArgumentException(fileName + ": key가 없거나 모양이 달라요.");
        if (!BrainRules.KINDS.contains(kind)) throw new IllegalArgumentException(fileName + ": kind는 RAW · WIKI · OUTPUT 중 하나여야 해요.");

        String body = BrainRules.body(t.substring(cut + 5).strip());
        if (body.isEmpty()) throw new IllegalArgumentException(fileName + ": 본문이 비어 있어요.");

        List<String> topicList = new ArrayList<>(BrainRules.topics(topics, ""));
        links.remove(key);
        return new Note(key, kind, BrainRules.title(title, body), topicList, List.copyOf(links), body);

    }

    // 본문 지문 — DB의 MD5(BODY)와 같은 값 (UTF-8)
    public static String hash(String body) {

        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }

    /**
     * 무엇을 할지
     * - NEW    : 처음 보는 이름표 → 넣기
     * - UPDATE : 넣어 준 적 있고, 그 뒤로 내가 안 고쳤고(지금 본문 지문 = 넣을 때 지문), 새 본문이 다름 → 바꾸기
     * - KEEP   : 같은 내용 · 내가 고친 노트 · 내가 지운 노트(받은 적은 있는데 노트가 없음)
     */
    public enum Action { NEW, UPDATE, KEEP }

    public static Action decide(boolean seen, String storedHash, String currentBodyHash, String newHash) {

        if (!seen) return Action.NEW;
        if (storedHash == null || currentBodyHash == null) return Action.KEEP;        // 지웠거나 예전 노트
        if (!storedHash.equals(currentBodyHash)) return Action.KEEP;                  // 내가 고침
        return storedHash.equals(newHash) ? Action.KEEP : Action.UPDATE;

    }

}
