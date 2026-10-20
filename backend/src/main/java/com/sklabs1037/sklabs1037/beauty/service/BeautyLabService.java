package com.sklabs1037.sklabs1037.beauty.service;

import com.sklabs1037.sklabs1037.beauty.dao.BeautyLabDao;
import com.sklabs1037.sklabs1037.beauty.vo.BeautyLogVO;
import com.sklabs1037.sklabs1037.beauty.vo.IngredientVO;
import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 뷰티 랩 Service
 *
 * 1. 성분 분석 (analyze)
 *    - 사진: Claude Vision이 전성분을 "표기 순서 그대로 읽기만" (정해진 도구 형식으로 받음 → JSON 모양이 항상 같음)
 *    - 글: 붙여 넣은 전성분을 서버가 쉼표·줄바꿈으로 나눔 (AI 호출 없음, 비용 0)
 *    - 판단(역할·기능성·알레르기·피부 타입 주의)은 직접 정리한 성분 사전(T_INGREDIENT)으로
 *      → AI가 성분 효능을 지어내지 않게. 사전에 없는 성분만 AI가 읽으면서 적은 역할을 "AI 추정"으로 표시
 *
 * 2. 기획 브리프 (brief)
 *    - 브랜드 담당자가 자유롭게 쓴 요청 → Claude가 정해진 브리프 양식으로 정리 + 빠진 정보 질문
 *    - 서버가 덧붙임: 규제 확인 포인트(기능성 심사·보고, 착향제 표시, 영유아 제품 등), 성분 사전 기반 추천 성분
 *
 * - 로그인 회원만, 종류별 하루 20번 (Claude 비용)
 * - 결과는 T_BEAUTY_LOG에 JSON으로 저장 → "최근 기록"에서 다시 보기
 */
@Slf4j
@Service
public class BeautyLabService {

    public static final int DAILY_LIMIT = 20;

    // 한 번에 다룰 최대 성분 수 / 성분명 최대 길이
    private static final int MAX_ITEMS = 120;
    private static final int MAX_NAME = 80;

    public static final Set<String> SKIN_TYPES = Set.of("민감성", "건성", "지성", "여드름", "복합성", "중성");

    // 사진에서 성분 읽기
    private static final String VISION_PROMPT = """
            너는 화장품 포장 사진에서 "전성분" 목록을 옮겨 적는 도우미야.
            규칙:
            - 사진에 보이는 전성분을 표기된 순서 그대로, 한글 성분명은 한글 그대로 적어. 영문만 있으면 영문 그대로.
            - 보이지 않거나 잘리거나 흐린 부분은 절대 짐작해서 채우지 말고 note에 적어.
            - 함량 표시(예: 5%, 10,000ppm)는 성분명에서 빼고 적어.
            - role에는 그 성분의 일반적인 역할을 한국어 10자 이내로 (예: 보습, 방부제, 유화제). 확실하지 않으면 빈 문자열.
            - 전성분이 사진에 없으면 readable=false, ingredients는 빈 배열.
            - 반드시 record_label 도구로만 답해.
            """;

