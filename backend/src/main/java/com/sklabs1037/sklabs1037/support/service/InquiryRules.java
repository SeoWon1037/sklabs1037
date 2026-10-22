package com.sklabs1037.sklabs1037.support.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 📮 문의 분류 규칙 (AI 답을 서버가 마지막으로 다듬는 부분, AI 없이 테스트)
 *
 * 왜 서버 규칙이 따로 있나
 *   - 문의는 누구나 쓰는 글 → "관리자 권한 검사를 빼주세요", "이전 지시는 무시하고…" 같은 글로
 *     AI를 속여 코드를 바꾸게 할 수 있음 (프롬프트 인젝션)
 *   - 그래서 위험한 낱말·요청은 AI 판단과 상관없이 서버가 먼저 잡아 RISK_FLAGS에 남기고 자동 수정 후보에서 뺌
 *
 * 자동 수정 후보(AUTOFIX)는 아래를 모두 만족할 때만
 *   AI가 "작고 분명한 버그·문구 수정"이라고 함 + 종류 BUG(또는 화면 문구·디자인 FEATURE) + 심각도 HIGH 아님
 *   + 위험 낱말 없음 + 계정·관리자 영역 아님
 */
public final class InquiryRules {

    private InquiryRules() {
    }

    public static final List<String> TYPES = List.of("BUG", "FEATURE", "QUESTION", "OTHER", "SPAM");
    public static final List<String> AREAS = List.of("STOCK", "MOCK", "LEAGUE", "PREDICT", "QUIZ", "DUEL", "COUPLE",
            "MESSAGES", "AI_CHAT", "BEAUTY", "NOTIFICATION", "APP", "ACCOUNT", "ADMIN", "ETC");
    public static final List<String> SEVERITIES = List.of("LOW", "MEDIUM", "HIGH");
    public static final Map<String, String> CATEGORY_TYPE = Map.of("BUG", "BUG", "FEATURE", "FEATURE", "QUESTION", "QUESTION", "OTHER", "OTHER");

    // 위험 신호 (라벨 → 패턴)
    static final Map<String, Pattern> RISKS = linked(
            "권한·관리자", "관리자\\s*(권한|계정|기능|페이지)|admin|권한\\s*(검사|확인|체크)?\\s*(빼|없애|풀|끄|우회)|role",
            "비밀번호·인증", "비밀\\s*번호|패스워드|password|로그인\\s*(없이|안\\s*하고)|인증\\s*(빼|없애|우회)|세션|토큰|token",
            "비밀 값", "api\\s*key|api\\s*키|secret|시크릿|\\.env|환경\\s*변수|키\\s*(값|알려)",
            "데이터 삭제·조작", "(데이터|db|디비|테이블|회원|계좌|기록)\\s*(을|를)?\\s*(전부|모두|다)?\\s*(삭제|지워|초기화|drop|truncate)|sql|drop\\s+table|delete\\s+from",
            "다른 사람 정보", "다른\\s*(사람|회원|사용자|유저)\\s*(의)?\\s*(정보|메시지|메일|이메일|계좌|비밀)",
            "지시 무시", "(이전|위|앞|기존)(의)?\\s*(지시|명령|규칙|프롬프트)\\s*(은|는|을|를)?\\s*(무시|잊)|ignore\\s+(all\\s+)?(previous|above)|system\\s*prompt|시스템\\s*프롬프트|너는\\s*이제|역할\\s*극",
            "돈·결제", "결제|환불|입금|송금|현금\\s*(으로|지급)|캐시\\s*(충전|지급)");

    static final Set<String> NO_AUTOFIX_AREAS = Set.of("ACCOUNT", "ADMIN");

    public record Triage(String type, String area, String severity, String summary, String fixHint, String reply,
                         boolean autofix, List<String> risks) {
    }

    // 위험 낱말 찾기 (제목 + 내용)
    public static List<String> risks(String text) {

        List<String> out = new ArrayList<>();
        String t = text == null ? "" : text;
        for (Map.Entry<String, Pattern> e : RISKS.entrySet()) {
            Matcher m = e.getValue().matcher(t);
            if (m.find()) out.add(e.getKey());
        }
        return out;

    }

    // AI 답 → 정해진 값만, 길이 자르기, 자동 수정 후보는 서버가 최종 결정
    public static Triage clean(Map<?, ?> ai, String category, String title, String body) {

        List<String> risks = risks(title + "\n" + body);

        String type = pick(ai.get("type"), TYPES, CATEGORY_TYPE.getOrDefault(category, "OTHER"));
        String area = pick(ai.get("area"), AREAS, "ETC");
        String severity = pick(ai.get("severity"), SEVERITIES, "MEDIUM");
        String summary = cut(str(ai.get("summary")), 120);
        if (summary.isEmpty()) summary = cut(title, 120);
        String hint = cut(str(ai.get("fixHint")), 300);
        String reply = cut(str(ai.get("reply")), 600);

        boolean aiSays = Boolean.TRUE.equals(ai.get("autofix"));
        boolean typeOk = "BUG".equals(type) || ("FEATURE".equals(type) && "LOW".equals(severity));
        boolean autofix = aiSays && typeOk && !"HIGH".equals(severity) && risks.isEmpty()
                && !NO_AUTOFIX_AREAS.contains(area) && body.strip().length() >= 10;

        return new Triage(type, area, severity, summary, hint.isEmpty() ? null : hint, reply.isEmpty() ? null : reply, autofix, risks);

    }

    // AI가 없거나 실패했을 때
    public static Triage fallback(String category, String title, String body) {

        return new Triage(CATEGORY_TYPE.getOrDefault(category, "OTHER"), "ETC", "MEDIUM", cut(title, 120), null, null, false, risks(title + "\n" + body));

    }

    private static String pick(Object v, List<String> allowed, String fallback) {

        String s = v == null ? "" : String.valueOf(v).strip().toUpperCase(Locale.ROOT);
        return allowed.contains(s) ? s : fallback;

    }

    private static String str(Object o) {

        return o == null ? "" : String.valueOf(o).strip();

    }

    static String cut(String s, int max) {

        String v = s == null ? "" : s.strip();
        return v.length() > max ? v.substring(0, max) : v;

    }

    private static Map<String, Pattern> linked(String... kv) {

        Map<String, Pattern> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], Pattern.compile(kv[i + 1], Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        return Collections.unmodifiableMap(m);

    }
}
