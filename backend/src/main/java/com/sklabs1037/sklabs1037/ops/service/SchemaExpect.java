package com.sklabs1037.sklabs1037.ops.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 🧪 "DB에 있어야 하는 표 · 칸" 목록 (순수 계산)
 * - backend/sql/*.sql 을 pgAdmin에서 손으로 실행하는 방식이라, 하나를 빼먹어도 그 기능을 누를 때까지 모름
 *   → 관리자 > 자가 점검이 이 목록과 실제 DB를 비교해 "아직 실행 안 한 SQL 파일"을 알려줌
 * - 목록은 backend/sql 과 resources/db/auto 의 CREATE TABLE / ALTER TABLE … ADD COLUMN 에서 뽑은 것 (표 이름 → 그 표를 만드는 파일)
 *   파일 이름이 날짜로 시작하면(2026-11-11-01-…) 서버가 스스로 실행하는 자동 SQL (AutoSqlRunner)
 *   SQL 파일을 새로 넣으면 여기에도 넣어야 함 — 안 넣으면 SchemaExpectTest가 실패해서 알려줌
 */
public final class SchemaExpect {

    private SchemaExpect() {
    }

    // 표 이름 → 만드는 SQL 파일
    public static final Map<String, String> TABLES = Map.ofEntries(
            Map.entry("T_USER_BADGE", "activity.sql"),
            Map.entry("T_NOTIFICATION", "activity.sql"),
            Map.entry("T_ATTENDANCE", "activity.sql"),
            Map.entry("T_AI_TRADER", "ai-league.sql"),
            Map.entry("T_AI_TRADE_LOG", "ai-league.sql"),
            Map.entry("T_AI_PROMPT", "ai-prompt.sql"),
            Map.entry("T_AI_USAGE", "ai-usage.sql"),
            Map.entry("T_AI_BUDGET", "ai-usage.sql"),
            Map.entry("T_API_CALL_LOG", "api-call-log.sql"),
            Map.entry("T_AUTOFIX", "autofix.sql"),
            Map.entry("T_INGREDIENT", "beauty-lab.sql"),
            Map.entry("T_BEAUTY_LOG", "beauty-lab.sql"),
            Map.entry("T_STOCK_POST_LIKE", "board-like-report.sql"),
            Map.entry("T_STOCK_POST_REPORT", "board-like-report.sql"),
            Map.entry("T_CALENDAR_ALERT", "calendar-alert.sql"),
            Map.entry("T_CLIENT_ERROR", "client-error.sql"),
            Map.entry("T_WEEKLY_COACH", "coach-share-duel.sql"),
            Map.entry("T_SHARE_CARD", "coach-share-duel.sql"),
            Map.entry("T_DUEL", "coach-share-duel.sql"),
            Map.entry("T_COUPLE_HEART", "couple-heart.sql"),
            Map.entry("T_COUPLE_PHOTO", "couple-photo.sql"),
            Map.entry("T_COUPLE", "couple.sql"),
            Map.entry("T_DM_ROOM", "dm.sql"),
            Map.entry("T_DM_MSG", "dm.sql"),
            Map.entry("T_DM_BLOCK", "dm.sql"),
            Map.entry("T_IDEA", "ideas-diagnosis-brief.sql"),
            Map.entry("T_IDEA_VOTE", "ideas-diagnosis-brief.sql"),
            Map.entry("T_MOCK_DIAGNOSIS", "ideas-diagnosis-brief.sql"),
            Map.entry("T_STOCK_BRIEF", "ideas-diagnosis-brief.sql"),
            Map.entry("T_INVITE", "invite.sql"),
            Map.entry("T_MARKET_OFFER", "market-offer.sql"),
            Map.entry("T_MARKET_REVIEW", "market-plus.sql"),
            Map.entry("T_MARKET_KEYWORD", "market-plus.sql"),
            Map.entry("T_MARKET_ITEM", "market.sql"),
            Map.entry("T_MARKET_PHOTO", "market.sql"),
            Map.entry("T_MARKET_LIKE", "market.sql"),
            Map.entry("T_MARKET_CHAT", "market.sql"),
            Map.entry("T_MARKET_REPORT", "market.sql"),
            Map.entry("T_MOCK_RESET_REQUEST", "mock-reset-request.sql"),
            Map.entry("T_MOCK_SEASON", "mock-season.sql"),
            Map.entry("T_MOCK_SNAPSHOT", "mock-snapshot.sql"),
            Map.entry("T_MOCK_ORDER", "mock-trade-limit.sql"),
            Map.entry("T_MOCK_ACCOUNT", "mock-trade.sql"),
            Map.entry("T_MOCK_HOLDING", "mock-trade.sql"),
            Map.entry("T_MOCK_TRADE", "mock-trade.sql"),
            Map.entry("T_MORNING_BRIEF", "morning-brief.sql"),
            Map.entry("T_NEWS_QUIZ", "news-quiz.sql"),
            Map.entry("T_NEWS_QUIZ_PLAY", "news-quiz.sql"),
            Map.entry("T_JOB_RUN", "ops-job.sql"),
            Map.entry("T_PERSONA_RESULT", "persona.sql"),
            Map.entry("T_PREDICT_ITEM", "predict-game.sql"),
            Map.entry("T_PREDICT_VOTE", "predict-game.sql"),
            Map.entry("T_PRICE_ALERT", "price-alert.sql"),
            Map.entry("T_PUSH_KEY", "push.sql"),
            Map.entry("T_PUSH_SUB", "push.sql"),
            Map.entry("T_QA_RUN", "qa-bot.sql"),
            Map.entry("T_RAG_DOC", "regulation-qa.sql"),
            Map.entry("T_RAG_CHUNK", "regulation-qa.sql"),
            Map.entry("T_INQUIRY", "support.sql"),
            Map.entry("T_DAILY_STAT", "usage-stats.sql"),
            Map.entry("T_DAILY_VISITOR", "usage-stats.sql"),
            Map.entry("T_MARKET_VIEW_DAY", "2026-11-11-01-market-view-day.sql"),
            Map.entry("T_PLAN", "2026-11-14-01-plan-voice.sql"),
            Map.entry("T_VOICE_BRIEF", "2026-11-14-01-plan-voice.sql"),
            Map.entry("T_COUPLE_BIRTHDAY", "2026-11-14-02-couple-birthday.sql"),
            Map.entry("T_LOGIN_KEEP", "2026-11-14-03-login-keep.sql"),
            Map.entry("T_COUPLE_NOTE", "2026-11-14-05-couple-note.sql"));

