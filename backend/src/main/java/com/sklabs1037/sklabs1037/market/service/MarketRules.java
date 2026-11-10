package com.sklabs1037.sklabs1037.market.service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 🛍️ 중고거래 규칙 (순수 계산 → 테스트 가능, DB·AI 없음)
 *
 * - 카테고리 · 상품 상태 · 거래 방법 목록 (화면도 서버가 준 이 목록을 씀)
 * - 글 검사: 제목 2~40자 · 설명 10~1,000자 · 가격 0(나눔)~9,999만 원
 * - 거래 금지 품목(술 · 담배 · 의약품 · 계정 · 신분증 …)이 제목·설명에 있으면 올릴 수 없음
 * - 사기에 자주 쓰이는 말(선입금 · 외부 메신저 유도 …)은 막지는 않고 "주의" 표시 → 상세 화면에 안내
 * - 상태 바꾸기: 판매중 ↔ 예약중 ↔ 판매완료 (숨김은 신고로만, 판매자가 못 품)
 */
public final class MarketRules {

    public static final int MAX_PHOTOS = 5;
    public static final int MAX_PRICE = 99_990_000;
    public static final int REPORTS_TO_HIDE = 3;

    // 코드 → 이름 (보여주는 순서대로)
    public static final Map<String, String> CATEGORIES = ordered(
            "DIGITAL", "디지털·가전", "FASHION", "의류", "ACC", "패션잡화", "BEAUTY", "뷰티·미용",
            "FURNITURE", "가구·인테리어", "LIVING", "생활·주방", "BOOK", "도서·티켓", "SPORTS", "스포츠·레저",
            "HOBBY", "게임·취미", "KIDS", "유아동", "PET", "반려동물", "ETC", "기타");

    public static final Map<String, String> CONDITIONS = ordered("NEW", "새 상품", "LIKE_NEW", "거의 새것", "USED", "사용감 있음");

    public static final Map<String, String> TRADE_TYPES = ordered("BOTH", "직거래·택배", "DIRECT", "직거래만", "DELIVERY", "택배만");

    public static final Map<String, String> STATUSES = ordered("SALE", "판매중", "RESERVED", "예약중", "SOLD", "판매완료", "HIDDEN", "숨김");

    // 거래 금지 품목 (걸리면 등록 불가)
    private static final List<Map.Entry<String, Pattern>> BANNED = List.of(
            Map.entry("담배·전자담배", Pattern.compile("담배|전자\\s*담배|액상\\s*니코틴")),
            Map.entry("술", Pattern.compile("(양주|소주|맥주|와인|위스키|막걸리)\\s*(팝니다|판매|팔아요)|주류\\s*판매")),
            Map.entry("의약품", Pattern.compile("의약품|처방\\s*약|전문\\s*의약|다이어트\\s*약|수면제|비아그라")),
            Map.entry("계정·개인정보", Pattern.compile("계정\\s*(판매|팝니다|양도)|개인\\s*정보\\s*(판매|팝니다)|대포\\s*(통장|폰)|통장\\s*(판매|대여|양도)|신분증|주민\\s*등록\\s*증|운전\\s*면허\\s*증")),
            Map.entry("위험물", Pattern.compile("총기|실탄|도검|마약|대마|필로폰|석궁|전기\\s*충격기")),
            Map.entry("성인용품", Pattern.compile("성인\\s*용품")));

    // 사기에 자주 쓰이는 말 (등록은 되지만 상세 화면에 주의 안내)
    private static final List<Map.Entry<String, Pattern>> CAUTIONS = List.of(
            Map.entry("먼저 입금을 요구해요", Pattern.compile("선\\s*입금|입금\\s*먼저|먼저\\s*입금")),
            Map.entry("다른 메신저로 유도해요", Pattern.compile("카톡\\s*(아이디|id|주세요|으로)|카카오톡\\s*(아이디|id)|텔레그램|라인\\s*(아이디|id)|오픈\\s*채팅")),
            Map.entry("외부 링크로 결제를 유도해요", Pattern.compile("https?://|안전\\s*결제\\s*링크|www\\.")));

