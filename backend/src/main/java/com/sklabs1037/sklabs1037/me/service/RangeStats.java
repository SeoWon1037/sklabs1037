package com.sklabs1037.sklabs1037.me.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 기간 숫자 (🤖 주간 코치 · ⚔️ 친구 대결 · 📸 공유 카드)
 * - MeMapper.selectRangeStats 결과를 숫자로
 */
public record RangeStats(int trades, int predictVotes, int predictGraded, int predictHits,
                         int quizPlays, int quizScore, int boardPosts, int comments,
                         int attendDays, int aiChats, int beautyLogs) {

    public static final RangeStats EMPTY = new RangeStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public static RangeStats of(Map<String, Object> m) {

        if (m == null) return EMPTY;

        return new RangeStats(n(m, "trades"), n(m, "predictVotes"), n(m, "predictGraded"), n(m, "predictHits"),
                n(m, "quizPlays"), n(m, "quizScore"), n(m, "boardPosts"), n(m, "comments"),
                n(m, "attendDays"), n(m, "aiChats"), n(m, "beautyLogs"));

    }

    public Map<String, Object> toMap() {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trades", trades);
        m.put("predictVotes", predictVotes);
        m.put("predictGraded", predictGraded);
        m.put("predictHits", predictHits);
        m.put("quizPlays", quizPlays);
        m.put("quizScore", quizScore);
        m.put("boardPosts", boardPosts);
        m.put("comments", comments);
        m.put("attendDays", attendDays);
        m.put("aiChats", aiChats);
        m.put("beautyLogs", beautyLogs);
        return m;

    }

    private static int n(Map<String, Object> m, String key) {

        return m.get(key) instanceof Number x ? x.intValue() : 0;

    }
}