    // "표.칸" → 그 칸을 더하는 SQL 파일 (나중에 ALTER TABLE로 더한 칸만)
    public static final Map<String, String> COLUMNS = Map.ofEntries(
            Map.entry("T_AI_CHAT_LOG.PROMPT_VERSION", "ai-prompt.sql"),
            Map.entry("T_AUTOFIX.SOURCE", "autofix-daily.sql"),
            Map.entry("T_AUTOFIX.TITLE", "autofix-daily.sql"),
            Map.entry("T_AUTOFIX.SIGNATURE", "autofix-daily.sql"),
            Map.entry("T_MARKET_ITEM.BUYER_ID", "market-plus.sql"),
            Map.entry("T_MARKET_ITEM.PREV_PRICE", "market-plus.sql"),
            Map.entry("T_MOCK_TRADE.ORDER_ID", "mock-trade-limit.sql"),
            Map.entry("T_STOCK.FEATURED_YN", "stock-all.sql"),
            Map.entry("T_STOCK.SECURITY_TYPE", "stock-all.sql"),
            Map.entry("T_STOCK.LAST_TRADE_DATE", "stock-all.sql"),
            Map.entry("T_STOCK.LAST_CLOSE", "stock-all.sql"),
            Map.entry("T_STOCK.PREV_CLOSE", "stock-all.sql"),
            Map.entry("T_STOCK.LAST_VOLUME", "stock-all.sql"),
            Map.entry("T_STOCK.HISTORY_DAY", "stock-history.sql"),
            Map.entry("T_STOCK.SHARES_OUTSTANDING", "stock-shares.sql"),
            Map.entry("T_PLAN.SHARED_YN", "2026-11-14-04-plan-shared-repeat.sql"),
            Map.entry("T_PLAN.REPEAT_ID", "2026-11-14-04-plan-shared-repeat.sql"));

    /**
     * @param tables  실제 DB에 있는 표 이름들 (대문자)
     * @param columns 실제 DB에 있는 "표.칸" 들 (대문자)
     * @return 아직 실행 안 한 것으로 보이는 SQL 파일 → 없는 표 · 칸 (파일 이름순)
     */
    public static Map<String, List<String>> missing(Set<String> tables, Set<String> columns) {

        Map<String, List<String>> out = new java.util.TreeMap<>();
        TABLES.forEach((table, file) -> {
            if (!tables.contains(table)) out.computeIfAbsent(file, (k) -> new ArrayList<>()).add(table);
        });
        COLUMNS.forEach((column, file) -> {
            String table = column.substring(0, column.indexOf('.'));
            // 표가 통째로 없으면 표만 말함 (칸까지 늘어놓지 않음)
            if (tables.contains(table) && !columns.contains(column)) out.computeIfAbsent(file, (k) -> new ArrayList<>()).add(column);
        });
        out.values().forEach(java.util.Collections::sort);
        return new LinkedHashMap<>(out);

    }
}
