package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.service.FormulaRules.Functional;
import com.sklabs1037.sklabs1037.beauty.service.FormulaRules.GroupLimit;
import com.sklabs1037.sklabs1037.beauty.service.FormulaRules.Limit;
import com.sklabs1037.sklabs1037.beauty.vo.IngredientVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 🧪 처방 검토기 (AI 없이 규칙만으로)
 *
 * 입력: 원료 이름 + 함량(%) 목록, 하나는 "적량"(나머지 채우기, 보통 정제수) 가능
 *       제품 유형(씻어내지 않는 / 씻어내는), 내세울 기능성(미백·주름·여드름), 어린이용 여부
 *
 * 결과
 *   1. 합계: 적량을 채운 뒤 100%인지 (넘거나 모자라면 오류)
 *   2. 사용 한도: 원료별 + 합산(파라벤) 한도, "산으로서" 환산, 제품 유형별 한도, 어린이용 금지
 *   3. 기능성: 고른 기능성마다 고시 성분이 고시 함량 안에 있는지 (자료제출 생략 가능 여부)
 *   4. 알레르기 착향제: 표시해야 하는 성분
 *   5. 전성분 표시안: 1% 초과는 함량 많은 순, 1% 이하는 입력 순서 그대로
 *
 * - 사전(IngredientDictionary)으로 이름을 맞춘 뒤 규칙(FormulaRules)으로 판단
 * - 결과 문구는 "규정 원문"을 같이 보여줌 → 사람이 확인할 수 있게
 */
@Service
public class FormulaCheckService {

    public static final int MAX_ITEMS = 80;
    private static final double EPS = 1e-6;

    @Autowired
    private IngredientDictionary dictionary;

    // 원료 한 줄
    public record Item(String name, Double pct, boolean qs) {
    }

