package com.sklabs1037.sklabs1037.beauty.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 📣 화장품 광고 문구 검수 규칙 (AI 없이 규칙만)
 *
 * 근거 (요약)
 *   - 「화장품법」 제13조 부당한 표시·광고 금지
 *       ① 1. 의약품으로 잘못 인식할 우려  2. 기능성화장품이 아닌데 기능성으로 오인
 *         3. 천연·유기농 화장품이 아닌데 오인  4. 소비자를 속이거나 잘못 알게 할 우려
 *   - 「화장품법 시행규칙」 [별표 5] 화장품 표시·광고의 범위 및 준수사항
 *       의사·약사·의료기관 등이 추천·개발·공인한다는 표현, 배타성을 띤 "최고"·"최상" 등 절대적 표현, 비교·비방 광고
 *   - 「화장품 표시·광고 실증에 관한 규정」 실증 자료가 있어야 쓸 수 있는 표현 (붓기·다크서클·셀룰라이트 등)
 *   - 기능성화장품 효능 문구 (심사·보고를 받은 경우에만): 미백·주름·자외선·여드름(씻어내는 제품)·탈모·아토피 건조함
 *
 * - 규칙이 바뀌면 이 파일만 고치면 됨 (테스트가 같이 확인)
 * - "쓸 수 없다"는 단정 대신 단계로: ERROR 고칠 것 / WARN 근거·실증 확인 / INFO 참고
 * - 최종 판단은 식약처 「화장품 표시·광고 관리 가이드라인」 원문과 담당 부서 확인 (화면에 같이 표시)
 */
public final class AdCopyRules {

    private AdCopyRules() {
    }

    public static final String RULE_VERSION = "2026-10 기준 (화장품법 제13조 · 시행규칙 별표5 · 실증 규정 요약)";

    // 기능성 효능 (심사·보고 받은 것) → 쓸 수 있는 공식 효능 문구
    public static final Map<String, String> CLAIMS = linked(
            "미백", "피부의 미백에 도움을 줌",
            "주름", "피부의 주름 개선에 도움을 줌",
            "자외선", "자외선으로부터 피부를 보호하는 데 도움을 줌",
            "여드름", "여드름성 피부를 완화하는 데 도움을 줌 (씻어내는 제품만)",
            "탈모", "탈모 증상의 완화에 도움을 줌",
            "아토피", "아토피성 피부로 인한 건조함 등을 완화하는 데 도움을 줌");

    static final String MED = "화장품법 제13조 ①1 (의약품으로 오인)";
    static final String FUNC = "화장품법 제13조 ①2 (기능성화장품 오인)";
    static final String NATURAL = "화장품법 제13조 ①3 (천연·유기농 오인)";
    static final String MISLEAD = "화장품법 제13조 ①4 (소비자 오인)";
    static final String ABS = "시행규칙 [별표 5] (배타성 절대적 표현)";
    static final String DOCTOR = "시행규칙 [별표 5] (의사·약사·의료기관 추천 표현)";
    static final String COMPARE = "시행규칙 [별표 5] (비교·비방 광고)";
    static final String PROOF = "표시·광고 실증에 관한 규정 (실증 자료 필요)";

    public enum Level { ERROR, WARN, INFO }

    /**
     * 규칙 하나
     * @param allowWith 이 기능성을 심사·보고 받았으면 INFO로 내려감 (없으면 null)
     */
    public record Rule(String code, Level level, String category, Pattern pattern,
                       String reason, String suggest, String basis, String allowWith) {
    }

