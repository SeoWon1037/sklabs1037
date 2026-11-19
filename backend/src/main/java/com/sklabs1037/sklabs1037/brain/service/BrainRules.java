package com.sklabs1037.sklabs1037.brain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 🧠 세컨드 브레인 규칙 (순수 계산 → 테스트 가능)
 *
 * 흐름: 모으기(RAW 메모) → 정리하기(AI가 주제별 WIKI 문서로) → 꺼내 쓰기(OUTPUT) — 주제 태그가 그래프의 허브
 * 원칙: "AI는 정리, 판단은 서버 규칙"
 *   - AI에게는 고른 메모만 보여 주고, AI가 답한 출처 번호는 그 안에 있는 것만 남김 (없는 메모를 지어내 근거로 달 수 없음)
 *   - 정리 문서의 모양(제목 · 요약 · 소제목 · 항목 수 · 길이)은 서버가 자르고 맞춤
 */
public final class BrainRules {

    private BrainRules() {
    }

    public static final List<String> KINDS = List.of("RAW", "WIKI", "OUTPUT");

    public static final int MAX_BODY = 20000;
    public static final int MAX_TITLE = 100;
    public static final int MAX_TOPICS = 5;
    public static final int MAX_TOPIC_LEN = 20;
    public static final int MAX_NOTES = 2000;          // 한 사람이 가질 수 있는 노트 수
    public static final int ORGANIZE_DAILY = 20;       // AI 정리는 하루 20번
    public static final int ORGANIZE_SOURCES = 30;     // 한 번 정리할 때 AI에게 보여 줄 메모 수
    public static final int SOURCE_CLIP = 1500;        // 메모 하나에서 AI에게 보여 줄 글자 수
    public static final int MATERIAL_MAX = 40000;      // AI에게 보여 줄 글 전체 글자 수

    // 본문 속 #태그 (앞이 글자 · 숫자 · & 가 아닐 때만 → "C#", "&#39;" 같은 것은 태그가 아님)
    private static final Pattern HASHTAG = Pattern.compile("(?<![\\p{L}\\p{N}&#])#([\\p{L}\\p{N}_\\-.]{1,20})");

    // ============================================
    // 글 다듬기
    // ============================================

    /** 줄바꿈 · 탭은 남기고 나머지 제어 문자는 지움 */
    static String clean(Object raw) {

        if (raw == null) return "";
        return String.valueOf(raw).replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");

    }

    public static String kind(Object raw) {

        String k = clean(raw).strip().toUpperCase();
        if (k.isEmpty()) return "RAW";
        if (!KINDS.contains(k)) throw new IllegalArgumentException("노트 종류는 RAW · WIKI · OUTPUT 중 하나예요.");
        return k;

    }

    /** 목록 걸러 보기용: 비었으면 null(전체) */
    public static String kindFilter(Object raw) {

        String k = clean(raw).strip().toUpperCase();
        return KINDS.contains(k) ? k : null;

    }

    public static String body(Object raw) {

        String s = clean(raw).strip();
        if (s.isEmpty()) throw new IllegalArgumentException("메모 내용을 적어 주세요.");
        if (s.length() > MAX_BODY) throw new IllegalArgumentException("메모는 " + MAX_BODY + "자까지 남길 수 있어요.");
        return s;

    }

    /** 제목이 없으면 본문 첫 줄에서 ("# 제목", "- 항목"의 기호는 떼고 60자까지) */
    public static String title(Object raw, String body) {

        String t = clean(raw).replace('\n', ' ').strip();
        if (t.isEmpty() && body != null) {
            for (String line : body.split("\n")) {
                String l = line.replaceAll("^[#>*\\-\\s]+", "").strip();
                if (!l.isEmpty()) {
                    t = l;
                    break;
                }
            }
            if (t.length() > 60) t = t.substring(0, 60).strip() + "…";
        }
        if (t.isEmpty()) t = "제목 없는 메모";
        if (t.length() > MAX_TITLE) t = t.substring(0, MAX_TITLE);
        return t;

    }

