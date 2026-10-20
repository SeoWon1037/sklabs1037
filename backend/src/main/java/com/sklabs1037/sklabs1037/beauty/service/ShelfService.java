package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.vo.IngredientVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 🧴 내 화장대 · 성분 궁합 (AI 없이 사전 + 규칙)
 *
 * 입력: 제품 여러 개 (이름, 전성분 글, 단계: 클렌저·토너·세럼·크림·선크림, 시간: 아침·저녁·둘 다), 피부 타입(선택)
 * 결과
 *   1. 제품마다: 사전에서 찾은 성분 수, 주요 활성 성분(레티노이드·AHA·BHA·순수 비타민C·나이아신아마이드·자외선 차단 등),
 *               알레르기 착향제, 내 피부 타입에 주의 성분
 *   2. 아침·저녁 루틴 순서 (클렌저 → 토너 → 세럼 → 크림 → 선크림)
 *   3. 궁합 판정 (같은 루틴 안에서)
 *      - 레티노이드 + AHA/BHA          → 주의(자극) : 번갈아 다른 날 저녁에
 *      - 각질 제거 성분 2종 이상          → 주의(과한 각질 제거)
 *      - 순수 비타민C + AHA/BHA         → 확인(자극 가능)
 *      - 아침 레티노이드                → 확인(저녁 사용 권장)
 *      - 아침에 레티노이드·AHA·BHA인데 선크림 없음 → 주의(자외선 민감)
 *      - 같은 활성 성분 제품 2개 이상    → 확인(겹침)
 *      - 나이아신아마이드 + 비타민C      → 참고(예전엔 피하라고 했지만 지금은 같이 써도 괜찮다고 알려짐)
 *
 * - 일반적인 사용 안내이고, 피부 반응은 사람마다 달라요 (화면에 같이 표시)
 * - 로그인 없이, 제품 목록은 사용자 브라우저에 저장 (서버에는 검사할 때만 보냄)
 */
@Service
public class ShelfService {

    public static final int MAX_PRODUCTS = 12;

    static final List<String> STEPS = List.of("CLEANSER", "TONER", "SERUM", "CREAM", "SUNSCREEN");
    static final Map<String, String> STEP_LABELS = Map.of(
            "CLEANSER", "클렌저", "TONER", "토너", "SERUM", "세럼·앰플", "CREAM", "크림·로션", "SUNSCREEN", "선크림");

    // 활성 성분 묶음 (사전 표기 기준 이름, 비교는 normalize)
    record Group(String code, String label, List<String> names) {
    }

    static final List<Group> GROUPS = List.of(
            new Group("RETINOID", "레티노이드", List.of("레티놀", "레티닐팔미테이트", "레티날", "하이드록시피나콜론레티노에이트", "폴리에톡실레이티드레틴아마이드")),
            new Group("AHA", "AHA(각질)", List.of("글라이콜릭애시드", "락틱애시드", "만델릭애시드")),
            new Group("BHA", "BHA(각질)", List.of("살리실릭애시드", "베타인살리실레이트")),
            new Group("PHA", "PHA(순한 각질)", List.of("글루코노락톤", "락토바이오닉애시드")),
            new Group("VITC", "순수 비타민C", List.of("아스코빅애시드")),
            new Group("NIACIN", "나이아신아마이드", List.of("나이아신아마이드")),
            new Group("COPPER", "구리 펩타이드", List.of("코퍼트라이펩타이드-1")));

    private static final Set<String> EXFOLIANTS = Set.of("AHA", "BHA");

    @Autowired
    private IngredientDictionary dictionary;

    public record Product(String name, String ingredients, String step, String time) {
    }

