package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.dao.BeautyLabDao;
import com.sklabs1037.sklabs1037.beauty.vo.BeautyLogVO;
import com.sklabs1037.sklabs1037.beauty.vo.IngredientVO;
import com.sklabs1037.sklabs1037.support.Fakes;

import java.util.*;
import java.util.function.Function;

/**
 * 뷰티 랩 테스트용 작은 성분 사전 (실제 beauty-lab.sql의 일부와 같은 값)
 */
final class BeautyTestData {

    private BeautyTestData() {
    }

    static List<IngredientVO> ingredients() {

        List<IngredientVO> list = new ArrayList<>();
        add(list, 1, "정제수", "Water", "물,워터,Aqua", "용제", null, "N", null, null);
        add(list, 2, "글리세린", "Glycerin", null, "보습", null, "N", null, null);
        add(list, 3, "부틸렌글라이콜", "Butylene Glycol", "부틸렌글리콜,BG", "보습", null, "N", null, null);
        add(list, 4, "나이아신아마이드", "Niacinamide", "니아신아마이드", "미백", "미백", "N", null, null);
        add(list, 5, "메틸파라벤", "Methylparaben", "메칠파라벤", "방부제", null, "N", "파라벤에 민감하다면 확인이 필요해요.", "민감성");
        add(list, 6, "병풀추출물", "Centella Asiatica Extract", "병풀잎추출물,시카", "진정", null, "N", null, null);
        add(list, 7, "살리실릭애시드", "Salicylic Acid", "살리실릭애씨드,BHA", "각질·여드름", "여드름", "N", "건조·따끔거림이 생길 수 있어요.", "민감성,건성");
        add(list, 8, "향료", "Fragrance", "Parfum", "향료", null, "N", "민감한 피부는 무향을 권해요.", "민감성");
        add(list, 9, "리모넨", "Limonene", null, "향료", null, "Y", "알레르기 유발 가능 착향제 25종에 속해요.", "민감성");
        add(list, 10, "1,2-헥산다이올", "1,2-Hexanediol", "헥산다이올", "보습", null, "N", null, null);
        add(list, 11, "에탄올", "Alcohol", "알코올", "용제", null, "N", "건조할 수 있어요.", "건성,민감성");
        add(list, 12, "아데노신", "Adenosine", null, "주름", "주름", "N", null, null);
        add(list, 13, "판테놀", "Panthenol", null, "보습·진정", null, "N", null, null);
        add(list, 14, "소듐하이알루로네이트", "Sodium Hyaluronate", "히알루론산", "보습", null, "N", null, null);
        add(list, 15, "베타인", "Betaine", null, "보습", null, "N", null, null);
        add(list, 16, "마데카소사이드", "Madecassoside", null, "진정", null, "N", null, null);
        add(list, 17, "알부틴", "Arbutin", null, "미백", "미백", "N", null, null);
        add(list, 18, "프로필파라벤", "Propylparaben", null, "방부제", null, "N", null, "민감성");
        add(list, 19, "부틸파라벤", "Butylparaben", null, "방부제", null, "N", null, "민감성");
        add(list, 20, "리날룰", "Linalool", null, "향료", null, "Y", "알레르기 유발 가능 착향제 25종에 속해요.", "민감성");
        return list;

    }

    private static void add(List<IngredientVO> list, long id, String ko, String en, String aliases, String category,
                            String functional, String allergen, String caution, String skin) {

        IngredientVO v = new IngredientVO();
        v.setIngredientId(id);
        v.setNameKo(ko);
        v.setNameEn(en);
        v.setAliases(aliases);
        v.setCategory(category);
        v.setDescription(ko + " 설명");
        v.setFunctional(functional);
        v.setAllergenYn(allergen);
        v.setCaution(caution);
        v.setCautionSkin(skin);
        list.add(v);

    }

    // 사전 + 오늘 사용 횟수(todayCount) + 저장된 기록(saved)을 흉내 내는 Dao
    static BeautyLabDao dao(int todayCount, List<BeautyLogVO> saved) {

        Map<String, Function<Object[], Object>> b = new HashMap<>();
        b.put("selectIngredients", a -> ingredients());
        b.put("countToday", a -> todayCount);
        b.put("insertLog", a -> {
            BeautyLogVO vo = (BeautyLogVO) a[0];
            vo.setLogId((long) (saved.size() + 1));
            saved.add(vo);
            return 1;
        });

        return Fakes.fake(BeautyLabDao.class, b, null);

    }

    static IngredientDictionary dictionary(BeautyLabDao dao) {

        IngredientDictionary d = new IngredientDictionary();
        Fakes.set(d, "beautyLabDao", dao);
        return d;

    }
}
