package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.autofix.service.AutofixService;
import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.client.TossApiClient;
import com.sklabs1037.sklabs1037.ops.dao.SelfCheckDao;
import com.sklabs1037.sklabs1037.whatif.service.WhatIfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 🧪 자가 점검 — 배포한 뒤 "제대로 붙었는지"를 버튼 하나로 확인 (관리자 > 자가 점검)
 *
 * 왜: SQL은 pgAdmin에서 손으로 실행하고, 토스 · AI · GitHub 키는 서버 .env에 있어서
 *     하나가 빠져도 그 기능을 누를 때까지 모름. 그리고 개발 환경에서는 토스를 실제로 부를 수 없어 운영에서만 확인되는 것이 있음
 *
 * 점검 항목 (하나가 실패해도 나머지는 계속)
 *   1. DB 표 · 칸: sql 폴더가 만드는 것과 실제 DB 비교 → 아직 실행 안 한 SQL 파일 (SchemaExpect)
 *   1-2. 자동 SQL: 서버가 켜질 때 db/auto 의 SQL을 스스로 실행한 결과 (AutoSqlRunner)
 *   2. 서버 키: 토스 · AI · GitHub 토큰이 "있는지"만 (값은 절대 보여주지 않음)
 *   3. 옛날 시세 이어 받기: 삼성전자 5년 전까지 실제로 받아보기 (⏳ 그때 샀더라면이 쓰는 길)
 *   4. 계산: 삼성전자 1년 전 100만 원 계산이 되는지
 * 결과 한 줄: { key, name, status(OK / WARN / FAIL), detail, fix }
 */
@Service
public class SelfCheckService {

    static final String SAMPLE = "005930";   // 삼성전자
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private SelfCheckDao selfCheckDao;

    @Autowired
    private TossApiClient tossApiClient;

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private AutofixService autofixService;

    @Autowired
    private WhatIfService whatIfService;

    @Autowired
    private AutoSqlRunner autoSqlRunner;

    public Map<String, Object> run() {

        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(guard("schema", "DB 표 · 칸", this::schema));
        checks.add(guard("autosql", "자동 SQL (서버가 스스로 실행)", () -> autoSqlLine(autoSqlRunner.status())));
        checks.add(keys());
        checks.add(guard("history", "옛날 시세 이어 받기 (삼성전자 5년)", this::history));
        checks.add(guard("whatif", "그때 샀더라면 계산 (삼성전자 1년 전)", this::whatIf));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("checks", checks);
        out.put("ok", checks.stream().allMatch((c) -> "OK".equals(c.get("status"))));
        out.put("at", System.currentTimeMillis());
        return out;

    }

    // 점검 하나가 예외를 내도 FAIL 한 줄로 (나머지 점검은 계속)
    private static Map<String, Object> guard(String key, String name, Supplier<Map<String, Object>> check) {

        try {
            Map<String, Object> r = new LinkedHashMap<>(check.get());
            r.put("key", key);
            r.put("name", name);
            return r;
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return line(key, name, "FAIL", msg.length() > 300 ? msg.substring(0, 300) : msg, "오류 내용을 캡처해서 알려주세요.");
        }

    }

    // 1. DB 표 · 칸
    private Map<String, Object> schema() {

        Map<String, List<String>> missing = SchemaExpect.missing(new HashSet<>(selfCheckDao.selectTables()), new HashSet<>(selfCheckDao.selectColumns()));
        return schemaLine(missing, SchemaExpect.TABLES.size(), SchemaExpect.COLUMNS.size());

    }

    static Map<String, Object> schemaLine(Map<String, List<String>> missing, int tables, int columns) {

        if (missing.isEmpty()) return line(null, null, "OK", "표 " + tables + "개 · 나중에 더한 칸 " + columns + "개가 모두 있어요.", null);

        StringBuilder sb = new StringBuilder("아직 실행 안 한 SQL: ");
        missing.forEach((file, names) -> sb.append(file).append(" (").append(String.join(", ", names)).append(")  "));
        return line(null, null, "FAIL", sb.toString().strip(),
                "pgAdmin에서 backend/sql 폴더의 위 파일을 실행해주세요. (이름이 날짜로 시작하는 파일은 서버가 스스로 실행하는 것이니 아래 자동 SQL 줄을 봐주세요)");

    }

