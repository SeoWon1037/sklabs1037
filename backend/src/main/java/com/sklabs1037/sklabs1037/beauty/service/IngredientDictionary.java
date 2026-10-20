package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.dao.BeautyLabDao;
import com.sklabs1037.sklabs1037.beauty.vo.IngredientVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * 화장품 성분 사전 (T_INGREDIENT를 메모리에 올려두고 비교)
 * - 150개 남짓이라 매번 DB를 뒤지지 않고 10분마다 다시 읽음
 * - 표기 차이를 맞춰서 비교 (normalize)
 *     "메칠파라벤" = "메틸파라벤", "살리실릭애씨드" = "살리실릭애시드", "부틸렌글라이콜" = "부틸렌글리콜",
 *     띄어쓰기·하이픈·괄호 무시, 영문 INCI 대소문자 무시
 */
@Slf4j
@Component
public class IngredientDictionary {

    // 다시 읽는 간격 (초)
    private static final long RELOAD_SECONDS = 600;

    @Autowired
    private BeautyLabDao beautyLabDao;

    private volatile List<IngredientVO> all = List.of();
    private volatile Map<String, IngredientVO> byKey = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    // 이름 하나 → 사전 성분 (없으면 null)
    public IngredientVO find(String name) {

        if (name == null || name.isBlank()) return null;

        Map<String, IngredientVO> map = ensureLoaded();
        String key = normalize(name);

        IngredientVO hit = map.get(key);
        if (hit != null) return hit;

        // "병풀추출물(50,000ppm)", "나이아신아마이드 5%" 처럼 뒤에 붙은 함량 표시 빼고 한 번 더
        String trimmed = normalize(name.replaceAll("[\\(\\[].*$", "").replaceAll("\\d+(\\.\\d+)?\\s*(%|ppm|ppb)", ""));

        return trimmed.isEmpty() ? null : map.get(trimmed);

    }

    // 전체
    public List<IngredientVO> all() {

        ensureLoaded();
        return all;

    }

    // 사전 검색 (이름·영문명·다른 표기·역할에 검색어가 들어간 것)
    public List<IngredientVO> search(String query, int limit) {

        ensureLoaded();
        String q = normalize(query);

        if (q.isEmpty()) return all.stream().limit(limit).toList();

        List<IngredientVO> exact = new ArrayList<>();
        List<IngredientVO> partial = new ArrayList<>();

        for (IngredientVO i : all) {

            String keys = normalize(i.getNameKo()) + "|" + normalize(i.getNameEn()) + "|" + normalize(i.getAliases()) + "|" + normalize(i.getCategory());

            if (normalize(i.getNameKo()).equals(q)) exact.add(i);
            else if (keys.contains(q)) partial.add(i);

        }

        exact.addAll(partial);

        return exact.stream().limit(limit).toList();

    }

    // 표기 차이 맞추기
    public static String normalize(String text) {

        if (text == null) return "";

        String s = text.toLowerCase(Locale.ROOT)
                .replace("메칠", "메틸")
                .replace("에칠", "에틸")
                .replace("애씨드", "애시드")
                .replace("글라이콜", "글리콜")
                .replace("다이", "디")
                .replace("트라이", "트리")
                .replace("아이소", "이소");

        return s.replaceAll("[\\s\\-_/(),.·'\\[\\]]", "");

    }

    // ============================================
    // 사전 읽기
    // ============================================
    private Map<String, IngredientVO> ensureLoaded() {

        if (Instant.now().isBefore(loadedAt.plusSeconds(RELOAD_SECONDS)) && !byKey.isEmpty()) return byKey;

        synchronized (this) {

            if (Instant.now().isBefore(loadedAt.plusSeconds(RELOAD_SECONDS)) && !byKey.isEmpty()) return byKey;

            try {

                List<IngredientVO> list = beautyLabDao.selectIngredients();
                Map<String, IngredientVO> map = new HashMap<>();

                // 한글명 → 영문명 → 다른 표기 순서로 (다른 성분의 별칭이 한글명을 덮어쓰지 않게)
                for (IngredientVO i : list) put(map, i.getNameKo(), i);
                for (IngredientVO i : list) put(map, i.getNameEn(), i);
                for (IngredientVO i : list) {
                    if (i.getAliases() != null) {
                        for (String a : i.getAliases().split(",")) put(map, a, i);
                    }
                }

                all = List.copyOf(list);
                byKey = Map.copyOf(map);
                loadedAt = Instant.now();
                log.info("성분 사전 읽음: {}개 (표기 {}개)", list.size(), map.size());

            } catch (Exception e) {

                // 테이블이 아직 없어도 서버는 뜨게 (다음 요청 때 다시)
                log.warn("성분 사전 읽기 실패: {}", e.getMessage());

            }

            return byKey;

        }

    }

    private void put(Map<String, IngredientVO> map, String name, IngredientVO i) {

        String key = normalize(name);

        if (!key.isEmpty()) map.putIfAbsent(key, i);

    }
}