    /** 주제 하나 다듬기: # 떼고, 영어는 소문자, 20자까지. 못 쓰면 null */
    static String topic(String raw) {

        if (raw == null) return null;
        String t = raw.strip().replaceAll("^#+", "").replaceAll("[,\\s]+", "").toLowerCase();
        if (t.isEmpty()) return null;
        return t.length() > MAX_TOPIC_LEN ? t.substring(0, MAX_TOPIC_LEN) : t;

    }

    /** 주제 모으기: 직접 고른 것(목록 · "a, b" 글) + 본문의 #태그 → 겹치지 않게 5개까지 */
    public static List<String> topics(Object explicit, String body) {

        Set<String> out = new LinkedHashSet<>();

        if (explicit instanceof Collection<?> list) {
            for (Object o : list) add(out, o == null ? null : String.valueOf(o));
        } else if (explicit != null) {
            for (String s : String.valueOf(explicit).split("[,\\n]")) add(out, s);
        }

        if (body != null) {
            Matcher m = HASHTAG.matcher(body);
            while (m.find()) add(out, m.group(1).replaceAll("[.\\-]+$", ""));
        }

        List<String> list = new ArrayList<>(out);
        return list.size() > MAX_TOPICS ? list.subList(0, MAX_TOPICS) : list;

    }

    private static void add(Set<String> out, String raw) {

        String t = topic(raw);
        if (t != null) out.add(t);

    }

    /** DB에 둘 모양 "spring,jpa" ↔ 목록 */
    public static String topicsColumn(List<String> topics) {

        return topics == null ? "" : String.join(",", topics);

    }

    public static List<String> splitTopics(Object column) {

        List<String> out = new ArrayList<>();
        if (column == null) return out;
        for (String s : String.valueOf(column).split(",")) if (!s.isBlank()) out.add(s.strip());
        return out;

    }

    /** 찾을 말: # 떼고 1~40자 */
    public static String query(Object raw) {

        String q = clean(raw).replace('\n', ' ').strip().replaceAll("^#+", "").strip();
        if (q.isEmpty()) throw new IllegalArgumentException("무엇을 찾거나 정리할지 알려 주세요. (예: Spring)");
        if (q.length() > 40) throw new IllegalArgumentException("찾을 말은 40자까지예요.");
        return q;

    }

    /** SQL LIKE에 넣을 때 %, _, \ 를 글자 그대로 (ESCAPE '\') */
    public static String likeEscape(String q) {

        return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");

    }

    /** 목록에 보일 앞부분 120자 */
    public static String snippet(Object body) {

        String s = clean(body).replaceAll("\\s+", " ").strip();
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;

    }

    // ============================================
    // AI 정리
    // ============================================