    // 1-2. 자동 SQL 결과 한 줄
    @SuppressWarnings("unchecked")
    static Map<String, Object> autoSqlLine(Map<String, Object> st) {

        if (!Boolean.TRUE.equals(st.get("ran"))) return line(null, null, "WARN", "서버가 켜진 뒤 아직 실행되지 않았어요. 잠시 뒤 다시 점검해주세요.", null);

        List<String> applied = (List<String>) st.get("applied");
        List<String> changed = (List<String>) st.get("changed");
        String summary = "전에 실행한 파일 " + st.get("known") + "개 · 이번에 켜지며 실행한 파일 " + applied.size() + "개" + (applied.isEmpty() ? "" : " (" + String.join(", ", applied) + ")");

        if (st.get("failedFile") != null) {
            return line(null, null, "FAIL", st.get("failedFile") + " 실패: " + st.get("error") + " · " + summary,
                    "이 줄을 캡처해서 알려주세요. 급하면 backend/src/main/resources/db/auto 의 그 파일을 pgAdmin에서 직접 실행해도 돼요. (다음에 켜질 때 서버가 한 번 더 실행해도 문제없게 만든 파일이에요)");
        }
        if (st.get("error") != null) return line(null, null, "WARN", String.valueOf(st.get("error")), null);
        if (!changed.isEmpty()) return line(null, null, "WARN", "실행한 뒤 내용이 바뀐 파일: " + String.join(", ", changed) + " (다시 실행하지 않아요) · " + summary, "바꿀 것이 있으면 새 파일로 추가해주세요.");
        return line(null, null, "OK", summary, null);

    }

    // 2. 서버 키 (있는지만)
    private Map<String, Object> keys() {

        List<String> missing = new ArrayList<>();
        if (!tossApiClient.isConfigured()) missing.add("토스증권(시세)");
        if (!claudeApiClient.isConfigured()) missing.add("AI");
        if (!autofixService.githubConfigured()) missing.add("GitHub 토큰(AI 자동 수정)");

        return missing.isEmpty()
                ? line("keys", "서버 키", "OK", "토스증권 · AI · GitHub 토큰이 모두 설정돼 있어요.", null)
                : line("keys", "서버 키", "WARN", "설정 안 된 것: " + String.join(", ", missing), "서버의 .env 파일에 넣고 다시 배포해주세요. (값은 채팅에 붙이지 마세요)");

    }

    // 3. 옛날 시세 이어 받기
    private Map<String, Object> history() {

        LocalDate today = LocalDate.now(KST);
        Map<String, Object> p = whatIfService.probe(SAMPLE, today.minusYears(5));
        return historyLine(p, today);

    }

    // 가장 이른 날이 4년 반보다 더 옛날이면 통과 (5년 전을 물었으니)
    static Map<String, Object> historyLine(Map<String, Object> p, LocalDate today) {

        int points = p.get("points") instanceof Number n ? n.intValue() : 0;
        String first = (String) p.get("firstDate");
        String detail = "거래일 " + points + "개 · 가장 이른 날 " + (first == null ? "없음" : first) + " · 토스에서 받은 쪽 수 " + p.get("pages");

        if (first == null) return line(null, null, "FAIL", "시세가 하나도 없어요. " + detail, "토스증권 키와 종목 수집(운영 > 작업 기록)을 확인해주세요.");
        if (LocalDate.parse(first).isAfter(today.minusMonths(54))) {
            return line(null, null, "WARN", detail + " → 5년 전까지 받지 못했어요. 그때 샀더라면은 이 날짜부터만 계산돼요.", "이 줄을 캡처해서 알려주세요. (토스 이어 받기 방식 확인 필요)");
        }
        return line(null, null, "OK", detail, null);

    }

    // 4. 계산
    private Map<String, Object> whatIf() {

        LocalDate from = LocalDate.now(KST).minusYears(1);
        Map<String, Object> r = whatIfService.calc(SAMPLE, from.toString(), 1_000_000L, false);
        String detail = r.get("buyDate") + " 종가 " + r.get("buyPrice") + " → " + r.get("nowDate") + " 종가 " + r.get("nowPrice") + " · 100만 원이 " + String.format("%,d", ((Number) r.get("value")).longValue()) + "원 (" + r.get("rate") + "%)";
        return Boolean.TRUE.equals(r.get("clamped"))
                ? line(null, null, "WARN", detail + " — 1년 전 기록이 없어 가장 이른 날부터 계산됐어요.", "옛날 시세 이어 받기 줄을 먼저 확인해주세요.")
                : line(null, null, "OK", detail, null);

    }

    static Map<String, Object> line(String key, String name, String status, String detail, String fix) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("name", name);
        m.put("status", status);
        m.put("detail", detail);
        m.put("fix", fix);
        return m;

    }
}
