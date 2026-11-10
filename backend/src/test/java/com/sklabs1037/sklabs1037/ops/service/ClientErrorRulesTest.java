package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.ops.service.ClientErrorRules.Report;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 🐞 화면 오류 보고 다듬기 (순수 계산)
class ClientErrorRulesTest {

    private static Map<String, Object> in(String kind, String message, String stack, String path) {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);
        m.put("message", message);
        m.put("stack", stack);
        m.put("path", path);
        return m;
    }

    private static final String STACK = "TypeError: Cannot read properties of undefined (reading 'map')\n"
            + "    at MarketPage (https://sklabs1037.com/assets/MarketPage-Ab3dE9xZ.js:1:2345)\n"
            + "    at div\n    at App (https://sklabs1037.com/assets/index-icFO7uJs.js:40:120)";

    @Test
    void 보고를_다듬어_저장할_모양으로() {

        Report r = ClientErrorRules.clean(in("render", "Cannot read properties of undefined (reading 'map')", STACK, "/market/37?q=아이폰#top"), "Mozilla/5.0 (iPhone)");
        assertEquals("RENDER", r.kind());
        assertEquals("/market/:id", r.path());
        assertEquals("Mozilla/5.0 (iPhone)", r.ua());
        assertEquals(32, r.sig().length());
        assertFalse(r.stack().contains("https://sklabs1037.com"));
        assertTrue(r.stack().contains("at MarketPage (/assets/MarketPage-Ab3dE9xZ.js:1:2345)"));

    }

    @Test
    void 빌드가_바뀌어도_줄_번호가_달라도_같은_오류는_같은_지문() {

        Report a = ClientErrorRules.clean(in("ERROR", "x is not a function", "TypeError: x is not a function\n at f (https://sklabs1037.com/assets/StockPage-DcWEycBT.js:12:345)", "/stock"), null);
        Report b = ClientErrorRules.clean(in("ERROR", "x is not a function", "TypeError: x is not a function\n at f (https://sklabs1037.com/assets/StockPage-Zz91kLmQ.js:19:7)", "/stock"), null);
        Report c = ClientErrorRules.clean(in("ERROR", "y is not a function", "TypeError: y is not a function\n at f (https://sklabs1037.com/assets/StockPage-DcWEycBT.js:12:345)", "/stock"), null);
        Report d = ClientErrorRules.clean(in("ERROR", "x is not a function", "TypeError: x is not a function\n at g (https://sklabs1037.com/assets/MockTradePage-DcWEycBT.js:12:345)", "/mock"), null);
        assertEquals(a.sig(), b.sig());
        assertNotEquals(a.sig(), c.sig());
        assertNotEquals(a.sig(), d.sig());

    }

    @Test
    void 종류가_다르면_다른_오류() {

        Report a = ClientErrorRules.clean(in("ERROR", "boom", null, "/"), null);
        Report b = ClientErrorRules.clean(in("PROMISE", "boom", null, "/"), null);
        assertNotEquals(a.sig(), b.sig());
        assertNull(a.stack());
        assertNull(a.ua());

    }

    @Test
    void 우리_코드_문제가_아닌_것은_받지_않는다() {

        assertNull(ClientErrorRules.clean(in("ERROR", "Script error.", null, "/"), null));
        assertNull(ClientErrorRules.clean(in("ERROR", "ResizeObserver loop limit exceeded", null, "/"), null));
        assertNull(ClientErrorRules.clean(in("PROMISE", "TypeError: Failed to fetch", null, "/"), null));
        assertNull(ClientErrorRules.clean(in("PROMISE", "Load failed", null, "/"), null));
        assertNull(ClientErrorRules.clean(in("ERROR", "x is undefined", "at a (chrome-extension://abcdef/content.js:1:1)", "/"), null));
        assertNull(ClientErrorRules.clean(in("ERROR", "   ", null, "/"), null));
        assertNull(ClientErrorRules.clean(in("ERROR", null, null, "/"), null));
        assertNull(ClientErrorRules.clean(null, null));

    }

    @Test
    void 화면_코드를_못_받은_것은_메시지가_fetch_실패여도_받는다() {

        Report r = ClientErrorRules.clean(in("CHUNK", "Failed to fetch dynamically imported module: https://sklabs1037.com/assets/MarketPage-Ab3dE9xZ.js", null, "/market"), null);
        assertNotNull(r);
        assertEquals("CHUNK", r.kind());
        // 빌드가 바뀌어도 같은 화면이면 같은 줄
        Report r2 = ClientErrorRules.clean(in("CHUNK", "Failed to fetch dynamically imported module: https://sklabs1037.com/assets/MarketPage-Zz91kLmQ.js", null, "/market"), null);
        assertEquals(r.sig(), r2.sig());

    }

    @Test
    void 모르는_종류는_ERROR로_길이는_자른다() {

        Report r = ClientErrorRules.clean(in("해킹", "가".repeat(1000), ("at f (a.js:1:1)\n").repeat(100), "/" + "a".repeat(500)), "U".repeat(500));
        assertEquals("ERROR", r.kind());
        assertEquals(300, r.message().length());
        assertEquals(12, r.stack().split("\n").length);
        assertEquals(120, r.path().length());
        assertEquals(200, r.ua().length());

    }

    @Test
    void 주소는_경로만_남긴다() {

        assertEquals("/market", ClientErrorRules.path("/market?q=비밀검색어"));
        assertEquals("/market/user/:id", ClientErrorRules.path("/market/user/11#x"));
        assertEquals("/market/:id/edit", ClientErrorRules.path("/market/3/edit"));
        assertEquals("/", ClientErrorRules.path("https://evil.com/x"));
        assertEquals("/", ClientErrorRules.path(null));

    }

    @Test
    void 줄바꿈_같은_제어문자는_메시지에서_지운다() {

        Report r = ClientErrorRules.clean(in("ERROR", "첫 줄\n둘째 줄\t끝", null, "/"), null);
        assertEquals("첫 줄 둘째 줄 끝", r.message());

    }
}
