package com.sklabs1037.sklabs1037.beauty.service;

import java.util.*;

/**
 * 처방 검토 규칙 (숫자는 전부 고시 원문 기준, AI가 판단하지 않음)
 *
 * 출처
 *   A. 「화장품 안전기준 등에 관한 규정」 [별표 2] 사용상의 제한이 필요한 원료 (보존제·자외선 차단 성분 사용한도)
 *   B. 「기능성화장품 심사에 관한 규정」 [별표 4] 자료제출이 생략되는 기능성화장품의 종류 (미백·주름·여드름 고시 함량)
 *   C. 「화장품 사용 시의 주의사항 및 알레르기 유발성분 표시에 관한 규정」 착향제 알레르기 유발성분
 *      (사용 후 씻어내는 제품 0.01% 초과, 씻어내지 않는 제품 0.001% 초과 시 성분명 표시)
 *   D. 「화장품법 시행규칙」 전성분 표시: 함량이 많은 것부터, 1% 이하 성분·착향제·착색제는 순서 상관없음
 *
 * - 규정이 바뀌면 이 파일만 고치면 됨 (테스트가 숫자를 같이 확인)
 * - 이름은 IngredientDictionary.normalize 로 맞춰서 비교 (에칠/에틸, 다이/디, 애씨드/애시드 표기 차이 무시)
 * - 여기 없는 원료는 "한도 규정 대상이 아님"이 아니라 "이 검토기가 아직 모르는 원료"로 안내
 */
public final class FormulaRules {

    private FormulaRules() {
    }

    public static final String RULE_VERSION = "2026-10 기준 (별표2 보존제·자외선 차단 성분 일부, 별표4 미백·주름·여드름)";

    // 알레르기 유발 착향제 표시 기준 (%)
    public static final double ALLERGEN_LABEL_LEAVE_ON = 0.001;
    public static final double ALLERGEN_LABEL_RINSE_OFF = 0.01;

    // 전성분 표시 순서를 지켜야 하는 기준 (이 값 초과만 함량순)
    public static final double LABEL_ORDER_FREE_AT = 1.0;

    // ============================================
    // A. 사용 한도
    // ============================================

    /**
     * 사용 한도 하나
     * @param names       해당 원료 이름들 (사전 표기 + 고시 표기)
     * @param leaveOn     씻어내지 않는 제품 한도 (%), null = 사용 금지
     * @param rinseOff    씻어내는 제품 한도 (%), null = 사용 금지
     * @param factor      "산으로서" 환산 계수 (원료 % × factor = 산 %), 그대로면 1
     * @param group       합산 한도 묶음 (같은 group끼리 더해서 groupLimit과 비교), 없으면 null
     * @param kidsBanned  영유아·어린이용 제품 사용 금지 (샴푸 제외)
     * @param basis       화면에 보여줄 고시 문구
     */
    public record Limit(List<String> names, Double leaveOn, Double rinseOff, double factor,
                        String group, boolean kidsBanned, String category, String basis) {
    }

    // 합산 한도 (group → 한도 %, 설명)
    public record GroupLimit(double limit, String label, String basis) {
    }

    // 파라벤 분자량 환산: p-하이드록시벤조익애씨드(138.12) / 각 에스텔
    private static final double PHBA = 138.12;

