package com.sklabs1037.sklabs1037.market.service;

import com.sklabs1037.sklabs1037.market.service.MarketRules.Draft;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🛍️ 중고거래 규칙: 글 검사 · 금지 품목 · 사기 주의 · 상태 바꾸기 · AI 초안 정리
 */
class MarketRulesTest {

    private static Map<String, Object> input(Object... kv) {
        Map<String, Object> m = new HashMap<>(Map.of("category", "digital", "title", "  아이폰 15   프로 256 ", "price", "850,000원",
                "description", "작년 11월 구매, 배터리 91%, 풀박스입니다.", "cond", "like_new", "tradeType", "direct", "region", " 서울  강남구 "));
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void 입력을_정리해서_받음() {

        Draft d = MarketRules.clean(input());
        assertEquals("DIGITAL", d.category());
        assertEquals("아이폰 15 프로 256", d.title());
        assertEquals(850_000L, d.price());
        assertEquals("LIKE_NEW", d.cond());
        assertEquals("DIRECT", d.tradeType());
        assertEquals("서울 강남구", d.region());

    }

    @Test
    void 나눔은_0원_숫자도_그대로() {

        assertEquals(0L, MarketRules.clean(input("price", 0)).price());
        assertEquals(12000L, MarketRules.clean(input("price", 12000)).price());

    }

    @Test
    void 잘못된_입력은_이유를_알려줌() {

        assertThrows(IllegalArgumentException.class, () -> MarketRules.clean(input("category", "CAR")));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.clean(input("title", "a")));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.clean(input("description", "짧음")));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.clean(input("price", "비싸요")));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.clean(input("price", -1)));
        assertThrows(IllegalArgumentException.class, () -> MarketRules.clean(input("price", 100_000_000)));

    }

    @Test
    void 모르는_상태_거래방법은_기본값() {

        Draft d = MarketRules.clean(input("cond", "???", "tradeType", null, "region", ""));
        assertEquals("USED", d.cond());
        assertEquals("BOTH", d.tradeType());
        assertNull(d.region());

    }

    @Test
    void 금지_품목은_올릴_수_없음() {

        assertEquals("담배·전자담배", MarketRules.banned("전자 담배 기기 팝니다"));
        assertEquals("계정·개인정보", MarketRules.banned("게임 계정 판매합니다"));
        assertEquals("의약품", MarketRules.banned("다이어트약 남은 것"));
        assertNull(MarketRules.banned("와인잔 세트 팝니다"));   // 와인잔은 술이 아님
        assertNull(MarketRules.banned("담요, 책상 정리합니다"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MarketRules.clean(input("description", "신분증 사본도 같이 드려요. 연락주세요.")));
        assertTrue(e.getMessage().contains("거래할 수 없는 품목"));

    }

    @Test
    void 사기에_자주_쓰이는_말은_주의만() {

        List<String> c = MarketRules.cautions("선입금 하시면 바로 보내드려요. 카톡 아이디 abc 로 연락주세요");
        assertEquals(2, c.size(), c.toString());
        assertTrue(MarketRules.cautions("직거래 선호합니다. 상태 좋아요").isEmpty());
        // 주의 표현은 등록 자체는 됨
        assertNotNull(MarketRules.clean(input("description", "택배는 선입금 후 발송합니다. 직거래 환영해요.")));

    }

    @Test
    void 상태는_판매중_예약중_판매완료_사이에서만() {

        assertTrue(MarketRules.canChange("SALE", "RESERVED"));
        assertTrue(MarketRules.canChange("SOLD", "SALE"));
        assertFalse(MarketRules.canChange("SALE", "SALE"));
        assertFalse(MarketRules.canChange("HIDDEN", "SALE"), "신고로 숨겨진 상품은 판매자가 못 품");
        assertFalse(MarketRules.canChange("SALE", "HIDDEN"));

    }

    @Test
    void 정렬과_검색어() {

        assertEquals("LOW", MarketRules.sortOf("low"));
        assertEquals("RECENT", MarketRules.sortOf("; DROP TABLE"));
        assertEquals("아이폰 100", MarketRules.keyword("  아이폰%_100\\ "));

    }

    @Test
    void 사진은_진짜_그림만() {

        byte[] jpg = new byte[20];
        jpg[0] = (byte) 0xFF; jpg[1] = (byte) 0xD8; jpg[2] = (byte) 0xFF;
        assertEquals("image/jpeg", MarketPhotos.imageType(jpg));
        assertNull(MarketPhotos.imageType("<script>alert(1)</script>".getBytes()));
        String b64 = Base64.getEncoder().encodeToString(jpg);
        assertEquals(20, MarketPhotos.decode("data:image/jpeg;base64," + b64, 1000).length);
        assertThrows(IllegalArgumentException.class, () -> MarketPhotos.decode(b64, 10));
        assertThrows(IllegalArgumentException.class, () -> MarketPhotos.decode("%%%", 1000));

    }

    @Test
    void AI_초안은_서버가_다시_정리() {

        Map<String, Object> out = MarketService.cleanAi(Map.of("title", "x".repeat(60), "description", "깨끗하게 썼어요.", "category", "car"), "의자");
        assertEquals(40, String.valueOf(out.get("title")).length());
        assertEquals("ETC", out.get("category"));
        assertThrows(IllegalArgumentException.class,
                () -> MarketService.cleanAi(Map.of("title", "전자담배 팝니다", "description", "새것", "category", "ETC"), "x"));

    }

    @Test
    void 채팅_첫_안내_메시지() {

        assertEquals("🛍️ 상품 문의: 의자 (15,000원)\n/market/7", MarketService.introMessage(7L, "의자", 15000L));
        assertTrue(MarketService.introMessage(7L, "책", 0L).contains("(나눔)"));

    }
}
