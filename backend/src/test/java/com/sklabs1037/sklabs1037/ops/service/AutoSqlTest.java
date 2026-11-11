package com.sklabs1037.sklabs1037.ops.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

// 🗄️ 자동 SQL: 문장 나누기 · 실행해도 되는지 검사 (순수 계산) + db/auto 폴더의 파일이 모두 통과하는지
class AutoSqlTest {

    @Test
    void 세미콜론으로_문장을_나눈다() {

        List<String> s = AutoSql.split("CREATE TABLE IF NOT EXISTS A (ID INT);\n\nCREATE INDEX IF NOT EXISTS IX ON A (ID);\n");
        assertEquals(2, s.size());
        assertEquals("CREATE TABLE IF NOT EXISTS A (ID INT)", s.get(0));
        assertEquals("CREATE INDEX IF NOT EXISTS IX ON A (ID)", s.get(1));

    }

    @Test
    void 주석은_빼고_주석_속_세미콜론은_나누지_않는다() {

        List<String> s = AutoSql.split("-- 설명; 여기 세미콜론\nCREATE TABLE IF NOT EXISTS A (\n  ID INT -- 번호; 기본키\n);\n/* 여러 줄;\n 주석 */\nALTER TABLE A ADD COLUMN IF NOT EXISTS B INT;");
        assertEquals(2, s.size());
        assertFalse(s.get(0).contains("번호"));
        assertTrue(s.get(1).startsWith("ALTER TABLE A"));

    }

    @Test
    void 따옴표와_달러_안의_세미콜론은_나누지_않는다() {

        List<String> s = AutoSql.split("INSERT INTO A (T) VALUES ('가;나 -- 주석 아님 ''따옴표''');\nUPDATE A SET T = $$x; y$$ WHERE ID = 1;\nUPDATE A SET T = $q$a;b$q$");
        assertEquals(3, s.size());
        assertTrue(s.get(0).endsWith("'가;나 -- 주석 아님 ''따옴표''')"));
        assertTrue(s.get(1).contains("$$x; y$$"));
        assertTrue(s.get(2).contains("$q$a;b$q$"));

    }

    @Test
    void 빈_글과_주석만_있는_글은_문장이_없다() {

        assertTrue(AutoSql.split(null).isEmpty());
        assertTrue(AutoSql.split("  \n ; ;\n-- 주석만\n").isEmpty());
        assertEquals(List.of("SELECT 1"), AutoSql.split("SELECT 1"));   // 끝에 ; 가 없어도

    }

    @Test
    void 여러_번_실행해도_되는_문장만_통과() {

        String ok = "CREATE TABLE IF NOT EXISTS A (ID INT);\nCREATE UNIQUE INDEX IF NOT EXISTS UX ON A (ID);\nALTER TABLE A ADD COLUMN IF NOT EXISTS B INT;";
        assertTrue(AutoSql.lint("2026-11-11-01-a.sql", ok).isEmpty());
        assertEquals(1, AutoSql.lint("2026-11-11-01-a.sql", "CREATE TABLE A (ID INT);").size());
        assertEquals(1, AutoSql.lint("2026-11-11-01-a.sql", "create index ix on a (id);").size());
        assertEquals(1, AutoSql.lint("2026-11-11-01-a.sql", "ALTER TABLE A ADD COLUMN B INT;").size());

    }

    @Test
    void 지우는_문장과_모르는_문장은_거부() {

        assertEquals(1, AutoSql.lint("2026-11-11-01-a.sql", "DROP TABLE T_USER;").size());
        assertEquals(1, AutoSql.lint("2026-11-11-01-a.sql", "truncate t_user;").size());
        assertEquals(1, AutoSql.lint("2026-11-11-01-a.sql", "DELETE FROM T_USER;").size());
        assertEquals(1, AutoSql.lint("2026-11-11-01-a.sql", "ALTER TABLE T_USER DROP COLUMN EMAIL;").size());
        assertEquals(1, AutoSql.lint("2026-11-11-01-a.sql", "GRANT ALL ON T_USER TO PUBLIC;").size());
        assertEquals(1, AutoSql.lint("2026-11-11-01-a.sql", "-- 주석만").size());

    }

    @Test
    void 파일_이름은_날짜_순번_설명() {

        assertTrue(AutoSql.lint("2026-11-11-01-market-view-day.sql", "CREATE TABLE IF NOT EXISTS A (ID INT);").isEmpty());
        assertEquals(1, AutoSql.lint("market.sql", "CREATE TABLE IF NOT EXISTS A (ID INT);").size());
        assertEquals(1, AutoSql.lint("2026-11-11-market.sql", "CREATE TABLE IF NOT EXISTS A (ID INT);").size());
        assertEquals(1, AutoSql.lint(null, "CREATE TABLE IF NOT EXISTS A (ID INT);").size());

    }

    @Test
    void 지문은_내용이_같으면_같고_줄바꿈_방식은_상관없다() {

        assertEquals(AutoSql.checksum("A;\nB;"), AutoSql.checksum("A;\r\nB;"));
        assertNotEquals(AutoSql.checksum("A;"), AutoSql.checksum("B;"));
        assertEquals(16, AutoSql.checksum("A;").length());

    }

    // db/auto 에 넣은 파일은 모두 lint를 통과해야 함 (통과 못 하면 서버가 실행을 거부하므로 여기서 먼저 알려줌)
    @Test
    void 자동_SQL_폴더의_파일이_모두_통과한다() throws IOException {

        Path dir = Stream.of(System.getProperty("sklabs.auto", "src/main/resources/db/auto"), "backend/src/main/resources/db/auto").map(Path::of).filter(Files::isDirectory).findFirst().orElse(null);
        if (dir == null) return;

        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter((p) -> p.toString().endsWith(".sql")).sorted().toList()) {
                List<String> problems = AutoSql.lint(f.getFileName().toString(), Files.readString(f, StandardCharsets.UTF_8));
                assertTrue(problems.isEmpty(), f.getFileName() + " → " + problems);
            }
        }

    }
}