    public static final List<Limit> LIMITS = List.of(

            // ----- 보존제 -----
            new Limit(List.of("페녹시에탄올"), 1.0, 1.0, 1, null, false, "보존제", "1.0%"),
            new Limit(List.of("메틸파라벤", "메칠파라벤"), 0.4, 0.4, PHBA / 152.15, "PARABEN", false, "보존제",
                    "단일성분일 경우 0.4%(산으로서), 혼합사용의 경우 0.8%(산으로서)"),
            new Limit(List.of("에틸파라벤", "에칠파라벤"), 0.4, 0.4, PHBA / 166.17, "PARABEN", false, "보존제",
                    "단일성분일 경우 0.4%(산으로서), 혼합사용의 경우 0.8%(산으로서)"),
            new Limit(List.of("프로필파라벤"), 0.4, 0.4, PHBA / 180.20, "PARABEN", false, "보존제",
                    "단일성분일 경우 0.4%(산으로서), 혼합사용의 경우 0.8%(산으로서)"),
            new Limit(List.of("부틸파라벤"), 0.4, 0.4, PHBA / 194.23, "PARABEN", false, "보존제",
                    "단일성분일 경우 0.4%(산으로서), 혼합사용의 경우 0.8%(산으로서)"),
            new Limit(List.of("벤질알코올", "벤질알콜"), 1.0, 1.0, 1, null, false, "보존제",
                    "1.0% (두발 염색용 제품류 용제는 10%, 이 검토기는 일반 제품 기준)"),
            new Limit(List.of("벤조익애시드", "벤조익애씨드"), 0.5, 2.5, 1, null, false, "보존제",
                    "산으로서 0.5% (벤조익애씨드 및 그 소듐염은 씻어내는 제품에 산으로서 2.5%)"),
            new Limit(List.of("소듐벤조에이트"), 0.5, 2.5, 122.12 / 144.10, null, false, "보존제",
                    "산으로서 0.5% (벤조익애씨드 및 그 소듐염은 씻어내는 제품에 산으로서 2.5%)"),
            new Limit(List.of("소르빅애시드", "소르빅애씨드"), 0.6, 0.6, 1, null, false, "보존제", "소르빅애씨드로서 0.6%"),
            new Limit(List.of("포타슘소르베이트"), 0.6, 0.6, 112.13 / 150.22, null, false, "보존제", "소르빅애씨드로서 0.6%"),
            new Limit(List.of("클로페네신"), 0.3, 0.3, 1, null, false, "보존제", "0.3%"),
            new Limit(List.of("살리실릭애시드", "살리실릭애씨드", "살리실산"), 0.5, 0.5, 1, null, true, "보존제·여드름",
                    "살리실릭애씨드로서 0.5% (영유아·만 13세 이하 어린이용 표시 제품에는 사용 금지, 샴푸 제외)"),
            new Limit(List.of("징크피리치온"), null, 0.5, 1, null, false, "보존제",
                    "사용 후 씻어내는 제품에 0.5% (기타 제품에는 사용 금지)"),

            // ----- 자외선 차단 성분 (최대 함량) -----
            new Limit(List.of("징크옥사이드"), 25.0, 25.0, 1, null, false, "자외선차단", "25% (자외선차단성분으로서)"),
            new Limit(List.of("티타늄디옥사이드"), 25.0, 25.0, 1, null, false, "자외선차단", "25% (자외선차단성분으로서)"),
            new Limit(List.of("에틸헥실메톡시신나메이트", "에칠헥실메톡시신나메이트"), 7.5, 7.5, 1, null, false, "자외선차단", "7.5%"),
            new Limit(List.of("에틸헥실살리실레이트", "에칠헥실살리실레이트"), 5.0, 5.0, 1, null, false, "자외선차단", "5%"),
            new Limit(List.of("호모살레이트"), 10.0, 10.0, 1, null, false, "자외선차단", "10%"),
            new Limit(List.of("옥토크릴렌"), 10.0, 10.0, 1, null, false, "자외선차단", "10%"),
            new Limit(List.of("부틸메톡시다이벤조일메탄", "부틸메톡시디벤조일메탄"), 5.0, 5.0, 1, null, false, "자외선차단", "5%"),
            new Limit(List.of("비스-에틸헥실옥시페놀메톡시페닐트라이아진", "비스-에칠헥실옥시페놀메톡시페닐트리아진"), 10.0, 10.0, 1, null, false, "자외선차단", "10%"),
            new Limit(List.of("다이에틸아미노하이드록시벤조일헥실벤조에이트", "디에칠아미노하이드록시벤조일헥실벤조에이트"), 10.0, 10.0, 1, null, false, "자외선차단", "10%"),
            new Limit(List.of("에틸헥실트라이아존", "에칠헥실트리아존"), 5.0, 5.0, 1, null, false, "자외선차단", "5%"),
            new Limit(List.of("메틸렌비스-벤조트라이아졸릴테트라메틸부틸페놀", "메칠렌비스-벤조트리아졸릴테트라메칠부틸페놀"), 10.0, 10.0, 1, null, false, "자외선차단", "10%"),
            new Limit(List.of("페닐벤즈이미다졸설포닉애시드", "페닐벤즈이미다졸설포닉애씨드"), 4.0, 4.0, 1, null, false, "자외선차단", "4%"),
            new Limit(List.of("드로메트리졸트라이실록세인", "드로메트리졸트리실록산"), 15.0, 15.0, 1, null, false, "자외선차단", "15%"),
            new Limit(List.of("다이에틸헥실부타미도트라이아존", "디에칠헥실부타미도트리아존"), 10.0, 10.0, 1, null, false, "자외선차단", "10%"),
            new Limit(List.of("벤조페논-3", "옥시벤존"), 5.0, 5.0, 1, null, false, "자외선차단", "5%")
    );