    public Map<String, Object> check(List<Item> input, boolean rinseOff, List<String> claims, boolean kids) {

        if (input == null || input.isEmpty()) throw new IllegalArgumentException("원료를 하나 이상 넣어주세요.");
        if (input.size() > MAX_ITEMS) throw new IllegalArgumentException("원료는 " + MAX_ITEMS + "개까지 넣을 수 있어요.");

        List<Map<String, Object>> issues = new ArrayList<>();

        // ===== 1. 이름 맞추기 + 합계 =====
        List<Row> rows = new ArrayList<>();
        int qsCount = 0;
        double fixed = 0;

        for (int i = 0; i < input.size(); i++) {

            Item it = input.get(i);
            String raw = it.name() == null ? "" : it.name().trim();
            if (raw.isEmpty()) continue;

            if (it.qs()) {
                qsCount++;
            } else {
                if (it.pct() == null || it.pct().isNaN() || it.pct() < 0) throw new IllegalArgumentException("'" + raw + "' 함량을 확인해주세요.");
                if (it.pct() > 100) throw new IllegalArgumentException("'" + raw + "' 함량이 100%를 넘어요.");
                fixed += it.pct();
            }

            IngredientVO hit = dictionary.find(raw);
            rows.add(new Row(i, raw, hit, it.qs() ? null : it.pct(), it.qs()));

        }

        if (rows.isEmpty()) throw new IllegalArgumentException("원료를 하나 이상 넣어주세요.");
        if (qsCount > 1) throw new IllegalArgumentException("'적량'은 한 원료에만 쓸 수 있어요. (보통 정제수)");

        double qsPct = 0;
        double total;

        if (qsCount == 1) {

            qsPct = round(100 - fixed);
            for (Row r : rows) if (r.qs) r.pct = Math.max(0, qsPct);
            total = qsPct < 0 ? fixed : 100;

            if (qsPct < 0) {
                issues.add(issue("ERROR", "합계", "적량을 빼고도 " + fmt(fixed) + "%라서 100%를 넘어요.", "전체 합계는 100%"));
            }

        } else {

            total = fixed;

            if (Math.abs(fixed - 100) > 0.0005) {
                issues.add(issue("ERROR", "합계",
                        "합계가 " + fmt(fixed) + "%예요. 100%가 되게 맞추거나 정제수를 '적량'으로 두세요.", "전체 합계는 100%"));
            }

        }

        // 같은 원료 두 번
        Map<String, Row> seen = new HashMap<>();
        for (Row r : rows) {
            Row before = seen.putIfAbsent(r.key(), r);
            if (before != null) {
                issues.add(issue("WARN", r.label(), "같은 원료가 두 번 들어 있어요. 한 줄로 합쳐주세요.", null));
            }
        }

        // ===== 2. 사용 한도 =====
        Map<String, Double> groupSum = new HashMap<>();
        Map<String, List<String>> groupNames = new HashMap<>();

        for (Row r : rows) {

            Limit limit = FormulaRules.limitOf(r.label());
            if (limit == null && r.hit != null) limit = FormulaRules.limitOf(r.hit.getNameKo());
            if (limit == null) continue;

            r.limited = true;
            double asRegulated = r.pct * limit.factor();
            Double max = rinseOff ? limit.rinseOff() : limit.leaveOn();
            String where = rinseOff ? "씻어내는 제품" : "씻어내지 않는 제품";

            if (max == null) {
                issues.add(issue("ERROR", r.label(), where + "에는 쓸 수 없는 원료예요.", limit.basis()));
            } else if (asRegulated > max + EPS) {
                issues.add(issue("ERROR", r.label(),
                        fmt(r.pct) + "%" + (limit.factor() != 1 ? " (산으로 환산 " + fmt(asRegulated) + "%)" : "") + " → 한도 " + fmt(max) + "% 초과",
                        limit.basis()));
            } else if (asRegulated > max * 0.9) {
                issues.add(issue("WARN", r.label(),
                        "한도 " + fmt(max) + "%에 가까워요 (" + fmt(asRegulated) + "%). 원료 순도·공정 손실을 고려해 여유를 두세요.", limit.basis()));
            }

            if (kids && limit.kidsBanned()) {
                issues.add(issue("ERROR", r.label(), "영유아·어린이용으로 표시하는 제품에는 쓸 수 없어요 (샴푸 제외).", limit.basis()));
            }

            if (limit.group() != null) {
                groupSum.merge(limit.group(), asRegulated, Double::sum);
                groupNames.computeIfAbsent(limit.group(), k -> new ArrayList<>()).add(r.label());
            }

        }

        groupSum.forEach((group, sum) -> {
            GroupLimit g = FormulaRules.GROUP_LIMITS.get(group);
            if (g != null && groupNames.get(group).size() > 1 && sum > g.limit() + EPS) {
                issues.add(issue("ERROR", g.label(),
                        String.join(" + ", groupNames.get(group)) + " = " + fmt(sum) + "% (산으로서) → 합산 한도 " + fmt(g.limit()) + "% 초과", g.basis()));
            }
        });

        // ===== 3. 기능성 =====
        List<Map<String, Object>> functional = new ArrayList<>();
        Set<String> wanted = new LinkedHashSet<>();
        if (claims != null) for (String c : claims) if (FormulaRules.CLAIMS.contains(c)) wanted.add(c);

        for (String claim : wanted) {

            List<Map<String, Object>> found = new ArrayList<>();
            boolean ok = false;

            for (Row r : rows) {
                for (Functional f : functionalsOf(r)) {

                    if (!f.claim().equals(claim)) continue;

                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", r.label());
                    m.put("pct", round(r.pct));
                    m.put("basis", f.basis());

                    String verdict;
                    if (f.rinseOffOnly() && !rinseOff) {
                        verdict = "씻어내는 제품만 해당돼요";
                    } else if (f.min() == null) {
                        verdict = f.note();
                    } else if (r.pct + EPS < f.min() || r.pct > f.max() + EPS) {
                        verdict = "고시 함량(" + f.basis() + ")과 달라요 → 자료제출 생략 불가, 유효성 자료로 심사 필요";
                    } else {
                        verdict = "고시 함량 안 → 자료제출 생략 대상";
                        ok = true;
                    }

                    m.put("ok", verdict.startsWith("고시 함량 안"));
                    m.put("verdict", verdict);
                    found.add(m);

                }
            }

            Map<String, Object> c = new LinkedHashMap<>();
            c.put("claim", claim);
            c.put("ok", ok);
            c.put("items", found);
            c.put("message", found.isEmpty()
                    ? "고시 성분이 처방에 없어요."
                    : ok ? "고시 성분·함량을 충족해요." : "고시 함량을 충족하는 성분이 없어요.");
            functional.add(c);

            if (!ok) {
                issues.add(issue("WARN", claim + " 기능성", (String) c.get("message"), "「기능성화장품 심사에 관한 규정」 별표4"));
            }

        }

        // ===== 4. 알레르기 착향제 =====
        double allergenAt = rinseOff ? FormulaRules.ALLERGEN_LABEL_RINSE_OFF : FormulaRules.ALLERGEN_LABEL_LEAVE_ON;
        List<String> allergens = new ArrayList<>();

        for (Row r : rows) {
            if (r.hit != null && "Y".equals(r.hit.getAllergenYn()) && r.pct > allergenAt + EPS) {
                allergens.add(r.label());
                r.allergenLabel = true;
            }
        }

        if (!allergens.isEmpty()) {
            issues.add(issue("INFO", "알레르기 착향제", String.join(", ", allergens) + " → 전성분에 성분명을 표시해야 해요 ("
                    + (rinseOff ? "씻어내는 제품 0.01%" : "씻어내지 않는 제품 0.001%") + " 초과)", "알레르기 유발성분 표시 규정"));
        }

        // ===== 5. 전성분 표시안 =====
        List<Row> ordered = new ArrayList<>();
        List<Row> small = new ArrayList<>();

        for (Row r : rows) {
            if (r.pct > FormulaRules.LABEL_ORDER_FREE_AT + EPS) ordered.add(r);
            else small.add(r);
        }

        ordered.sort(Comparator.comparingDouble((Row r) -> r.pct).reversed().thenComparingInt(r -> r.index));

        List<String> label = new ArrayList<>();
        for (Row r : ordered) label.add(r.label());
        for (Row r : small) label.add(r.label());

        // ===== 사전에 없는 원료 =====
        List<String> unknown = rows.stream().filter(r -> r.hit == null && !r.limited).map(r -> r.raw).toList();
        if (!unknown.isEmpty()) {
            issues.add(issue("INFO", "사전에 없는 원료",
                    String.join(", ", unknown) + " → 이 검토기의 사전·규칙에 없어서 한도를 확인하지 못했어요. 직접 확인해주세요.", null));
        }

        // 심각한 것부터
        issues.sort(Comparator.comparingInt(m -> List.of("ERROR", "WARN", "INFO").indexOf((String) m.get("level"))));

        // ===== 응답 =====
        List<Map<String, Object>> items = new ArrayList<>();
        for (Row r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("input", r.raw);
            m.put("name", r.label());
            m.put("matched", r.hit != null);
            m.put("category", r.hit == null ? null : r.hit.getCategory());
            m.put("pct", round(r.pct));
            m.put("qs", r.qs);
            m.put("allergenLabel", r.allergenLabel);
            items.add(m);
        }

        long errors = issues.stream().filter(m -> "ERROR".equals(m.get("level"))).count();
        long warns = issues.stream().filter(m -> "WARN".equals(m.get("level"))).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", round(total));
        result.put("qsPct", qsCount == 1 ? round(qsPct) : null);
        result.put("rinseOff", rinseOff);
        result.put("kids", kids);
        result.put("errorCount", errors);
        result.put("warnCount", warns);
        result.put("verdict", errors > 0 ? "FAIL" : warns > 0 ? "CHECK" : "PASS");
        result.put("issues", issues);
        result.put("functional", functional);
        result.put("label", String.join(", ", label));
        result.put("items", items);
        result.put("ruleVersion", FormulaRules.RULE_VERSION);

        return result;

    }

