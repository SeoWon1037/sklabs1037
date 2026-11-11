package com.sklabs1037.sklabs1037.ops.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 🗄️ 자동 SQL 파일 다루기 (순수 계산)
 * - split: SQL 글을 문장 단위로 나눔 (주석 · 따옴표 · $$ 안의 ; 은 나누지 않음)
 * - lint: 서버가 스스로 실행해도 되는 파일인지 검사
 *     · 여러 번 실행해도 같은 결과여야 함 → CREATE TABLE / INDEX, ADD COLUMN 은 IF NOT EXISTS 필수
 *     · 지우는 문장은 안 됨 → DROP · TRUNCATE · DELETE 금지 (그런 일은 사람이 pgAdmin에서 직접)
 * - 파일 이름은 "날짜-순번-설명.sql" (2026-11-11-01-market-view-day.sql) → 이름순 = 실행 순서
 */
public final class AutoSql {

    public static final Pattern FILE_NAME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d{2}-[a-z0-9-]+\\.sql");

    private AutoSql() {
    }

    // 주석을 빼고 문장으로 나눔 (빈 문장은 버림)
    public static List<String> split(String sql) {

        List<String> out = new ArrayList<>();
        if (sql == null) return out;

        StringBuilder cur = new StringBuilder();
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            char next = i + 1 < n ? sql.charAt(i + 1) : '\0';

            if (c == '-' && next == '-') {                       // -- 한 줄 주석
                while (i < n && sql.charAt(i) != '\n') i++;
            } else if (c == '/' && next == '*') {                // /* 여러 줄 주석 */
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                cur.append(' ');
            } else if (c == '\'') {                              // '글' ('' 는 따옴표 하나)
                int j = i + 1;
                while (j < n) {
                    if (sql.charAt(j) == '\'') {
                        if (j + 1 < n && sql.charAt(j + 1) == '\'') j += 2;
                        else break;
                    } else j++;
                }
                j = Math.min(n - 1, j);
                cur.append(sql, i, j + 1);
                i = j + 1;
            } else if (c == '$') {                               // $$ … $$ 또는 $tag$ … $tag$
                int close = sql.indexOf('$', i + 1);
                String tag = close > 0 ? sql.substring(i, close + 1) : null;
                if (tag != null && tag.matches("\\$[A-Za-z_]*\\$")) {
                    int end = sql.indexOf(tag, close + 1);
                    int stop = end < 0 ? n : end + tag.length();
                    cur.append(sql, i, stop);
                    i = stop;
                } else {
                    cur.append(c);
                    i++;
                }
            } else if (c == ';') {
                add(out, cur);
                i++;
            } else {
                cur.append(c);
                i++;
            }
        }
        add(out, cur);
        return out;

    }

    private static void add(List<String> out, StringBuilder cur) {

        String s = cur.toString().strip();
        if (!s.isEmpty()) out.add(s);
        cur.setLength(0);

    }

    // 스스로 실행해도 되는지 → 안 되는 이유들 (비어 있으면 통과)
    public static List<String> lint(String fileName, String sql) {

        List<String> problems = new ArrayList<>();
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) problems.add("파일 이름은 2026-11-11-01-설명.sql 모양이어야 해요.");

        List<String> statements = split(sql);
        if (statements.isEmpty()) problems.add("실행할 문장이 없어요.");

        for (String st : statements) {
            String s = st.replaceAll("\\s+", " ").toUpperCase();
            String head = s.length() > 60 ? s.substring(0, 60) + "…" : s;

            if (s.matches("^(DROP|TRUNCATE|DELETE)\\b.*") || s.contains(" DROP COLUMN ") || s.contains(" DROP CONSTRAINT ")) {
                problems.add("지우는 문장은 자동으로 실행하지 않아요: " + head);
            } else if (s.matches("^CREATE TABLE (?!IF NOT EXISTS ).*")) {
                problems.add("CREATE TABLE 에 IF NOT EXISTS 가 없어요: " + head);
            } else if (s.matches("^CREATE (UNIQUE )?INDEX (?!IF NOT EXISTS ).*")) {
                problems.add("CREATE INDEX 에 IF NOT EXISTS 가 없어요: " + head);
            } else if (s.matches("^ALTER TABLE .*") && Pattern.compile("ADD COLUMN (?!IF NOT EXISTS )").matcher(s).find()) {
                problems.add("ADD COLUMN 에 IF NOT EXISTS 가 없어요: " + head);
            } else if (!s.matches("^(CREATE TABLE|CREATE (UNIQUE )?INDEX|ALTER TABLE|INSERT INTO|UPDATE|COMMENT ON) .*")) {
                problems.add("자동으로 실행할 수 없는 종류의 문장이에요: " + head);
            }
        }
        return problems;

    }

    // 파일 내용 지문 (실행한 뒤에 파일이 바뀌었는지 알아보기)
    public static String checksum(String sql) {

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((sql == null ? "" : sql.replace("\r\n", "\n")).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

    }
}
