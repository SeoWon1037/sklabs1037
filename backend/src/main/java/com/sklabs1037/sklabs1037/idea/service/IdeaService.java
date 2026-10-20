package com.sklabs1037.sklabs1037.idea.service;

import com.sklabs1037.sklabs1037.client.ClaudeApiClient;
import com.sklabs1037.sklabs1037.idea.dao.IdeaDao;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 💡 기능 제안 + 투표
 *
 * - 누구나 보고, 로그인하면 제안(하루 5개) · 👍 투표 (다시 누르면 취소, 내 제안은 자동 1표)
 * - 쓰기 전에 "비슷한 제안" 찾기: AI가 기존 제안 목록에서 같은 뜻을 고르고 카테고리를 붙임
 *   (AI가 없거나 실패하면 낱말이 겹치는 정도로) → 이미 있으면 거기에 투표하도록 안내
 * - 관리자: 상태 바꾸기 (제안 → 만드는 중 → 나왔어요 / 보류) + 한마디
 *   → 🔔 쓴 사람에게 알림, "나왔어요"면 투표한 사람 모두에게 알림
 */
@Slf4j
@Service
public class IdeaService {

    static final List<String> CATEGORIES = List.of("투자", "게임·도전", "AI", "뷰티", "커뮤니티", "화면·편의", "기타");
    static final Set<String> STATUSES = Set.of("OPEN", "PLANNED", "DONE", "REJECTED");
    static final Map<String, String> STATUS_LABEL = Map.of("OPEN", "제안", "PLANNED", "만드는 중", "DONE", "나왔어요", "REJECTED", "보류");

    private static final int DAILY_LIMIT = 5;
    private static final int CANDIDATES = 80;

    private static final String SYSTEM = """
            너는 SKLabs(모의투자·예측 게임·뉴스 퀴즈·종목 토론·AI 비서·뷰티 랩 사이트)의 기능 제안 정리 담당이다.
            새 제안과 기존 제안 목록을 보고, 사실상 같은 기능을 원하는 기존 제안 번호를 최대 3개 고르고(없으면 빈 배열),
            새 제안에 맞는 카테고리를 하나 고른다. 표현이 달라도 원하는 기능이 같으면 같은 제안이다.
            반드시 match_ideas 도구로만 답한다.
            """;