    private MarketRules() {
    }

    public record Draft(String category, String title, long price, String description, String cond, String tradeType, String region) {
    }

    // 입력 정리 + 검사 → 문제가 있으면 IllegalArgumentException(사용자에게 보여줄 말)
    public static Draft clean(Map<String, Object> in) {

        String category = str(in.get("category")).toUpperCase(Locale.ROOT);
        if (!CATEGORIES.containsKey(category)) throw new IllegalArgumentException("카테고리를 골라주세요.");

        String title = str(in.get("title")).replaceAll("\\s+", " ");
        if (title.length() < 2 || title.length() > 40) throw new IllegalArgumentException("제목은 2~40자로 써주세요.");

        String description = str(in.get("description")).replace("\r\n", "\n").replaceAll("\n{4,}", "\n\n\n");
        if (description.length() < 10) throw new IllegalArgumentException("설명을 10자 이상 써주세요. (상태 · 구매 시기 · 하자 등)");
        if (description.length() > 1000) throw new IllegalArgumentException("설명은 1,000자까지 쓸 수 있어요.");

        long price;
        try {
            Object p = in.get("price");
            price = p instanceof Number n ? n.longValue() : Long.parseLong(str(p).replaceAll("[,원\\s]", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("가격을 숫자로 넣어주세요. (나눔은 0)");
        }
        if (price < 0 || price > MAX_PRICE) throw new IllegalArgumentException("가격은 0원(나눔)부터 9,999만 원까지예요.");

        String cond = str(in.get("cond")).toUpperCase(Locale.ROOT);
        if (!CONDITIONS.containsKey(cond)) cond = "USED";

        String tradeType = str(in.get("tradeType")).toUpperCase(Locale.ROOT);
        if (!TRADE_TYPES.containsKey(tradeType)) tradeType = "BOTH";

        String region = str(in.get("region")).replaceAll("\\s+", " ");
        if (region.length() > 20) region = region.substring(0, 20);

        String banned = banned(title + "\n" + description);
        if (banned != null) throw new IllegalArgumentException("거래할 수 없는 품목이에요: " + banned);

        return new Draft(category, title, price, description, cond, tradeType, region.isEmpty() ? null : region);

    }

    // 금지 품목 이름 (없으면 null)
    public static String banned(String text) {

        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Pattern> e : BANNED) if (e.getValue().matcher(t).find()) return e.getKey();
        return null;

    }

    // 주의 안내들 (없으면 빈 목록)
    public static List<String> cautions(String text) {

        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Pattern> e : CAUTIONS) if (e.getValue().matcher(t).find()) out.add(e.getKey());
        return out;

    }

    // 판매자가 바꿀 수 있는 상태인지 (숨김으로/숨김에서는 못 바꿈)
    public static boolean canChange(String from, String to) {

        Set<String> open = Set.of("SALE", "RESERVED", "SOLD");
        return open.contains(from) && open.contains(to) && !from.equals(to);

    }

    public static String sortOf(String sort) {

        return switch (str(sort).toUpperCase(Locale.ROOT)) {
            case "LOW" -> "LOW";
            case "HIGH" -> "HIGH";
            case "POPULAR" -> "POPULAR";
            default -> "RECENT";
        };

    }

    // 검색어 정리 (LIKE 특수문자 제거, 30자)
    public static String keyword(String q) {

        String s = str(q).replace("\\", "").replace("%", "").replace("_", " ").replaceAll("\\s+", " ").strip();
        return s.length() > 30 ? s.substring(0, 30) : s;

    }

    private static String str(Object o) {

        return o == null ? "" : String.valueOf(o).strip();

    }

    private static Map<String, String> ordered(String... kv) {

        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return Collections.unmodifiableMap(m);

    }
}