    private static final Map<String, Object> RECORD_LABEL_TOOL = Map.of(
            "name", "record_label",
            "description", "화장품 사진에서 읽은 제품 정보와 전성분 목록을 기록한다.",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "readable", Map.of("type", "boolean", "description", "전성분 목록을 읽을 수 있었는지"),
                            "productName", Map.of("type", "string", "description", "제품명 (보이면, 없으면 빈 문자열)"),
                            "productType", Map.of("type", "string", "description", "제품 종류 (예: 토너, 크림, 선크림, 클렌저). 모르면 빈 문자열"),
                            "ingredients", Map.of("type", "array", "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "name", Map.of("type", "string"),
                                            "role", Map.of("type", "string")),
                                    "required", List.of("name"))),
                            "note", Map.of("type", "string", "description", "잘리거나 흐려서 못 읽은 부분 등")),
                    "required", List.of("readable", "ingredients")));

    // 기획 요청 → 브리프
    private static final String BRIEF_PROMPT = """
            너는 화장품 ODM 회사의 제품 기획 담당자야. 브랜드 담당자가 자유롭게 쓴 요청을, 연구소가 바로 검토할 수 있는 개발 브리프로 정리해.
            규칙:
            - 요청에 있는 내용만 정리하고, 없는 수치(단가, 용량, 일정 등)는 지어내지 말고 빈 문자열로 둬.
            - 애매하거나 빠진 정보는 missingQuestions에 브랜드에 되물을 질문으로 3~6개 적어. (예: 목표 단가, 용량, 출시 일정, 판매 채널, 원하는 인증)
            - keyBenefits는 정해진 값 중에서 골라.
            - mustHave는 요청에 언급된 꼭 넣고 싶은 성분, avoid는 빼달라는 성분이나 개념(예: 향료, 파라벤, 알코올, 실리콘).
            - summary는 연구소에 전달할 한두 문장 요약.
            - 반드시 write_brief 도구로만 답해.
            """;

    private static final List<String> BENEFITS = List.of(
            "보습", "진정", "미백", "주름", "탄력", "자외선차단", "각질", "트러블", "피지조절", "장벽", "쿨링", "세정");

    private static final Map<String, Object> WRITE_BRIEF_TOOL = Map.of(
            "name", "write_brief",
            "description", "제품 개발 브리프를 작성한다.",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", briefProperties(),
                    "required", List.of("productName", "category", "texture", "keyBenefits", "missingQuestions", "summary")));

    // 브리프에서 "빼주세요"를 사전 성분으로 바꾸는 규칙
    private static final Map<String, List<String>> AVOID_KEYWORDS = Map.of(
            "향료", List.of("향료"),
            "파라벤", List.of("메틸파라벤", "프로필파라벤", "부틸파라벤"),
            "알코올", List.of("에탄올"),
            "실리콘", List.of("다이메티콘", "사이클로펜타실록세인"),
            "설페이트", List.of("소듐라우릴설페이트", "소듐라우레스설페이트", "암모늄라우릴설페이트"),
            "미네랄오일", List.of("미네랄오일", "페트롤라툼"),
            "페녹시에탄올", List.of("페녹시에탄올"));

    // 효능 → 추천할 사전 성분 (역할·기능성 기준)
    private static final Map<String, List<String>> BENEFIT_PICKS = Map.ofEntries(
            Map.entry("보습", List.of("글리세린", "소듐하이알루로네이트", "판테놀", "베타인", "트레할로스")),
            Map.entry("장벽", List.of("세라마이드엔피", "콜레스테롤", "스쿠알란", "판테놀")),
            Map.entry("진정", List.of("병풀추출물", "마데카소사이드", "알란토인", "판테놀", "약모밀추출물")),
            Map.entry("미백", List.of("나이아신아마이드", "알부틴", "에틸아스코빌에터", "아스코빌글루코사이드")),
            Map.entry("주름", List.of("아데노신", "레티놀", "레티닐팔미테이트")),
            Map.entry("탄력", List.of("팔미토일펜타펩타이드-4", "아세틸헥사펩타이드-8", "아데노신")),
            Map.entry("자외선차단", List.of("징크옥사이드", "티타늄디옥사이드", "비스-에틸헥실옥시페놀메톡시페닐트라이아진", "에틸헥실트라이아존")),
            Map.entry("각질", List.of("글루코노락톤", "만델릭애시드", "락틱애시드")),
            Map.entry("트러블", List.of("살리실릭애시드", "약모밀추출물", "티트리잎오일", "병풀추출물")),
            Map.entry("피지조절", List.of("나이아신아마이드", "살리실릭애시드", "녹차추출물")),
            Map.entry("쿨링", List.of("엘-멘톨", "알로에베라잎추출물")),
            Map.entry("세정", List.of("코카미도프로필베타인", "데실글루코사이드", "소듐코코일글루타메이트")));

    private static final Pattern SPLIT = Pattern.compile("(?<!\\d),|,(?!\\d)|\\n|;|·|ㆍ");

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private IngredientDictionary dictionary;

    @Autowired
    private BeautyLabDao beautyLabDao;

    @Autowired
    private JsonMapper jsonMapper;

    // ============================================
    // 1. 성분 분석
    // ============================================
    public Map<String, Object> analyze(Long userId, String imageBase64, String mediaType, String text, String skinType) {

        checkLimit(userId, "ANALYZE");

        Read read = readItems(imageBase64, mediaType, text, null);

        Map<String, Object> result = buildAnalysis(read.items(), skinType);
        result.put("inputType", read.image() ? "IMAGE" : "TEXT");
        result.put("productName", read.productName());
        result.put("productType", read.productType());
        result.put("readNote", read.note());
        result.put("skinType", skinType);

        result.put("logId", saveLog(userId, "ANALYZE", read.image() ? "IMAGE" : "TEXT", read.title(), read.items().size(), result));
        result.put("remaining", Math.max(0, DAILY_LIMIT - countToday(userId, "ANALYZE")));

        return result;

    }

    // 사진 또는 글에서 읽은 전성분
    record Read(List<String[]> items, String productName, String productType, String note, boolean image) {

        // 기록 제목: 제품명 → 없으면 "정제수 외 24개"
        String title() {
            return !productName.isEmpty() ? productName : items.get(0)[0] + " 외 " + (items.size() - 1) + "개";
        }
    }

    // 전성분 읽기 (사진: Claude Vision이 읽기만 / 글: 서버가 나눔, AI 호출 없음)
    // - label: 비교에서 "A 제품" 같은 이름 (오류 메시지에 붙임), name: 직접 적은 제품명
    @SuppressWarnings("unchecked")
    private Read readItems(String imageBase64, String mediaType, String text, String label) {

        boolean isImage = imageBase64 != null && !imageBase64.isBlank();
        String who = label == null ? "" : label + ": ";
        String productName = "";
        String productType = "";
        String note = "";
        List<String[]> items = new ArrayList<>();   // [이름, AI 역할]

        if (isImage) {

            if (!claudeApiClient.isConfigured()) throw new IllegalStateException("AI 기능이 설정되지 않았습니다.");

            Map<String, Object> image = Map.of("type", "image", "source",
                    Map.of("type", "base64", "media_type", mediaType, "data", imageBase64));
            Map<String, Object> ask = Map.of("type", "text", "text", "이 화장품 사진의 전성분을 읽어서 record_label로 기록해줘.");

            Map<String, Object> response = claudeApiClient.createMessage(VISION_PROMPT, List.of(RECORD_LABEL_TOOL),
                    List.of(Map.of("role", "user", "content", List.of(image, ask))),
                    Map.of("type", "tool", "name", "record_label"), 4096);

            Map<String, Object> input = toolInput(response, "record_label");

            if (!Boolean.TRUE.equals(input.get("readable"))) {
                throw new IllegalArgumentException(who + "사진에서 전성분을 찾지 못했어요. 성분표가 크고 선명하게 보이게 다시 찍어주세요."
                        + (str(input.get("note")).isEmpty() ? "" : " (" + str(input.get("note")) + ")"));
            }

            productName = cut(str(input.get("productName")), 100);
            productType = cut(str(input.get("productType")), 30);
            note = cut(str(input.get("note")), 300);

            Object list = input.get("ingredients");
            if (list instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m) {
                        String name = cut(str(m.get("name")).trim(), MAX_NAME);
                        if (!name.isEmpty()) items.add(new String[]{name, cut(str(m.get("role")).trim(), 20)});
                    }
                }
            }

        } else {

            for (String name : splitIngredients(text)) items.add(new String[]{name, ""});

        }

        if (items.isEmpty()) throw new IllegalArgumentException(who + "성분을 하나도 찾지 못했어요.");
        if (items.size() > MAX_ITEMS) items = new ArrayList<>(items.subList(0, MAX_ITEMS));

        return new Read(items, productName, productType, note, isImage);

    }

    // ============================================
    // 1-2. 두 제품 성분 비교
    // ============================================
    @SuppressWarnings("unchecked")
    public Map<String, Object> compare(Long userId, Map<String, String> a, Map<String, String> b, String skinType) {

        checkLimit(userId, "COMPARE");

        Read ra = readItems(a.get("image"), a.get("mediaType"), a.get("text"), "A 제품");
        Read rb = readItems(b.get("image"), b.get("mediaType"), b.get("text"), "B 제품");

        Map<String, Object> resA = buildAnalysis(ra.items(), skinType);
        Map<String, Object> resB = buildAnalysis(rb.items(), skinType);

        String nameA = firstNonBlank(a.get("name"), ra.productName(), "A 제품");
        String nameB = firstNonBlank(b.get("name"), rb.productName(), "B 제품");
        resA.put("productName", nameA);
        resB.put("productName", nameB);

        // 성분 이름을 사전 기준으로 맞춰서 비교 (사전에 없으면 표기 차이만 맞춘 이름)
        Map<String, String> keysA = compareKeys(ra.items());
        Map<String, String> keysB = compareKeys(rb.items());

        List<String> common = new ArrayList<>();
        List<String> onlyA = new ArrayList<>();
        List<String> onlyB = new ArrayList<>();

        keysA.forEach((k, label) -> (keysB.containsKey(k) ? common : onlyA).add(label));
        keysB.forEach((k, label) -> { if (!keysA.containsKey(k)) onlyB.add(label); });

        int union = keysA.size() + keysB.size() - common.size();
        int similarity = union == 0 ? 0 : Math.round(common.size() * 100f / union);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("a", resA);
        result.put("b", resB);
        result.put("common", common);
        result.put("onlyA", onlyA);
        result.put("onlyB", onlyB);
        result.put("similarity", similarity);
        result.put("skinType", skinType);
        result.put("verdicts", verdicts(nameA, nameB, resA, resB, similarity, skinType));

        boolean image = ra.image() || rb.image();
        result.put("logId", saveLog(userId, "COMPARE", image ? "IMAGE" : "TEXT", cut(nameA + " vs " + nameB, 200),
                ra.items().size() + rb.items().size(), result));
        result.put("remaining", Math.max(0, DAILY_LIMIT - countToday(userId, "COMPARE")));

        return result;

    }

    // 비교용 이름 (사전 한글명 → 없으면 정규화한 이름) → 화면에 보일 이름
    private Map<String, String> compareKeys(List<String[]> items) {

        Map<String, String> keys = new LinkedHashMap<>();

        for (String[] item : items) {
            IngredientVO hit = dictionary.find(item[0]);
            String label = hit != null ? hit.getNameKo() : item[0];
            keys.putIfAbsent(hit != null ? "#" + hit.getIngredientId() : IngredientDictionary.normalize(item[0]), label);
        }

        return keys;

    }

    // 비교 요약 문장 (규칙으로)
    @SuppressWarnings("unchecked")
    private List<String> verdicts(String nameA, String nameB, Map<String, Object> a, Map<String, Object> b, int similarity, String skinType) {

        List<String> out = new ArrayList<>();

        out.add("두 제품의 성분이 " + similarity + "% 겹쳐요." + (similarity >= 60 ? " 꽤 비슷한 처방이에요." : similarity <= 25 ? " 처방이 많이 달라요." : ""));

        Set<String> fa = ((Map<String, List<String>>) a.get("functional")).keySet();
        Set<String> fb = ((Map<String, List<String>>) b.get("functional")).keySet();
        for (String t : fa) if (!fb.contains(t)) out.add(nameA + "에만 " + t + " 기능성 고시 성분이 들어 있어요.");
        for (String t : fb) if (!fa.contains(t)) out.add(nameB + "에만 " + t + " 기능성 고시 성분이 들어 있어요.");

        int alA = ((List<?>) a.get("allergens")).size();
        int alB = ((List<?>) b.get("allergens")).size();
        boolean frA = Boolean.TRUE.equals(a.get("fragrance"));
        boolean frB = Boolean.TRUE.equals(b.get("fragrance"));

        if (frA != frB) out.add((frA ? nameB : nameA) + "은(는) 향료가 표시돼 있지 않아요.");
        if (alA != alB) out.add("알레르기 유발 가능 착향제: " + nameA + " " + alA + "개 / " + nameB + " " + alB + "개");

        if (skinType != null) {
            int ca = ((List<?>) a.get("skinCautions")).size();
            int cb = ((List<?>) b.get("skinCautions")).size();
            out.add(ca == cb
                    ? skinType + " 피부 기준 주의 성분 수는 같아요 (" + ca + "개)."
                    : skinType + " 피부 기준으로는 " + (ca < cb ? nameA : nameB) + "의 주의 성분이 더 적어요 (" + Math.min(ca, cb) + "개 vs " + Math.max(ca, cb) + "개).");
        }

        return out;

    }

    private String firstNonBlank(String... values) {

        for (String v : values) if (v != null && !v.isBlank()) return cut(v.trim(), 60);

        return "";

    }

    // 사전으로 판단
    private Map<String, Object> buildAnalysis(List<String[]> items, String skinType) {

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, List<String>> functional = new LinkedHashMap<>();
        List<String> allergens = new ArrayList<>();
        List<Map<String, Object>> skinCautions = new ArrayList<>();
        List<String> preservatives = new ArrayList<>();
        Map<String, Integer> categories = new LinkedHashMap<>();
        boolean fragrance = false;
        int matched = 0;
        Set<Long> seen = new HashSet<>();

        for (int i = 0; i < items.size(); i++) {

            String name = items.get(i)[0];
            IngredientVO hit = dictionary.find(name);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("order", i + 1);
            row.put("name", name);
            row.put("matched", hit != null);

            if (hit != null) {

                matched++;
                row.put("nameKo", hit.getNameKo());
                row.put("nameEn", hit.getNameEn());
                row.put("category", hit.getCategory());
                row.put("description", hit.getDescription());
                row.put("functional", hit.getFunctional());
                row.put("allergen", "Y".equals(hit.getAllergenYn()));
                row.put("caution", hit.getCaution());

                boolean first = seen.add(hit.getIngredientId());

                if (first) {

                    categories.merge(hit.getCategory(), 1, Integer::sum);
                    if (hit.getFunctional() != null) functional.computeIfAbsent(hit.getFunctional(), k -> new ArrayList<>()).add(hit.getNameKo());
                    if ("Y".equals(hit.getAllergenYn())) allergens.add(hit.getNameKo());
                    if ("향료".equals(hit.getNameKo())) fragrance = true;
                    if (hit.getCategory().contains("방부")) preservatives.add(hit.getNameKo());

                    boolean forSkin = skinType != null && hit.getCautionSkin() != null
                            && Arrays.asList(hit.getCautionSkin().split(",")).contains(skinType);
                    row.put("skinCaution", forSkin);

                    if (forSkin) {
                        Map<String, Object> c = new LinkedHashMap<>();
                        c.put("name", hit.getNameKo());
                        c.put("caution", hit.getCaution());
                        skinCautions.add(c);
                    }

                }

            } else {

                String role = items.get(i)[1];
                row.put("aiRole", role.isEmpty() ? null : role);   // 사전에 없을 때만, "AI 추정"으로 표시

            }

            rows.add(row);

        }

        // 한눈에 보는 요약 문장 (규칙으로 만듦)
        List<String> highlights = new ArrayList<>();

        List<String> top = items.subList(0, Math.min(5, items.size())).stream().map(x -> x[0]).toList();
        highlights.add("앞쪽 성분일수록 많이 들어 있어요. 이 제품의 바탕(앞쪽 " + top.size() + "개): " + String.join(", ", top));

        functional.forEach((type, names) -> highlights.add(
                type + " 기능성 고시 성분이 들어 있어요: " + String.join(", ", names)
                        + " (포장에 '기능성화장품' 표시가 있어야 효능을 인정받은 제품이에요)"));

        if (!allergens.isEmpty()) {
            highlights.add("알레르기 유발 가능 착향제 " + allergens.size() + "개(" + String.join(", ", allergens) + ")가 표시돼 있어요.");
        }

        if (fragrance) highlights.add("향료가 들어 있어요. 향에 민감하다면 무향 제품도 비교해보세요.");
        if (!fragrance && allergens.isEmpty()) highlights.add("향료와 알레르기 유발 가능 착향제가 표시돼 있지 않아요.");

        highlights.add(preservatives.isEmpty()
                ? "사전에 있는 방부제는 보이지 않아요. (1,2-헥산다이올 같은 방부 보조 성분으로 대신하는 제품도 많아요)"
                : "방부제: " + String.join(", ", preservatives));

        if (skinType != null) {
            highlights.add(skinCautions.isEmpty()
                    ? skinType + " 피부에 특히 주의가 필요한 성분은 사전 기준으로 보이지 않아요."
                    : skinType + " 피부라면 " + skinCautions.size() + "개 성분을 확인해보세요.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", items.size());
        result.put("matchedCount", matched);
        result.put("highlights", highlights);
        result.put("functional", functional);
        result.put("allergens", allergens);
        result.put("fragrance", fragrance);
        result.put("skinCautions", skinCautions);
        result.put("categories", categories);
        result.put("ingredients", rows);

        return result;

    }

    // ============================================
    // 2. 기획 브리프
    // ============================================
    public Map<String, Object> brief(Long userId, String request) {

        checkLimit(userId, "BRIEF");
        if (!claudeApiClient.isConfigured()) throw new IllegalStateException("AI 기능이 설정되지 않았습니다.");

        Map<String, Object> response = claudeApiClient.createMessage(BRIEF_PROMPT, List.of(WRITE_BRIEF_TOOL),
                List.of(Map.of("role", "user", "content", request)),
                Map.of("type", "tool", "name", "write_brief"), 3000);

        Map<String, Object> b = toolInput(response, "write_brief");

        // AI가 준 값 정리 (모양·길이 고정)
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("productName", cut(str(b.get("productName")), 60));
        brief.put("category", cut(str(b.get("category")), 20));
        brief.put("target", cut(str(b.get("target")), 120));
        brief.put("skinTypes", strings(b.get("skinTypes"), 6, 10));
        brief.put("texture", cut(str(b.get("texture")), 120));
        List<String> benefits = strings(b.get("keyBenefits"), 6, 10).stream().filter(BENEFITS::contains).toList();
        brief.put("keyBenefits", benefits);
        brief.put("mustHave", strings(b.get("mustHave"), 10, 40));
        brief.put("avoid", strings(b.get("avoid"), 10, 40));
        brief.put("fragrance", cut(str(b.get("fragrance")), 80));
        brief.put("volume", cut(str(b.get("volume")), 40));
        brief.put("targetPrice", cut(str(b.get("targetPrice")), 60));
        brief.put("packaging", cut(str(b.get("packaging")), 80));
        brief.put("claims", strings(b.get("claims"), 8, 60));
        brief.put("channel", cut(str(b.get("channel")), 60));
        brief.put("timeline", cut(str(b.get("timeline")), 60));
        brief.put("missingQuestions", strings(b.get("missingQuestions"), 8, 120));
        brief.put("summary", cut(str(b.get("summary")), 400));

        // 서버가 덧붙이는 것
        @SuppressWarnings("unchecked")
        List<String> avoid = (List<String>) brief.get("avoid");
        @SuppressWarnings("unchecked")
        List<String> skinTypes = (List<String>) brief.get("skinTypes");
        @SuppressWarnings("unchecked")
        List<String> mustHave = (List<String>) brief.get("mustHave");

        boolean sensitive = skinTypes.stream().anyMatch(s -> s.contains("민감"))
                || avoid.stream().anyMatch(a -> a.contains("향"));

        Set<String> avoidNames = avoidIngredients(avoid);

        brief.put("suggestions", suggest(benefits, avoidNames, sensitive));
        brief.put("mustHaveCheck", mustHave.stream().map(name -> {
            IngredientVO hit = dictionary.find(name);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("known", hit != null);
            m.put("description", hit == null ? null : hit.getDescription());
            m.put("functional", hit == null ? null : hit.getFunctional());
            return m;
        }).toList());
        brief.put("avoidIngredients", List.copyOf(avoidNames));
        brief.put("regulatory", regulatory(brief, benefits, sensitive, request));
        brief.put("request", request);

        String title = !str(brief.get("productName")).isEmpty() ? str(brief.get("productName")) : cut(request.replaceAll("\\s+", " "), 40);
        brief.put("logId", saveLog(userId, "BRIEF", "TEXT", title, null, brief));
        brief.put("remaining", Math.max(0, DAILY_LIMIT - countToday(userId, "BRIEF")));

        return brief;

    }

    // 효능별 추천 성분 (빼달라는 성분·민감성이면 알레르기 착향제 제외)
    private List<Map<String, Object>> suggest(List<String> benefits, Set<String> avoidNames, boolean sensitive) {

        List<Map<String, Object>> out = new ArrayList<>();

        for (String benefit : benefits) {

            List<Map<String, Object>> picks = new ArrayList<>();

            for (String name : BENEFIT_PICKS.getOrDefault(benefit, List.of())) {

                if (picks.size() >= 4) break;

                IngredientVO i = dictionary.find(name);
                if (i == null || avoidNames.contains(i.getNameKo())) continue;
                if (sensitive && ("Y".equals(i.getAllergenYn()) || (i.getCautionSkin() != null && i.getCautionSkin().contains("민감성")))) continue;

                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", i.getNameKo());
                p.put("description", i.getDescription());
                p.put("functional", i.getFunctional());
                picks.add(p);

            }

            if (!picks.isEmpty()) {
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("benefit", benefit);
                group.put("ingredients", picks);
                out.add(group);
            }

        }

        return out;

    }

    // "향료 빼주세요", "파라벤 프리" → 사전 성분 이름
    private Set<String> avoidIngredients(List<String> avoid) {

        Set<String> names = new LinkedHashSet<>();

        for (String a : avoid) {

            AVOID_KEYWORDS.forEach((keyword, list) -> {
                if (a.contains(keyword)) names.addAll(list);
            });

            IngredientVO hit = dictionary.find(a);
            if (hit != null) names.add(hit.getNameKo());

            // 향료를 빼면 알레르기 착향제도 같이
            if (a.contains("향")) {
                dictionary.all().stream().filter(i -> "Y".equals(i.getAllergenYn())).forEach(i -> names.add(i.getNameKo()));
            }

        }

        return names;

    }

    // 규제·표시 확인 포인트 (확정 판단이 아니라 "확인할 것" 목록)
    private List<String> regulatory(Map<String, Object> brief, List<String> benefits, boolean sensitive, String request) {

        List<String> notes = new ArrayList<>();
        String claims = String.join(" ", strings(brief.get("claims"), 20, 100)) + " " + request;

        List<String> functional = benefits.stream().filter(b -> List.of("미백", "주름", "자외선차단").contains(b)).toList();
        if (!functional.isEmpty()) {
            notes.add(String.join("·", functional) + " 효능을 표방하려면 기능성화장품 심사 또는 보고가 필요해요. 고시 성분·함량 범위를 쓰면 보고로 간소화할 수 있어요.");
        }

        if (benefits.contains("트러블") || claims.contains("여드름")) {
            notes.add("'여드름성 피부 완화' 기능성은 씻어내는 제품(인체 세정용)에만 인정돼요. 바르는 제품은 '진정'·'피지 케어' 같은 일반 표현으로 검토해요.");
        }

        if (claims.contains("탈모")) {
            notes.add("탈모 증상 완화 기능성은 고시 성분(덱스판테놀, 비오틴, 엘-멘톨, 징크피리치온) 기준을 확인해요.");
        }

        if (sensitive) {
            notes.add("민감성 대상이라 향료와 알레르기 유발 가능 착향제 25종은 빼는 걸 권해요. 들어가면 씻어내지 않는 제품 0.001%, 씻어내는 제품 0.01% 초과 시 성분명을 표시해야 해요.");
        }

        if (claims.contains("천연") || claims.contains("유기농") || claims.contains("오가닉")) {
            notes.add("'천연화장품'·'유기농화장품' 표시는 식약처 인증 기준(함량 등)을 충족해야 해요.");
        }

        if (claims.contains("비건")) {
            notes.add("'비건'은 법정 인증이 아니라 민간 인증이에요. 쓰려는 인증 기관 기준을 확인해요.");
        }

        if (claims.contains("유아") || claims.contains("어린이") || claims.contains("키즈") || claims.contains("아기")) {
            notes.add("영유아·어린이용으로 표시·광고하면 제품별 안전성 자료를 작성·보관해야 해요.");
        }

        if (claims.contains("의약") || claims.contains("치료") || claims.contains("재생") || claims.contains("아토피")) {
            notes.add("'치료', '재생', 질병 이름 같은 의약품 오인 표현은 화장품 광고에 쓸 수 없어요. 문구를 다시 검토해요.");
        }

        if (notes.isEmpty()) notes.add("일반 화장품 기준으로 보여요. 광고 문구가 정해지면 표시·광고 기준을 한 번 더 확인해요.");

        return notes;

    }

    // ============================================
    // 기록
    // ============================================
    public List<BeautyLogVO> myLogs(Long userId) {

        BeautyLogVO param = new BeautyLogVO();
        param.setUserId(userId);
        param.setLimit(20);

        return beautyLabDao.selectMyLogs(param);

    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> myLog(Long userId, Long logId) {

        BeautyLogVO param = new BeautyLogVO();
        param.setUserId(userId);
        param.setLogId(logId);

        BeautyLogVO saved = beautyLabDao.selectMyLog(param);
        if (saved == null) return null;

        Map<String, Object> result = jsonMapper.readValue(saved.getResultJson(), Map.class);
        result.put("kind", saved.getKind());
        result.put("logId", saved.getLogId());

        return result;

    }

    public int remaining(Long userId, String kind) {

        return Math.max(0, DAILY_LIMIT - countToday(userId, kind));

    }

    // ============================================
    // 공통
    // ============================================

    private void checkLimit(Long userId, String kind) {

        if (countToday(userId, kind) >= DAILY_LIMIT) {
            throw new IllegalArgumentException("오늘은 " + DAILY_LIMIT + "번까지 쓸 수 있어요. 내일 다시 시도해주세요.");
        }

    }

    private int countToday(Long userId, String kind) {

        BeautyLogVO param = new BeautyLogVO();
        param.setUserId(userId);
        param.setKind(kind);

        return beautyLabDao.countToday(param);

    }

    private Long saveLog(Long userId, String kind, String inputType, String title, Integer count, Map<String, Object> result) {

        try {

            BeautyLogVO vo = new BeautyLogVO();
            vo.setUserId(userId);
            vo.setKind(kind);
            vo.setInputType(inputType);
            vo.setTitle(cut(title, 200));
            vo.setItemCount(count);
            vo.setResultJson(jsonMapper.writeValueAsString(result));
            beautyLabDao.insertLog(vo);

            return vo.getLogId();

        } catch (Exception e) {

            // 기록 저장이 실패해도 결과는 보여줌
            log.warn("뷰티 랩 기록 저장 실패: {}", e.getMessage());
            return null;

        }

    }

    // 강제한 도구의 입력(JSON)만 꺼내기
    @SuppressWarnings("unchecked")
    private Map<String, Object> toolInput(Map<String, Object> response, String toolName) {

        Object content = response == null ? null : response.get("content");

        if (content instanceof List<?> blocks) {
            for (Object block : blocks) {
                if (block instanceof Map<?, ?> m && "tool_use".equals(m.get("type")) && toolName.equals(m.get("name"))
                        && m.get("input") instanceof Map<?, ?> input) {
                    return (Map<String, Object>) input;
                }
            }
        }

        throw new IllegalStateException("AI 응답에서 결과를 찾지 못했어요.");

    }

    // 붙여넣은 전성분 글 → 성분 이름 목록 (AI 호출 없음, 🧴 내 화장대에서도 같이 씀)
    static List<String> splitIngredients(String text) {

        List<String> names = new ArrayList<>();

        for (String part : SPLIT.split(stripLabel(text))) {
            String name = part.trim();
            if (name.length() > MAX_NAME) name = name.substring(0, MAX_NAME);
            if (!name.isEmpty()) names.add(name);
        }

        return names;

    }

    // "[전성분]", "전성분:" 같은 머리말 빼기
    private static String stripLabel(String text) {

        if (text == null) return "";

        return text.replaceFirst("^\\s*[\\[【(]?\\s*(전\\s*성분|성분|ingredients)\\s*[\\]】)]?\\s*[:：]?", "");

    }

    private List<String> strings(Object value, int maxItems, int maxLength) {

        if (!(value instanceof List<?> list)) return List.of();

        return list.stream()
                .map(this::str)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> cut(s, maxLength))
                .limit(maxItems)
                .toList();

    }

    private String str(Object value) {

        return value == null ? "" : String.valueOf(value);

    }

    private String cut(String text, int max) {

        if (text == null) return "";

        return text.length() > max ? text.substring(0, max) : text;

    }

    // 브리프 입력 형식
    private static Map<String, Object> briefProperties() {

        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> strArray = Map.of("type", "array", "items", Map.of("type", "string"));

        p.put("productName", Map.of("type", "string", "description", "가제 (요청에 없으면 특징을 살린 짧은 가제)"));
        p.put("category", Map.of("type", "string", "description", "스킨케어 / 메이크업 / 선케어 / 클렌징 / 헤어 / 바디 중 하나 + 제품 종류 (예: 스킨케어 - 크림)"));
        p.put("target", Map.of("type", "string", "description", "타깃 고객 (나이·성별·특징)"));
        p.put("skinTypes", Map.of("type", "array", "items", Map.of("type", "string"), "description", "대상 피부 타입 (민감성, 건성, 지성, 복합성, 여드름 등)"));
        p.put("texture", Map.of("type", "string", "description", "제형·사용감 (예: 가벼운 젤 크림, 끈적임 없음)"));
        p.put("keyBenefits", Map.of("type", "array", "items", Map.of("type", "string", "enum", BENEFITS), "description", "핵심 효능"));
        p.put("mustHave", Map.of("type", "array", "items", Map.of("type", "string"), "description", "꼭 넣고 싶은 성분"));
        p.put("avoid", Map.of("type", "array", "items", Map.of("type", "string"), "description", "빼달라는 성분·개념"));
        p.put("fragrance", Map.of("type", "string", "description", "향 (예: 무향, 은은한 플로럴)"));
        p.put("volume", Map.of("type", "string", "description", "용량 (요청에 없으면 빈 문자열)"));
        p.put("targetPrice", Map.of("type", "string", "description", "목표 원가·단가 (요청에 없으면 빈 문자열)"));
        p.put("packaging", Map.of("type", "string", "description", "용기 (예: 튜브, 펌프)"));
        p.put("claims", strArray);
        p.put("channel", Map.of("type", "string", "description", "판매 채널 (올리브영, 해외 등)"));
        p.put("timeline", Map.of("type", "string", "description", "출시 일정"));
        p.put("missingQuestions", Map.of("type", "array", "items", Map.of("type", "string"), "description", "브랜드에 되물을 질문 3~6개"));
        p.put("summary", Map.of("type", "string", "description", "연구소 전달용 한두 문장 요약"));

        return p;

    }
}