    public Map<String, Object> check(List<Product> input, String skinType) {

        if (input == null || input.isEmpty()) throw new IllegalArgumentException("제품을 하나 이상 넣어주세요.");
        if (input.size() > MAX_PRODUCTS) throw new IllegalArgumentException("제품은 " + MAX_PRODUCTS + "개까지 검사할 수 있어요.");

        // ===== 1. 제품 분석 =====
        List<Analyzed> products = new ArrayList<>();

        for (Product p : input) {

            String name = p.name() == null || p.name().isBlank() ? "이름 없는 제품" : p.name().trim();
            if (name.length() > 60) name = name.substring(0, 60);

            String step = STEPS.contains(p.step()) ? p.step() : "SERUM";
            String time = List.of("AM", "PM", "BOTH").contains(p.time()) ? p.time() : "BOTH";

            List<String> names = BeautyLabService.splitIngredients(p.ingredients());
            if (names.isEmpty()) throw new IllegalArgumentException("'" + name + "' 전성분을 붙여넣어 주세요.");

            Analyzed a = new Analyzed(name, step, time, names.size());

            for (String raw : names) {

                IngredientVO hit = dictionary.find(raw);

                // 활성 성분은 사전에 없는 표기(레티날 등)도 이름으로 한 번 더 확인
                String shown = hit != null ? hit.getNameKo() : raw;
                String key = IngredientDictionary.normalize(shown);

                for (Group g : GROUPS) {
                    if (g.names().stream().anyMatch(n -> IngredientDictionary.normalize(n).equals(key))) {
                        a.actives.computeIfAbsent(g.code(), k -> new LinkedHashSet<>()).add(shown);
                    }
                }

                if (hit == null) continue;

                a.matched++;

                if ("자외선차단".equals(hit.getFunctional())) a.actives.computeIfAbsent("SUNSCREEN", k -> new LinkedHashSet<>()).add(hit.getNameKo());
                if ("Y".equals(hit.getAllergenYn())) a.allergens.add(hit.getNameKo());
                if (skinType != null && hit.getCautionSkin() != null && Arrays.asList(hit.getCautionSkin().split(",")).contains(skinType)) {
                    a.caution.add(hit.getNameKo());
                }

            }

            products.add(a);

        }

        // ===== 2~3. 아침·저녁 루틴 + 궁합 =====
        List<Map<String, Object>> findings = new ArrayList<>();
        Map<String, List<Map<String, Object>>> routines = new LinkedHashMap<>();

        for (String routine : List.of("AM", "PM")) {

            List<Analyzed> list = products.stream()
                    .filter(a -> a.time.equals(routine) || a.time.equals("BOTH"))
                    .filter(a -> !("PM".equals(routine) && "SUNSCREEN".equals(a.step)))   // 선크림은 저녁 루틴에서 뺌
                    .sorted(Comparator.comparingInt(a -> STEPS.indexOf(a.step)))
                    .toList();

            List<Map<String, Object>> steps = new ArrayList<>();
            for (Analyzed a : list) steps.add(Map.of("name", a.name, "step", a.step, "stepLabel", STEP_LABELS.get(a.step)));
            routines.put(routine, steps);

            findings.addAll(rules(routine, list));

        }

        // 저녁에만 쓰는 선크림
        for (Analyzed a : products) {
            if ("SUNSCREEN".equals(a.step) && "PM".equals(a.time)) {
                findings.add(finding("LOW", "PM", "선크림은 아침에", "선크림은 낮 동안 자외선을 막는 제품이라 아침 루틴 마지막 단계에 써요.", List.of(a.name)));
            }
        }

        findings.sort(Comparator.comparingInt(f -> List.of("HIGH", "MEDIUM", "LOW", "INFO").indexOf((String) f.get("level"))));

        // ===== 응답 =====
        List<Map<String, Object>> productMaps = new ArrayList<>();
        for (Analyzed a : products) {

            List<Map<String, Object>> actives = new ArrayList<>();
            a.actives.forEach((code, names) -> actives.add(Map.of("code", code, "label", label(code), "ingredients", new ArrayList<>(names))));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", a.name);
            m.put("step", a.step);
            m.put("stepLabel", STEP_LABELS.get(a.step));
            m.put("time", a.time);
            m.put("count", a.count);
            m.put("matched", a.matched);
            m.put("actives", actives);
            m.put("allergens", a.allergens);
            m.put("caution", a.caution);
            productMaps.add(m);

        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("products", productMaps);
        result.put("routines", routines);
        result.put("findings", findings);
        result.put("skinType", skinType);

        return result;

    }

    // 한 루틴 안의 궁합 규칙 (순수 계산, 테스트 가능)
    static List<Map<String, Object>> rules(String routine, List<Analyzed> list) {

        List<Map<String, Object>> out = new ArrayList<>();
        String when = "AM".equals(routine) ? "아침" : "저녁";

        List<String> retinoid = with(list, "RETINOID");
        List<String> exfoliant = list.stream().filter(a -> a.actives.keySet().stream().anyMatch(EXFOLIANTS::contains)).map(a -> a.name).toList();
        List<String> vitc = with(list, "VITC");
        List<String> niacin = with(list, "NIACIN");
        List<String> copper = with(list, "COPPER");
        List<String> sunscreen = list.stream().filter(a -> "SUNSCREEN".equals(a.step) || a.actives.containsKey("SUNSCREEN")).map(a -> a.name).toList();

        if (!retinoid.isEmpty() && !exfoliant.isEmpty()) {
            out.add(finding("HIGH", routine, when + " 루틴: 레티노이드 + 각질 제거 성분",
                    "같이 쓰면 따가움·붉어짐 같은 자극이 커질 수 있어요. 레티노이드와 AHA·BHA 제품은 다른 날 저녁에 번갈아 쓰는 걸 권해요.",
                    union(retinoid, exfoliant)));
        }

        Set<String> exfoliantKinds = new TreeSet<>();
        for (Analyzed a : list) for (String c : a.actives.keySet()) if (EXFOLIANTS.contains(c)) exfoliantKinds.add(c);
        if (exfoliant.size() >= 2 || exfoliantKinds.size() >= 2) {
            out.add(finding("MEDIUM", routine, when + " 루틴: 각질 제거 성분이 겹쳐요",
                    "AHA·BHA를 한 루틴에서 여러 번 쓰면 피부 장벽이 약해질 수 있어요. 하루 한 제품만 쓰고 나머지는 보습 위주로 해 주세요.",
                    exfoliant));
        }

        if (!vitc.isEmpty() && !exfoliant.isEmpty()) {
            out.add(finding("MEDIUM", routine, when + " 루틴: 순수 비타민C + AHA·BHA",
                    "둘 다 산성이라 같이 쓰면 자극을 느낄 수 있어요. 비타민C는 아침, 각질 제거는 저녁으로 나눠 보세요.",
                    union(vitc, exfoliant)));
        }

        if ("AM".equals(routine) && !retinoid.isEmpty()) {
            out.add(finding("MEDIUM", routine, "레티노이드는 저녁에",
                    "레티노이드는 빛에 약하고 피부를 자외선에 민감하게 할 수 있어서 보통 저녁에 써요.", retinoid));
        }

        if ("AM".equals(routine) && sunscreen.isEmpty() && (!retinoid.isEmpty() || !exfoliant.isEmpty())) {
            out.add(finding("HIGH", routine, "아침 루틴에 선크림이 없어요",
                    "레티노이드·AHA·BHA를 쓰는 기간에는 피부가 자외선에 민감해져요. 아침 마지막 단계에 선크림을 꼭 더해 주세요.",
                    union(retinoid, exfoliant)));
        }

        for (Group g : GROUPS) {
            List<String> same = with(list, g.code());
            if (same.size() >= 2 && !"NIACIN".equals(g.code())) {
                out.add(finding("LOW", routine, when + " 루틴: " + g.label() + " 제품이 " + same.size() + "개",
                        "같은 활성 성분을 여러 제품으로 겹쳐 바르면 효과보다 자극이 먼저 늘 수 있어요. 하나로 줄여 보세요.", same));
            }
        }

        if (!copper.isEmpty() && !vitc.isEmpty()) {
            out.add(finding("LOW", routine, when + " 루틴: 구리 펩타이드 + 순수 비타민C",
                    "같이 바르면 서로 효과를 떨어뜨릴 수 있다고 알려져 있어요. 아침·저녁으로 나눠 쓰면 좋아요.", union(copper, vitc)));
        }

        if (!niacin.isEmpty() && !vitc.isEmpty()) {
            out.add(finding("INFO", routine, when + " 루틴: 나이아신아마이드 + 비타민C",
                    "예전에는 같이 쓰지 말라는 말이 많았지만, 지금 쓰는 화장품 농도에서는 대체로 함께 써도 괜찮다고 알려져 있어요. 따가우면 나눠 쓰세요.",
                    union(niacin, vitc)));
        }

        return out;

    }

    // ============================================
    // 내부
    // ============================================
    static final class Analyzed {

        final String name;
        final String step;
        final String time;
        final int count;
        int matched;
        final Map<String, Set<String>> actives = new LinkedHashMap<>();
        final List<String> allergens = new ArrayList<>();
        final List<String> caution = new ArrayList<>();

        Analyzed(String name, String step, String time, int count) {
            this.name = name;
            this.step = step;
            this.time = time;
            this.count = count;
        }
    }

    private static List<String> with(List<Analyzed> list, String code) {

        return list.stream().filter(a -> a.actives.containsKey(code)).map(a -> a.name).toList();

    }

    private static List<String> union(List<String> a, List<String> b) {

        LinkedHashSet<String> s = new LinkedHashSet<>(a);
        s.addAll(b);

        return new ArrayList<>(s);

    }

    private static String label(String code) {

        if ("SUNSCREEN".equals(code)) return "자외선 차단";

        return GROUPS.stream().filter(g -> g.code().equals(code)).map(Group::label).findFirst().orElse(code);

    }

    private static Map<String, Object> finding(String level, String routine, String title, String detail, List<String> products) {

        Map<String, Object> f = new LinkedHashMap<>();
        f.put("level", level);          // HIGH 주의 / MEDIUM 확인 / LOW 참고 / INFO 알아두기
        f.put("routine", routine);
        f.put("title", title);
        f.put("detail", detail);
        f.put("products", products);

        return f;

    }
}
