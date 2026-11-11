package com.sklabs1037.sklabs1037.ops.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

// 🧪 자가 점검: "있어야 하는 표 · 칸" 목록과 판정 (순수 계산 + sql 폴더와 맞는지)
class SchemaExpectTest {

    @Test
    void 다_있으면_빠진_것이_없다() {

        Set<String> tables = new HashSet<>(SchemaExpect.TABLES.keySet());
        Set<String> columns = new HashSet<>(SchemaExpect.COLUMNS.keySet());
        assertTrue(SchemaExpect.missing(tables, columns).isEmpty());

    }

    @Test
    void 없는_표는_그_표를_만드는_파일로_알려준다() {

        Set<String> tables = new HashSet<>(SchemaExpect.TABLES.keySet());
        tables.remove("T_MARKET_OFFER");
        tables.remove("T_INVITE");
        Map<String, List<String>> missing = SchemaExpect.missing(tables, new HashSet<>(SchemaExpect.COLUMNS.keySet()));
        assertEquals(2, missing.size());
        assertEquals(List.of("T_INVITE"), missing.get("invite.sql"));
        assertEquals(List.of("T_MARKET_OFFER"), missing.get("market-offer.sql"));

    }

    @Test
    void 나중에_더한_칸이_없으면_그_파일로_알려준다() {

        Set<String> columns = new HashSet<>(SchemaExpect.COLUMNS.keySet());
        columns.remove("T_MARKET_ITEM.BUYER_ID");
        columns.remove("T_MARKET_ITEM.PREV_PRICE");
        Map<String, List<String>> missing = SchemaExpect.missing(new HashSet<>(SchemaExpect.TABLES.keySet()), columns);
        assertEquals(List.of("T_MARKET_ITEM.BUYER_ID", "T_MARKET_ITEM.PREV_PRICE"), missing.get("market-plus.sql"));
        assertEquals(1, missing.size());

    }

    @Test
    void 표가_통째로_없으면_칸까지_늘어놓지_않는다() {

        Set<String> tables = new HashSet<>(SchemaExpect.TABLES.keySet());
        tables.remove("T_MARKET_ITEM");
        Map<String, List<String>> missing = SchemaExpect.missing(tables, Set.of());
        assertEquals(List.of("T_MARKET_ITEM"), missing.get("market.sql"));
        assertNull(missing.get("market-plus.sql"));

    }

    @Test
    void 점검_한_줄_만들기() {

        assertEquals("OK", SelfCheckService.schemaLine(Map.of(), 62, 15).get("status"));
        Map<String, Object> fail = SelfCheckService.schemaLine(Map.of("invite.sql", List.of("T_INVITE")), 61, 15);
        assertEquals("FAIL", fail.get("status"));
        assertTrue(String.valueOf(fail.get("detail")).contains("invite.sql (T_INVITE)"));

    }

    @Test
    void 옛날_시세_점검은_5년_전까지_받아야_통과() {

        LocalDate today = LocalDate.parse("2026-11-10");
        Map<String, Object> ok = new HashMap<>(Map.of("points", 1400, "firstDate", "2021-03-02", "pages", 7));
        Map<String, Object> shortOne = new HashMap<>(Map.of("points", 200, "firstDate", "2026-01-15", "pages", 1));
        Map<String, Object> none = new HashMap<>(Map.of("points", 0, "pages", 0));
        assertEquals("OK", SelfCheckService.historyLine(ok, today).get("status"));
        assertEquals("WARN", SelfCheckService.historyLine(shortOne, today).get("status"));
        assertEquals("FAIL", SelfCheckService.historyLine(none, today).get("status"));

    }

    @Test
    void 자동_SQL_점검_한_줄() {

        Map<String, Object> notYet = Map.of("ran", false);
        assertEquals("WARN", SelfCheckService.autoSqlLine(notYet).get("status"));

        Map<String, Object> ok = new HashMap<>(Map.of("ran", true, "known", 3, "applied", List.of("2026-11-11-01-a.sql"), "changed", List.of()));
        assertEquals("OK", SelfCheckService.autoSqlLine(ok).get("status"));
        assertTrue(String.valueOf(SelfCheckService.autoSqlLine(ok).get("detail")).contains("2026-11-11-01-a.sql"));

        Map<String, Object> failed = new HashMap<>(ok);
        failed.put("failedFile", "2026-11-12-01-b.sql");
        failed.put("error", "permission denied for schema public");
        assertEquals("FAIL", SelfCheckService.autoSqlLine(failed).get("status"));
        assertTrue(String.valueOf(SelfCheckService.autoSqlLine(failed).get("detail")).contains("permission denied"));

        Map<String, Object> changed = new HashMap<>(Map.of("ran", true, "known", 3, "applied", List.of(), "changed", List.of("2026-11-11-01-a.sql")));
        assertEquals("WARN", SelfCheckService.autoSqlLine(changed).get("status"));

        Map<String, Object> off = new HashMap<>(Map.of("ran", true, "known", 0, "applied", List.of(), "changed", List.of(), "error", "꺼져 있어요 (APP_AUTO_SQL=false)"));
        assertEquals("WARN", SelfCheckService.autoSqlLine(off).get("status"));

    }

    // sql 폴더에 새 표 · 칸이 생겼는데 SchemaExpect에 안 넣었으면 여기서 알려줌
    @Test
    void 목록이_sql_폴더와_맞는다() throws IOException {

        Path dir = Stream.of(System.getProperty("sklabs.sql", "sql"), "sql", "backend/sql").map(Path::of).filter(Files::isDirectory).findFirst().orElse(null);
        if (dir == null) return;   // sql 폴더를 찾을 수 없는 환경에서는 건너뜀
        // 자동 SQL 폴더 (backend/sql 옆의 src/main/resources/db/auto)
        Path auto = dir.resolveSibling("src").resolve("main/resources/db/auto");

        Pattern create = Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Za-z_0-9]+)", Pattern.CASE_INSENSITIVE);
        Pattern add = Pattern.compile("ALTER\\s+TABLE\\s+([A-Za-z_0-9]+)\\s+ADD\\s+COLUMN\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Za-z_0-9]+)", Pattern.CASE_INSENSITIVE);

        Set<String> tables = new TreeSet<>();
        Set<String> columns = new TreeSet<>();
        for (Path folder : Files.isDirectory(auto) ? List.of(dir, auto) : List.of(dir)) {
            try (Stream<Path> files = Files.list(folder)) {
                for (Path f : files.filter((p) -> p.toString().endsWith(".sql")).toList()) {
                    String sql = Files.readString(f, StandardCharsets.UTF_8).replaceAll("--[^\\n]*", "");
                    Matcher m = create.matcher(sql);
                    while (m.find()) tables.add(m.group(1).toUpperCase());
                    m = add.matcher(sql);
                    while (m.find()) columns.add(m.group(1).toUpperCase() + "." + m.group(2).toUpperCase());
                }
            }
        }

        assertEquals(tables, new TreeSet<>(SchemaExpect.TABLES.keySet()));
        assertEquals(columns, new TreeSet<>(SchemaExpect.COLUMNS.keySet()));

    }
}
