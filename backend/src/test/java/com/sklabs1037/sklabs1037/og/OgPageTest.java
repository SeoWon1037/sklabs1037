package com.sklabs1037.sklabs1037.og;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// 🔗 공유 링크 미리보기 HTML (순수 계산)
class OgPageTest {

    private static final String SITE = "https://sklabs1037.com";

    @Test
    void 제목_설명_그림_주소가_들어간다() {

        String html = OgPage.html(SITE, "/market/3", "아이폰 15 · 850,000원", "서울 강남구 · 깨끗해요", "/api/market/photos/7");
        assertTrue(html.contains("<meta property=\"og:title\" content=\"아이폰 15 · 850,000원\">"));
        assertTrue(html.contains("<meta property=\"og:description\" content=\"서울 강남구 · 깨끗해요\">"));
        assertTrue(html.contains("<meta property=\"og:image\" content=\"https://sklabs1037.com/api/market/photos/7\">"));
        assertTrue(html.contains("<meta property=\"og:url\" content=\"https://sklabs1037.com/market/3\">"));
        assertTrue(html.contains("location.replace(\"/market/3\")"));
        assertTrue(html.contains("content=\"0;url=/market/3\""));

    }

    @Test
    void 그림이_없으면_기본_그림() {

        assertTrue(OgPage.html(SITE + "/", "/whatif", "t", "d", null).contains("content=\"https://sklabs1037.com/og-image.png\""));
        assertTrue(OgPage.html(SITE, "/whatif", "t", "d", " ").contains("content=\"https://sklabs1037.com/og-image.png\""));

    }

    @Test
    void 사용자가_쓴_글의_태그와_따옴표는_글자로_바뀐다() {

        String html = OgPage.html(SITE, "/market/3", "<script>alert(1)</script>\" onload=\"x", "a'b&c", null);
        assertFalse(html.contains("<script>alert(1)"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;&quot; onload=&quot;x"));
        assertTrue(html.contains("a&#39;b&amp;c"));

    }

    @Test
    void 우리_사이트_밖으로는_보내지_않는다() {

        assertEquals("/", OgPage.safePath("//evil.com"));
        assertEquals("/", OgPage.safePath("https://evil.com"));
        assertEquals("/", OgPage.safePath("/\\evil.com"));
        assertEquals("/", OgPage.safePath("/a\nb"));
        assertEquals("/", OgPage.safePath(null));
        assertEquals("/whatif?s=005930&d=2024-11-08&a=1000000", OgPage.safePath("/whatif?s=005930&d=2024-11-08&a=1000000"));

    }

    @Test
    void 주소의_앰퍼샌드는_속성에서는_이스케이프_스크립트에서는_유니코드() {

        String html = OgPage.html(SITE, "/whatif?s=005930&d=2024-11-08", "t", "d", null);
        assertTrue(html.contains("content=\"0;url=/whatif?s=005930&amp;d=2024-11-08\""));
        assertTrue(html.contains("location.replace(\"/whatif?s=005930\\u0026d=2024-11-08\")"));

    }

    @Test
    void 스크립트_문자열은_닫는_태그로_끊기지_않는다() {

        String js = OgPage.jsString("/a</script><b>\"'");
        assertFalse(js.contains("<"));
        assertFalse(js.substring(1, js.length() - 1).contains("\""));

    }

    @Test
    void 긴_글은_자르고_줄바꿈은_한_칸으로() {

        assertEquals("가 나 다", OgPage.clip(" 가\n\n나\t다 ", 70));
        String cut = OgPage.clip("가".repeat(200), 140);
        assertEquals(140, cut.length());
        assertTrue(cut.endsWith("…"));
        assertEquals("", OgPage.clip(null, 10));

    }

    @Test
    void 그때_샀더라면_제목() {

        assertEquals("삼성전자 1,000,000원 → 2,859,025원 (+185.90%) · 그때 샀더라면", OgController.whatIfTitle("삼성전자", 1_000_000, 2_859_025, "185.90"));
        assertEquals("카카오 1,000,000원 → 680,000원 (-32.00%) · 그때 샀더라면", OgController.whatIfTitle("카카오", 1_000_000, 680_000, "-32.00"));

    }
}
