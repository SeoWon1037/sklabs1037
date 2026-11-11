package com.sklabs1037.sklabs1037.ops.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 🗄️ 자동 SQL — 서버가 켜질 때 resources/db/auto/*.sql 중 아직 안 한 것을 이름순으로 한 번씩 실행
 *
 * 왜: 지금까지는 새 표가 필요할 때마다 pgAdmin에서 SQL 파일을 손으로 실행해야 했음 (빼먹으면 그 기능이 깨짐)
 *     → 앞으로 새로 만드는 SQL은 db/auto 에 넣고, 서버가 스스로 실행. (예전 backend/sql 파일들은 이미 실행한 것이라 건드리지 않음)
 *
 * 안전장치
 * - 파일마다 하나의 묶음(트랜잭션)으로 실행 → 중간에 실패하면 그 파일은 없던 일로, 뒤 파일은 실행하지 않음
 * - 실행 전에 AutoSql.lint: 여러 번 실행해도 되는 문장만(IF NOT EXISTS), 지우는 문장(DROP · TRUNCATE · DELETE)은 거부
 * - 실패해도 서버는 그대로 켜짐 (그 SQL이 필요한 기능만 안 됨) → 결과는 관리자 > 자가 점검 > 자동 SQL
 * - 한 번 실행한 파일은 T_SCHEMA_AUTO에 적어 다시 실행하지 않음. 실행한 뒤 내용이 바뀐 파일은 알려주기만 함
 * - 끄기: 서버 .env 에 APP_AUTO_SQL=false
 * - 🤖 AI 자동 수정은 이 폴더를 고칠 수 없음 (.github/ai-fix/check.sh)
 */
@Slf4j
@Service
public class AutoSqlRunner {

    private static final String PATTERN = "classpath:db/auto/*.sql";

    @Autowired
    private DataSource dataSource;

    @Value("${app.auto-sql:true}")
    private boolean enabled;

    // 마지막 실행 결과 (자가 점검이 읽음)
    private volatile Map<String, Object> last = Map.of("ran", false);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {

        try {
            last = run();
        } catch (Exception e) {
            log.error("자동 SQL을 시작하지 못함", e);
            last = result(0, List.of(), List.of(), null, "시작하지 못했어요: " + e.getMessage());
        }

    }

    public Map<String, Object> status() {

        return last;

    }

    Map<String, Object> run() throws Exception {

        if (!enabled) return result(0, List.of(), List.of(), null, "꺼져 있어요 (APP_AUTO_SQL=false)");

        Resource[] files = new PathMatchingResourcePatternResolver().getResources(PATTERN);
        Arrays.sort(files, Comparator.comparing((Resource r) -> String.valueOf(r.getFilename())));

        List<String> applied = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        int known = 0;

        try (Connection c = dataSource.getConnection()) {

            c.setAutoCommit(true);
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS T_SCHEMA_AUTO (FILE_NAME VARCHAR(120) PRIMARY KEY, CHECKSUM VARCHAR(20) NOT NULL, STATEMENTS INT NOT NULL, INS_DAY TIMESTAMPTZ NOT NULL DEFAULT NOW())");
            }

            Map<String, String> done = new HashMap<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT FILE_NAME, CHECKSUM FROM T_SCHEMA_AUTO")) {
                while (rs.next()) done.put(rs.getString(1), rs.getString(2));
            }

            for (Resource file : files) {
                String name = file.getFilename();
                String sql = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String sum = AutoSql.checksum(sql);

                if (done.containsKey(name)) {
                    known++;
                    if (!sum.equals(done.get(name))) changed.add(name);
                    continue;
                }

                List<String> problems = AutoSql.lint(name, sql);
                if (!problems.isEmpty()) {
                    log.error("자동 SQL 거부: {} → {}", name, problems);
                    return result(known, applied, changed, name, "실행하지 않았어요: " + problems.get(0));
                }

                List<String> statements = AutoSql.split(sql);
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    for (String s : statements) st.execute(s);
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO T_SCHEMA_AUTO (FILE_NAME, CHECKSUM, STATEMENTS) VALUES (?, ?, ?)")) {
                        ps.setString(1, name);
                        ps.setString(2, sum);
                        ps.setInt(3, statements.size());
                        ps.executeUpdate();
                    }
                    c.commit();
                    applied.add(name);
                    log.info("자동 SQL 실행: {} ({}문장)", name, statements.size());
                } catch (Exception e) {
                    c.rollback();
                    log.error("자동 SQL 실패: {}", name, e);
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().replaceAll("\\s+", " ");
                    return result(known, applied, changed, name, msg.length() > 240 ? msg.substring(0, 240) : msg);
                } finally {
                    c.setAutoCommit(true);
                }
            }
        }

        return result(known, applied, changed, null, null);

    }

    // { ran, known(전에 실행한 파일 수), applied(이번에 실행), changed(실행 뒤 내용이 바뀐 파일), failedFile, error }
    private static Map<String, Object> result(int known, List<String> applied, List<String> changed, String failedFile, String error) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ran", true);
        m.put("known", known);
        m.put("applied", applied);
        m.put("changed", changed);
        m.put("failedFile", failedFile);
        m.put("error", error);
        m.put("at", System.currentTimeMillis());
        return m;

    }
}