    /**
     * AI에게 보여 줄 자료: <memo id="12" title="…">본문(1500자까지)</memo> … 전체 40000자까지
     * notes: { id, title, body, topics }
     */
    public static String material(List<Map<String, Object>> notes) {

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> n : notes) {
            String body = clean(n.get("body")).strip();
            if (body.length() > SOURCE_CLIP) body = body.substring(0, SOURCE_CLIP) + "…(뒷부분 생략)";
            String piece = "<memo id=\"" + n.get("id") + "\" title=\"" + attr(n.get("title")) + "\" topics=\"" + attr(n.get("topics")) + "\">\n"
                    + body.replace("</memo>", "< /memo>") + "\n</memo>\n";
            if (sb.length() + piece.length() > MATERIAL_MAX) break;
            sb.append(piece);
        }
        return sb.toString();

    }

    private static String attr(Object v) {

        return clean(v).replace('\n', ' ').replace("\"", "'").replace("<", "‹").replace(">", "›");

    }

    public record Wiki(String title, String body, List<String> topics, List<Long> sourceIds) {
    }

    static final int MAX_SECTIONS = 8;
    static final int MAX_POINTS = 8;
    static final int MAX_POINT_LEN = 300;
    static final int MAX_SUMMARY_LEN = 600;

    /**
     * AI 답(write_wiki 도구 입력) → 정리 문서
     * - 출처 번호는 allowedIds(이번에 보여 준 메모) 안의 것만 남김
     * - 본문은 "## 소제목 / - 항목 / 출처: [[메모:12]]" 모양 (화면이 [[메모:12]]를 그 메모로 가는 링크로 바꿈)
     * @param input AI가 준 { title, summary, sections: [{ heading, points[], source_ids[] }], topics[] }
     */
    public static Wiki wiki(Map<?, ?> input, Set<Long> allowedIds, String query) {

        if (input == null) throw new IllegalStateException("정리하지 못했어요. 잠시 후 다시 해 주세요.");

        String title = clean(input.get("title")).replace('\n', ' ').strip();
        if (title.isEmpty()) title = query + " 정리";
        if (title.length() > MAX_TITLE) title = title.substring(0, MAX_TITLE);

        String summary = clean(input.get("summary")).strip();
        if (summary.length() > MAX_SUMMARY_LEN) summary = summary.substring(0, MAX_SUMMARY_LEN) + "…";

        StringBuilder body = new StringBuilder();
        if (!summary.isEmpty()) body.append("## 요약\n").append(summary).append("\n\n");

        Set<Long> used = new LinkedHashSet<>();
        int sections = 0;

        if (input.get("sections") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> sec) || sections >= MAX_SECTIONS) continue;

                List<String> points = new ArrayList<>();
                if (sec.get("points") instanceof List<?> ps) {
                    for (Object p : ps) {
                        String s = clean(p).replace('\n', ' ').strip().replaceAll("^[-*•\\s]+", "");
                        if (s.isEmpty()) continue;
                        points.add(s.length() > MAX_POINT_LEN ? s.substring(0, MAX_POINT_LEN) + "…" : s);
                        if (points.size() >= MAX_POINTS) break;
                    }
                }
                if (points.isEmpty()) continue;

                String heading = clean(sec.get("heading")).replace('\n', ' ').strip().replaceAll("^#+\\s*", "");
                if (heading.isEmpty()) heading = "정리 " + (sections + 1);
                if (heading.length() > 60) heading = heading.substring(0, 60);

                List<Long> ids = new ArrayList<>();
                if (sec.get("source_ids") instanceof List<?> sids) {
                    for (Object s : sids) {
                        Long id = toLong(s);
                        if (id != null && allowedIds.contains(id) && !ids.contains(id)) ids.add(id);
                    }
                }

                body.append("## ").append(heading).append('\n');
                for (String p : points) body.append("- ").append(p).append('\n');
                if (!ids.isEmpty()) {
                    body.append("출처:");
                    for (Long id : ids) body.append(" [[메모:").append(id).append("]]");
                    body.append('\n');
                }
                body.append('\n');

                used.addAll(ids);
                sections++;
            }
        }

        if (sections == 0) throw new IllegalStateException("정리할 내용을 찾지 못했어요. 메모를 조금 더 남긴 뒤 다시 해 주세요.");

        List<String> topics = topics(input.get("topics"), null);
        String qt = topic(query);
        if (qt != null && !topics.contains(qt)) {
            topics = new ArrayList<>(topics);
            topics.add(0, qt);
            if (topics.size() > MAX_TOPICS) topics = topics.subList(0, MAX_TOPICS);
        }

        return new Wiki(title, body.toString().strip(), topics, new ArrayList<>(used));

    }

    static Long toLong(Object v) {

        if (v instanceof Number n) return n.longValue();
        try {
            return v == null ? null : Long.valueOf(String.valueOf(v).strip());
        } catch (NumberFormatException e) {
            return null;
        }

    }

    // ============================================
    // 그래프
    // ============================================

    public static final int MAX_HUBS = 30;

    /**
     * 노트 · 연결 → 그래프
     * @param notes { id, kind, title, topics(쉼표 글) }
     * @param links { fromId, toId }
     * @return { nodes: [{ id, kind, title, topics[] }], hubs: [{ id:"t:spring", label, count }], edges: [{ a, b, type:"link"|"topic" }] }
     *         topic 선은 노트 → 허브 (b가 "t:..."), 허브는 노트가 많은 순으로 30개까지
     */
    public static Map<String, Object> graph(List<Map<String, Object>> notes, List<Map<String, Object>> links) {

        Map<String, Integer> counts = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<Long> ids = new LinkedHashSet<>();

        for (Map<String, Object> n : notes) {
            Long id = toLong(n.get("id"));
            if (id == null) continue;
            ids.add(id);
            List<String> topics = splitTopics(n.get("topics"));
            for (String t : topics) counts.merge(t, 1, Integer::sum);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("kind", String.valueOf(n.get("kind")));
            m.put("title", String.valueOf(n.get("title")));
            m.put("topics", topics);
            nodes.add(m);
        }

        List<String> hubNames = counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(MAX_HUBS).map(Map.Entry::getKey).toList();

        List<Map<String, Object>> hubs = new ArrayList<>();
        for (String h : hubNames) hubs.add(Map.of("id", "t:" + h, "label", h, "count", counts.get(h)));

        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> l : links) {
            Long a = toLong(l.get("fromId")), b = toLong(l.get("toId"));
            if (a != null && b != null && ids.contains(a) && ids.contains(b)) edges.add(Map.of("a", a, "b", b, "type", "link"));
        }
        for (Map<String, Object> n : nodes) {
            @SuppressWarnings("unchecked")
            List<String> topics = (List<String>) n.get("topics");
            for (String t : topics) if (hubNames.contains(t)) edges.add(Map.of("a", n.get("id"), "b", "t:" + t, "type", "topic"));
        }

        Map<String, Object> g = new LinkedHashMap<>();
        g.put("nodes", nodes);
        g.put("hubs", hubs);
        g.put("edges", edges);
        return g;

    }

    // ============================================
    // 예시 메모 (처음 써 볼 때 "예시 메모 넣기" — 모두 #예시 태그가 붙어서 한 번에 찾고 지울 수 있음)
    // ============================================
    public static final List<String[]> SAMPLES = List.of(
            new String[]{"Spring 빈 생명주기", "#spring #예시\n스프링 컨테이너가 객체(빈)를 만들고 의존성을 넣은 뒤 초기화 메서드를 부르고, 끝날 때 소멸 메서드를 부름.\n@PostConstruct 는 의존성 주입이 끝난 직후 한 번 실행됨."},
            new String[]{"@Transactional 이 안 먹을 때", "#spring #예시\n같은 클래스 안에서 this.메서드()로 부르면 프록시를 거치지 않아서 트랜잭션이 안 걸림.\nprivate 메서드에도 안 걸림. 체크 예외는 기본으로 롤백되지 않음 → rollbackFor 지정."},
            new String[]{"MyBatis에서 Map으로 받기", "#spring #mybatis #예시\nresultType=\"java.util.LinkedHashMap\" 으로 받고 칸 이름은 AS \"camelCase\" 로 맞추면 화면에 그대로 내보낼 수 있음.\nLIKE 검색은 % _ 를 ESCAPE 처리해야 함."},
            new String[]{"스프링 부트 프로필 나누기", "#spring #예시\napplication-local.properties / application-prod.properties 로 나누고 spring.profiles.active 로 고름.\n비밀값은 파일에 넣지 말고 환경 변수(.env)로."},
            new String[]{"React useEffect 정리 함수", "#react #예시\nuseEffect 안에서 setInterval · 이벤트 리스너를 걸었으면 return () => 해제 를 꼭 넣기.\n안 그러면 화면을 나갔다 와도 계속 돌아감."},
            new String[]{"AI 기능 설계 원칙", "#ai #예시\nAI는 정리만, 판단은 서버 규칙이. 강제 도구 호출로 정해진 모양의 JSON만 받고, 서버가 다시 검사함.\n되돌릴 수 없는 일(돈 · 삭제 · 배포)은 사람 승인."}
    );
}