    private static Rule r(String code, Level level, String category, String regex, String reason, String suggest, String basis) {
        return new Rule(code, level, category, Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), reason, suggest, basis, null);
    }

    private static Rule f(String code, Level level, String claim, String regex, String reason, String suggest) {
        return new Rule(code, level, "기능성 오인", Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), reason, suggest, FUNC, claim);
    }

    public static final List<Rule> RULES = List.of(

            // ----- 의약품 오인 (어떤 경우에도 고칠 것) -----
            r("MED_TREAT", Level.ERROR, "의약품 오인", "치료|치유|완치|낫게\\s*하|약효|의약품\\s*(같은|처럼|급)",
                    "질병을 고친다는 뜻이라 의약품으로 오인될 수 있어요.", "\"피부 컨디션을 가꾸는 데 도움\"처럼 화장품 범위로", MED),
            r("MED_INFLAME", Level.ERROR, "의약품 오인", "항염|소염|염증|진통|통증\\s*(완화|해소|감소)?|가려움\\s*(을|이)?\\s*(없애|해소|치료|완화)",
                    "염증·통증·가려움을 다스린다는 표현은 의약품 효능이에요.", "\"건조하고 예민해진 피부를 편안하게\"처럼 사용감으로", MED),
            r("MED_REGEN", Level.ERROR, "의약품 오인", "(피부|세포|조직)\\s*재생|상처\\s*(회복|치유|재생)?|흉터\\s*(제거|개선|완화|치료)?|면역력|유전자|DNA\\s*(복구|회복)",
                    "재생·상처·흉터·면역은 의약품으로 오인되는 표현이에요.", "\"매끈하고 탄탄한 피부결로 가꿔줘요\"처럼 겉모습으로", MED),
            r("MED_STERILE", Level.ERROR, "의약품 오인", "살균|소독|바이러스\\s*(차단|제거|예방)",
                    "살균·소독은 의약품·의약외품 효능이에요.", "\"노폐물을 말끔하게 씻어내요\"처럼 세정으로", MED),
            r("MED_DISEASE", Level.ERROR, "의약품 오인", "피부\\s*질환|습진|피부염|건선|무좀|여드름\\s*(을|이)?\\s*(치료|없애|제거|박멸|예방)",
                    "피부 질환 이름이나 질환을 없앤다는 표현은 쓸 수 없어요.", "기능성 심사를 받았다면 공식 효능 문구만", MED),
            r("MED_BODY", Level.ERROR, "의약품 오인", "지방\\s*(분해|연소|감소)|체지방|체중\\s*(감량|감소)|살\\s*(이)?\\s*빠|독소\\s*(배출|제거)?|디톡스|발모|육모|양모|모발\\s*(이)?\\s*(자라|성장|재생)",
                    "몸의 기능을 바꾼다는 표현은 의약품으로 오인돼요.", "\"매끈해 보이는 바디 라인 케어\"처럼 겉모습으로", MED),
            r("MED_REMOVE", Level.ERROR, "의약품 오인", "(기미|주근깨|잡티|주름|모공|여드름|다크서클|튼살)\\s*(을|를|이|가)?\\s*(완전히\\s*)?(제거|없애|없어|사라|지워)",
                    "없앤다·사라진다는 표현은 의약품 효능처럼 보여요.", "기능성이면 \"~완화에 도움을 줌\", 아니면 \"~가 덜 보이게 커버\"처럼", MED),
            r("MED_PREVENT", Level.ERROR, "의약품 오인", "(탈모|노화|여드름|기미)\\s*(방지|예방|억제)",
                    "방지·예방은 질병을 막는다는 뜻으로 읽혀요.", "기능성이면 공식 효능 문구로, 아니면 \"~케어\"처럼", MED),

            // ----- 기능성 오인 (심사·보고 받았으면 참고) -----
            f("FN_WHITE", Level.ERROR, "미백", "미백|화이트닝|멜라닌|기미|주근깨|잡티\\s*(개선|완화|케어)",
                    "미백 기능성 심사·보고를 받은 제품만 쓸 수 있어요.", "기능성이 아니면 \"맑고 환한 피부 톤으로 가꿔줘요\""),
            f("FN_WRINKLE", Level.ERROR, "주름", "주름\\s*(개선|완화|감소|케어)|링클\\s*케어",
                    "주름 개선 기능성 심사·보고를 받은 제품만 쓸 수 있어요.", "기능성이 아니면 \"촉촉하고 탄력 있어 보이는 피부\""),
            f("FN_UV", Level.ERROR, "자외선", "자외선\\s*(차단|보호)|UV\\s*(차단|보호)|SPF\\s*\\d+|PA\\s*\\+{1,4}|선\\s*블록|썬\\s*블록",
                    "자외선 차단 기능성 심사·보고를 받은 제품만, 심사받은 SPF·PA 수치 그대로 쓸 수 있어요.", "기능성이 아니면 자외선 관련 표현을 빼요"),
            f("FN_HAIR", Level.ERROR, "탈모", "탈모",
                    "탈모 증상 완화 기능성 심사·보고를 받은 제품만 쓸 수 있어요.", "기능성이 아니면 \"힘 없는 모발에 볼륨감\"처럼"),
            f("FN_ATOPY", Level.ERROR, "아토피", "아토피",
                    "아토피 기능성(건조함 완화) 심사·보고를 받은 제품만 쓸 수 있어요.", "기능성이 아니면 \"건조하고 예민한 피부 보습\""),
            f("FN_ACNE", Level.WARN, "여드름", "여드름|트러블\\s*(개선|완화|진정)",
                    "\"여드름성 피부에 사용 적합\"은 실증 자료가 있어야 하고, 여드름 완화는 씻어내는 기능성 제품만 쓸 수 있어요.", "\"피지·각질 케어\"처럼"),
            r("FN_AGE", Level.WARN, "기능성 오인", "안티\\s*에이징|노화\\s*(개선|완화|케어)|역노화|회춘|리프팅|V\\s*라인",
                    "주름 개선 기능성으로 오인되거나 과장으로 보일 수 있어요.", "\"탄력 있어 보이는 피부\"처럼 사용감으로", FUNC),

            // ----- 의사·기관 추천 -----
            r("DR_RECOMMEND", Level.ERROR, "의사·기관 추천", "(의사|약사|한의사|전문의|피부과|병원|의료진|클리닉)\\s*(가|이|들이|에서)?\\s*(추천|개발|공인|인증|처방|선택|지정|사용하는|쓰는)",
                    "의사·약사·의료기관이 추천·개발했다는 표현은 쓸 수 없어요.", "\"피부 전문 연구소에서 개발\"처럼 사실인 범위로", DOCTOR),
            r("DR_MFDS", Level.ERROR, "의사·기관 추천", "식약처\\s*(인증|인정|허가|승인|검증|추천)|식품의약품안전처\\s*(인증|인정|허가|승인|검증|추천)",
                    "식약처가 인증·허가했다는 표현은 오인될 수 있어요.", "기능성이면 \"식약처 기능성화장품 심사(보고) 완료\"만", MISLEAD),
            r("DR_TEST", Level.WARN, "실증 필요", "피부과\\s*테스트|임상\\s*(시험|테스트|완료|입증)|인체\\s*적용\\s*시험|(효과|효능)\\s*(입증|검증)",
                    "시험 기관·대상·기간 등 실증 자료가 있어야 해요. 결과 범위를 넘게 말하면 안 돼요.", "\"○○기관 인체적용시험 완료 (20XX.XX, n=XX)\"처럼 출처와 함께", PROOF),

            // ----- 절대·과장 표현 -----
            r("ABS_BEST", Level.ERROR, "절대적 표현", "최고|최상|독보적|유일무이|세계\\s*최초",
                    "배타성을 띤 절대적 표현은 쓸 수 없어요.", "\"오랜 연구로 완성한\"처럼 구체적 사실로", ABS),
            r("ABS_RANK", Level.WARN, "절대적 표현", "최초|유일|1\\s*위|넘버\\s*원|No\\.?\\s*1|일등|베스트\\s*셀러",
                    "순위·최초는 객관적 근거(조사 기관·기간·대상)를 함께 밝혀야 해요.", "\"○○몰 20XX년 X월 스킨케어 판매 1위\"처럼 출처와 함께", MISLEAD),
            r("ABS_100", Level.ERROR, "절대적 표현", "(효과|효능|개선|만족)\\s*100\\s*%|100\\s*%\\s*(효과|효능|개선|만족)|부작용\\s*(이|이\\s*전혀)?\\s*없|무자극|자극\\s*(이)?\\s*(전혀\\s*)?없|모든\\s*피부\\s*(에|가)?\\s*(안전|문제\\s*없)",
                    "효과를 장담하거나 부작용이 없다는 표현은 소비자를 오인하게 해요.", "\"저자극 테스트 완료(실증 시)\"처럼 확인된 범위로", MISLEAD),
            r("ABS_FAST", Level.WARN, "과장 표현", "즉시|즉각|바로\\s*효과|단\\s*\\d+\\s*(일|주|번|회)\\s*만에|영구|평생|완벽(하게|한)?|확실(하게|한)",
                    "효과가 빠르다·영원하다는 표현은 실증 자료가 없으면 과장이에요.", "\"사용 직후 촉촉함(자사 테스트)\"처럼 근거와 함께", MISLEAD),
            r("ABS_LOW", Level.WARN, "실증 필요", "저자극|논\\s*코메도제닉|넌\\s*코메도제닉|하이포알러제닉|민감\\s*피부\\s*(도|에도)?\\s*(안심|안전)",
                    "저자극·논코메도제닉은 시험으로 실증한 경우에만 쓸 수 있어요.", "시험 결과가 있으면 출처와 함께", PROOF),

            // ----- 실증이 필요한 효능 -----
            r("PR_EFFECT", Level.WARN, "실증 필요", "붓기|부기|다크서클|셀룰라이트|혈행|혈액\\s*순환|모공\\s*(축소|수축|조임)|피지\\s*(조절|억제)|항균|노폐물|콜라겐\\s*(생성|증가|합성)",
                    "실증 규정에 있는 표현이라 시험 자료가 있어야 쓸 수 있어요. \"일시적\" 같은 한정도 필요할 수 있어요.", "근거가 없으면 \"매끈해 보이는\"처럼 겉모습으로", PROOF),
            r("PR_TIMES", Level.WARN, "비교 표현", "\\d+(\\.\\d+)?\\s*배\\s*(더|이상|높은|빠른|강력)?|타사|경쟁사|기존\\s*제품\\s*(보다|대비)|(다른|일반)\\s*제품\\s*(보다|대비)",
                    "비교는 객관적으로 확인되는 사실만, 비교 대상과 기준을 밝혀야 해요. 다른 제품을 깎아내리면 안 돼요.", "\"자사 기존 제품 대비 보습력 2배 (○○시험, 20XX)\"처럼", COMPARE),

            // ----- 천연·유기농 -----
            r("NT_NATURAL", Level.WARN, "천연·유기농", "천연|유기농|오가닉|organic|natural|100\\s*%\\s*(식물|자연)|(?<![가-힣A-Za-z0-9]\\s?)무\\s*첨가|무\\s*화학|화학\\s*성분\\s*(0|없|무)|케미컬\\s*프리|chemical\\s*free",
                    "천연·유기농은 식약처 인증 기준을 맞춘 제품만 쓸 수 있고, \"무첨가\"는 무엇을 뺐는지 밝혀야 해요.", "\"파라벤 무첨가\"처럼 뺀 성분을 구체적으로", NATURAL)
    );

    // ============================================
    // 검사
    // ============================================

    public record Finding(int start, int end, String phrase, Level level, String category, String code,
                          String reason, String suggest, String basis) {
    }

    /**
     * 문구 검사
     * - 같은 자리에 여러 규칙이 걸리면 더 심각한 것 → 더 긴 것 하나만
     * - 기능성을 심사·보고 받았다고 고르면 그 기능성 규칙은 INFO(공식 효능 문구 안내)로
     */
    public static List<Finding> check(String text, Collection<String> claims) {

        if (text == null || text.isBlank()) return List.of();
        Set<String> owned = claims == null ? Set.of() : new HashSet<>(claims);

        List<Finding> all = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(text);
            while (m.find()) {
                if (m.end() == m.start()) continue;
                boolean allowed = rule.allowWith() != null && owned.contains(rule.allowWith());
                Level level = allowed ? Level.INFO : rule.level();
                String reason = allowed ? "기능성 심사·보고를 받았다면 공식 효능 문구 범위 안에서만 써요." : rule.reason();
                String suggest = allowed ? "\"" + CLAIMS.get(rule.allowWith()) + "\"" : rule.suggest();
                all.add(new Finding(m.start(), m.end(), m.group().strip(), level, rule.category(), rule.code(), reason, suggest, rule.basis()));
            }
        }

        all.sort(Comparator.<Finding>comparingInt(x -> x.level().ordinal())
                .thenComparing(x -> -(x.end() - x.start()))
                .thenComparingInt(Finding::start));

        List<Finding> picked = new ArrayList<>();
        for (Finding x : all) {
            boolean overlap = picked.stream().anyMatch(p -> x.start() < p.end() && p.start() < x.end());
            if (!overlap) picked.add(x);
        }

        picked.sort(Comparator.comparingInt(Finding::start));
        return picked;

    }

    public static String verdict(List<Finding> findings) {

        if (findings.stream().anyMatch(x -> x.level() == Level.ERROR)) return "FAIL";
        if (findings.stream().anyMatch(x -> x.level() == Level.WARN)) return "CHECK";
        return "PASS";

    }

    private static Map<String, String> linked(String... kv) {

        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return Collections.unmodifiableMap(m);

    }
}