    private static final Map<String, Object> TOOL = Map.of(
            "name", "match_ideas",
            "description", "비슷한 기존 제안 번호와 카테고리",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "similarIds", Map.of("type", "array", "maxItems", 3, "items", Map.of("type", "integer")),
                            "category", Map.of("type", "string", "enum", CATEGORIES)),
                    "required", List.of("similarIds", "category")));

    @Autowired
    private IdeaDao ideaDao;

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private NotificationService notificationService;

    // ============================================
    // 목록
    // ============================================
    public Map<String, Object> list(Long userId, String sort, String status) {

        String s = status != null && STATUSES.contains(status) ? status : null;

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String k : List.of("OPEN", "PLANNED", "DONE", "REJECTED")) counts.put(k, 0);
        for (Map<String, Object> r : ideaDao.selectStatusCounts()) counts.put(String.valueOf(r.get("status")), ((Number) r.get("count")).intValue());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ideas", ideaDao.selectIdeas(userId, "NEW".equals(sort) ? "NEW" : "TOP", s, 100));
        result.put("counts", counts);
        result.put("categories", CATEGORIES);
        return result;

    }

    // ============================================
    // 쓰기 전: 비슷한 제안 찾기
    // ============================================
    @SuppressWarnings("unchecked")
    public Map<String, Object> check(String title, String body) {

        String t = clean(title, 60);
        String b = clean(body, 500);
        if (t.length() < 4) throw new IllegalArgumentException("제목을 4글자 이상 적어주세요.");

        List<Map<String, Object>> candidates = ideaDao.selectCandidates(CANDIDATES);
        List<Long> ids = null;
        String category = null;

        if (claudeApiClient.isConfigured() && !candidates.isEmpty()) {

            try {

                StringBuilder sb = new StringBuilder("## 기존 제안\n");
                for (Map<String, Object> c : candidates) sb.append(c.get("id")).append(". ").append(c.get("title")).append("\n");
                sb.append("\n## 새 제안\n제목: ").append(t).append("\n내용: ").append(b);

                Map<String, Object> res = claudeApiClient.createMessage(SYSTEM, List.of(TOOL),
                        List.of(Map.of("role", "user", "content", sb.toString())), Map.of("type", "tool", "name", "match_ideas"), 400);

                if (res != null && res.get("content") instanceof List<?> blocks) {
                    for (Object o : blocks) {
                        if (o instanceof Map<?, ?> m && "tool_use".equals(m.get("type")) && m.get("input") instanceof Map<?, ?> in) {
                            ids = new ArrayList<>();
                            if (in.get("similarIds") instanceof List<?> l) for (Object x : l) if (x instanceof Number n) ids.add(n.longValue());
                            category = in.get("category") == null ? null : String.valueOf(in.get("category"));
                        }
                    }
                }

            } catch (Exception e) {

                log.warn("비슷한 제안 찾기(AI) 실패, 낱말 비교로: {}", e.getMessage());

            }

        }

        List<Map<String, Object>> similar = ids != null ? pick(candidates, ids) : bySimilarity(t + " " + b, candidates);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("similar", similar);
        result.put("category", category != null && CATEGORIES.contains(category) ? category : guessCategory(t + " " + b));
        return result;

    }

    // ============================================
    // 쓰기 · 지우기 · 투표
    // ============================================
    public Map<String, Object> create(Long userId, String title, String body, String category) {

        String t = clean(title, 60);
        String b = clean(body, 500);
        if (t.length() < 4) throw new IllegalArgumentException("제목을 4글자 이상 적어주세요.");
        if (ideaDao.countToday(userId) >= DAILY_LIMIT) throw new IllegalArgumentException("제안은 하루 " + DAILY_LIMIT + "개까지 할 수 있어요.");

        Map<String, Object> idea = new HashMap<>();
        idea.put("userId", userId);
        idea.put("title", t);
        idea.put("body", b.isEmpty() ? null : b);
        idea.put("category", category != null && CATEGORIES.contains(category) ? category : guessCategory(t + " " + b));
        ideaDao.insertIdea(idea);

        Long id = ((Number) idea.get("id")).longValue();
        vote(userId, id);   // 내 제안은 자동 1표

        return Map.of("id", id);

    }

    public void delete(Long userId, Long id) {

        if (ideaDao.deleteIdea(id, userId) == 0) throw new IllegalArgumentException("지울 수 없는 제안이에요.");

    }

    // 👍 누르면 투표, 다시 누르면 취소
    public Map<String, Object> vote(Long userId, Long id) {

        if (ideaDao.selectIdea(id) == null) throw new IllegalArgumentException("제안을 찾을 수 없어요.");

        boolean voted;
        if (ideaDao.insertVote(id, userId) > 0) {
            ideaDao.addVoteCount(id, 1);
            voted = true;
        } else {
            if (ideaDao.deleteVote(id, userId) > 0) ideaDao.addVoteCount(id, -1);
            voted = false;
        }

        return Map.of("voted", voted, "voteCount", ideaDao.selectVoteCount(id));

    }

    // ============================================
    // 관리자: 상태 바꾸기
    // ============================================
    public void changeStatus(Long id, String status, String note) {

        if (!STATUSES.contains(status)) throw new IllegalArgumentException("상태를 확인해주세요.");

        Map<String, Object> idea = ideaDao.selectIdea(id);
        if (idea == null) throw new IllegalArgumentException("제안을 찾을 수 없어요.");

        String n = clean(note, 300);
        ideaDao.updateStatus(id, status, n.isEmpty() ? null : n);

        if (status.equals(idea.get("status"))) return;

        String title = String.valueOf(idea.get("title"));
        Long authorId = ((Number) idea.get("userId")).longValue();

        notificationService.notify(authorId, "IDEA", "💡 내 제안이 '" + STATUS_LABEL.get(status) + "'(으)로 바뀌었어요",
                title + (n.isEmpty() ? "" : " · " + n), "/ideas", "IDEA:" + id + ":" + status);

        if ("DONE".equals(status)) {
            for (Long voter : ideaDao.selectVoters(id)) {
                if (voter.equals(authorId)) continue;
                notificationService.notify(voter, "IDEA", "🎉 투표한 기능이 나왔어요", title, "/ideas", "IDEA_DONE:" + id);
            }
        }

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================

    // AI가 고른 번호 → 실제 있는 제안만 (최대 3개)
    static List<Map<String, Object>> pick(List<Map<String, Object>> candidates, List<Long> ids) {

        List<Map<String, Object>> out = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            for (Map<String, Object> c : candidates) {
                if (out.size() < 3 && c.get("id") instanceof Number n && n.longValue() == id) out.add(summary(c));
            }
        }
        return out;

    }

    // 낱말이 겹치는 정도 (자카드) 0.25 이상, 높은 순 3개
    static List<Map<String, Object>> bySimilarity(String text, List<Map<String, Object>> candidates) {

        Set<String> a = words(text);
        List<Map.Entry<Double, Map<String, Object>>> scored = new ArrayList<>();

        for (Map<String, Object> c : candidates) {
            Set<String> b = words(c.get("title") + " " + Objects.toString(c.get("body"), ""));
            if (a.isEmpty() || b.isEmpty()) continue;
            Set<String> inter = new HashSet<>(a);
            inter.retainAll(b);
            Set<String> union = new HashSet<>(a);
            union.addAll(b);
            double score = (double) inter.size() / union.size();
            if (score >= 0.25) scored.add(Map.entry(score, c));
        }

        scored.sort((x, y) -> Double.compare(y.getKey(), x.getKey()));
        return scored.stream().limit(3).map(e -> summary(e.getValue())).toList();

    }

    static Set<String> words(String text) {

        Set<String> set = new HashSet<>();
        for (String w : text.toLowerCase().split("[^0-9a-z가-힣]+")) {
            // 한국어 조사 몇 개는 떼고 비교 ("알림을" = "알림")
            String s = w.replaceAll("(을|를|이|가|은|는|에|의|도|으로|로|에서|하고|해서|했으면|하면|좋겠어요|좋겠다|주세요)$", "");
            if (s.length() >= 2) set.add(s);
        }
        return set;

    }

    static String guessCategory(String text) {

        String t = text.toLowerCase();
        if (t.matches(".*(성분|화장|피부|뷰티|처방|크림).*")) return "뷰티";
        if (t.matches(".*(예측|퀴즈|대결|배지|랭킹|게임|미션).*")) return "게임·도전";
        if (t.matches(".*(ai|비서|요약|분석|추천|챗).*")) return "AI";
        if (t.matches(".*(토론|댓글|게시판|친구|채팅|커뮤니티).*")) return "커뮤니티";
        if (t.matches(".*(주식|종목|매수|매도|모의|차트|시세|환율|배당|포트폴리오).*")) return "투자";
        if (t.matches(".*(화면|글자|다크|모바일|메뉴|알림|속도|디자인).*")) return "화면·편의";
        return "기타";

    }

    private static Map<String, Object> summary(Map<String, Object> c) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.get("id"));
        m.put("title", c.get("title"));
        m.put("status", c.get("status"));
        m.put("voteCount", c.get("voteCount"));
        return m;

    }

    private static String clean(String s, int max) {

        String v = s == null ? "" : s.strip().replaceAll("\\s+", " ");
        return v.length() > max ? v.substring(0, max) : v;

    }
}