    public static final Map<String, GroupLimit> GROUP_LIMITS = Map.of(
            "PARABEN", new GroupLimit(0.8, "파라벤 합계", "혼합사용의 경우 0.8%(산으로서)")
    );

    // ============================================
    // B. 기능성 고시 함량 (자료제출이 생략되는 범위)
    // ============================================

    /**
     * @param claim   미백 / 주름 / 여드름
     * @param min     고시 함량 최소 (%), 하나로 정해진 값이면 min = max
     * @param max     고시 함량 최대 (%)
     * @param note    % 로 판단할 수 없는 경우 안내 (IU 단위 등)
     * @param rinseOffOnly 씻어내는 제품만 해당 (여드름)
     */
    public record Functional(List<String> names, String claim, Double min, Double max, String basis, String note,
                             boolean rinseOffOnly) {
    }

    public static final List<String> CLAIMS = List.of("미백", "주름", "여드름");

    public static final List<Functional> FUNCTIONALS = List.of(
            new Functional(List.of("닥나무추출물"), "미백", 2.0, 2.0, "2%", null, false),
            new Functional(List.of("알부틴"), "미백", 2.0, 5.0, "2~5%", null, false),
            new Functional(List.of("에틸아스코빌에터", "에칠아스코빌에텔"), "미백", 1.0, 2.0, "1~2%", null, false),
            new Functional(List.of("유용성감초추출물"), "미백", 0.05, 0.05, "0.05%", null, false),
            new Functional(List.of("아스코빌글루코사이드"), "미백", 2.0, 2.0, "2%", null, false),
            new Functional(List.of("마그네슘아스코빌포스페이트"), "미백", 3.0, 3.0, "3%", null, false),
            new Functional(List.of("나이아신아마이드"), "미백", 2.0, 5.0, "2~5%", null, false),
            new Functional(List.of("알파-비사보롤"), "미백", 0.5, 0.5, "0.5%", null, false),
            new Functional(List.of("아스코빌테트라아이소팔미테이트", "아스코빌테트라이소팔미테이트"), "미백", 2.0, 2.0, "2%", null, false),

            new Functional(List.of("레티놀"), "주름", null, null, "2,500 IU/g",
                    "고시 함량이 IU 단위라 %로 판정하지 않아요. 원료 역가(IU/g)로 따로 계산해주세요.", false),
            new Functional(List.of("레티닐팔미테이트"), "주름", null, null, "10,000 IU/g",
                    "고시 함량이 IU 단위라 %로 판정하지 않아요. 원료 역가(IU/g)로 따로 계산해주세요.", false),
            new Functional(List.of("아데노신"), "주름", 0.04, 0.04, "0.04%", null, false),
            new Functional(List.of("폴리에톡실레이티드레틴아마이드"), "주름", 0.05, 0.2, "0.05~0.2%", null, false),

            new Functional(List.of("살리실릭애시드", "살리실릭애씨드", "살리실산"), "여드름", 0.5, 0.5, "0.5% (씻어내는 제품)", null, true)
    );

    // ============================================
    // 찾기
    // ============================================
    private static final Map<String, Limit> LIMIT_BY_NAME = index(LIMITS, Limit::names);
    private static final Map<String, List<Functional>> FUNCTIONAL_BY_NAME = indexMulti(FUNCTIONALS);

    public static Limit limitOf(String name) {

        return LIMIT_BY_NAME.get(IngredientDictionary.normalize(name));

    }

    public static List<Functional> functionalsOf(String name) {

        return FUNCTIONAL_BY_NAME.getOrDefault(IngredientDictionary.normalize(name), List.of());

    }

    private static <T> Map<String, T> index(List<T> list, java.util.function.Function<T, List<String>> names) {

        Map<String, T> map = new HashMap<>();
        for (T t : list) for (String n : names.apply(t)) map.put(IngredientDictionary.normalize(n), t);

        return Map.copyOf(map);

    }

    private static Map<String, List<Functional>> indexMulti(List<Functional> list) {

        Map<String, List<Functional>> map = new HashMap<>();
        for (Functional f : list) {
            for (String n : f.names()) map.computeIfAbsent(IngredientDictionary.normalize(n), k -> new ArrayList<>()).add(f);
        }

        return Map.copyOf(map);

    }
}