    // ============================================
    // 내부
    // ============================================
    private static final class Row {

        final int index;
        final String raw;
        final IngredientVO hit;
        Double pct;
        final boolean qs;
        boolean limited;
        boolean allergenLabel;

        Row(int index, String raw, IngredientVO hit, Double pct, boolean qs) {
            this.index = index;
            this.raw = raw;
            this.hit = hit;
            this.pct = pct == null ? 0 : pct;
            this.qs = qs;
        }

        // 표시 이름: 사전에 있으면 사전 표기, 없으면 입력 그대로
        String label() {
            return hit != null ? hit.getNameKo() : raw;
        }

        String key() {
            return hit != null ? "ID:" + hit.getIngredientId() : IngredientDictionary.normalize(raw);
        }
    }

    private List<Functional> functionalsOf(Row r) {

        List<Functional> list = FormulaRules.functionalsOf(r.label());
        if (list.isEmpty() && r.hit != null) list = FormulaRules.functionalsOf(r.raw);

        return list;

    }

    private static Map<String, Object> issue(String level, String target, String message, String basis) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);       // ERROR 고쳐야 함 / WARN 확인 필요 / INFO 참고
        m.put("target", target);
        m.put("message", message);
        m.put("basis", basis);       // 규정 원문 (없으면 null)

        return m;

    }

    private static double round(double v) {

        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();

    }

    // 0.4 → "0.4", 0.3043 → "0.304", 12.0 → "12"
    static String fmt(double v) {

        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();

    }
}
