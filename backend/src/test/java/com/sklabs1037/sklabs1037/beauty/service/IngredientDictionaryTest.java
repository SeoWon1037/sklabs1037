package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.vo.IngredientVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 성분 사전 표기 맞추기
 * - 화장품 포장마다 같은 성분을 다르게 적어서(메칠/메틸, 애씨드/애시드, 함량 표시) 이게 틀리면 분석 전체가 틀림
 */
class IngredientDictionaryTest {

    private final IngredientDictionary dict = BeautyTestData.dictionary(BeautyTestData.dao(0, new ArrayList<>()));

    @Test
    void 옛_표기와_새_표기를_같은_성분으로() {
        assertEquals("메틸파라벤", dict.find("메칠파라벤").getNameKo());
        assertEquals("살리실릭애시드", dict.find("살리실릭애씨드").getNameKo());
        assertEquals("부틸렌글라이콜", dict.find("부틸렌글리콜").getNameKo());
    }

    @Test
    void 영문_INCI와_다른_이름도_찾음() {
        assertEquals("정제수", dict.find("Aqua").getNameKo());
        assertEquals("나이아신아마이드", dict.find("niacinamide").getNameKo());
        assertEquals("병풀추출물", dict.find("병풀잎추출물").getNameKo());
    }

    @Test
    void 띄어쓰기와_함량_표시는_무시() {
        assertEquals("나이아신아마이드", dict.find("나이아신 아마이드").getNameKo());
        assertEquals("나이아신아마이드", dict.find("나이아신아마이드(5%)").getNameKo());
        assertEquals("병풀추출물", dict.find("병풀추출물 10,000ppm").getNameKo());
    }

    @Test
    void 숫자_쉼표가_든_성분명() {
        IngredientVO hit = dict.find("1,2-헥산다이올");
        assertNotNull(hit);
        assertEquals("1,2-헥산다이올", hit.getNameKo());
    }

    @Test
    void 사전에_없는_성분은_null() {
        assertNull(dict.find("이상한성분ABC"));
        assertNull(dict.find(""));
        assertNull(dict.find(null));
    }

    @Test
    void 검색은_이름이_같은_것을_먼저() {
        var list = dict.search("글리세린", 10);
        assertEquals("글리세린", list.get(0).getNameKo());
        assertTrue(dict.search("방부", 10).stream().anyMatch(i -> i.getNameKo().equals("메틸파라벤")));
    }

    @Test
    void 표기_맞추기_규칙() {
        assertEquals(IngredientDictionary.normalize("에칠헥실 글리세린"), IngredientDictionary.normalize("에틸헥실글리세린"));
        assertEquals(IngredientDictionary.normalize("다이소듐이디티에이"), IngredientDictionary.normalize("디소듐이디티에이"));
        assertEquals("", IngredientDictionary.normalize(null));
    }
}
