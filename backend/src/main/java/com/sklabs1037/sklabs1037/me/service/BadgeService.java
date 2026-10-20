package com.sklabs1037.sklabs1037.me.service;

import com.sklabs1037.sklabs1037.me.dao.MeDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;

/**
 * 🏅 업적·배지
 *
 * - 조건은 여기(코드)에, 받은 날짜는 T_USER_BADGE 에
 * - "내 활동"을 열 때마다 지금 숫자로 다시 확인해서 새로 달성한 배지를 주고 🔔 알림
 *   (따로 이벤트를 심지 않아도 예측·퀴즈·모의투자·토론 어디서 달성하든 다음에 열면 받음)
 */
@Service
public class BadgeService {

    /** 배지 판정에 쓰는 숫자들 */
    public record Stats(int tradeCount, Double returnRate, boolean beatAi,
                        int predictVotes, int predictGraded, int predictHits, int predictBestStreak,
                        int quizPlays, int quizPerfect, int quizBestStreak,
                        int boardPosts, int likesReceived, int comments,
                        int aiChats, int beautyLogs, int watchCount, int alertCount,
                        boolean personaDone, int attendDays, int attendBestStreak) {
    }

    public record Badge(String code, String icon, String name, String desc, String group, Predicate<Stats> rule) {
    }

    public static final List<Badge> BADGES = List.of(
            new Badge("ATTEND_FIRST", "👋", "반가워요", "처음 출석했어요", "출석", s -> s.attendDays() >= 1),
            new Badge("ATTEND_7", "📅", "일주일 개근", "7일 연속 출석", "출석", s -> s.attendBestStreak() >= 7),
            new Badge("ATTEND_30", "🗓️", "단골손님", "출석 30일", "출석", s -> s.attendDays() >= 30),

            new Badge("TRADE_FIRST", "💰", "첫 거래", "모의투자 첫 체결", "모의투자", s -> s.tradeCount() >= 1),
            new Badge("TRADE_50", "📈", "부지런한 트레이더", "모의투자 50번 체결", "모의투자", s -> s.tradeCount() >= 50),
            new Badge("PROFIT_5", "🟥", "수익 +5%", "모의투자 수익률 +5% 달성", "모의투자", s -> s.returnRate() != null && s.returnRate() >= 5),
            new Badge("PROFIT_20", "🚀", "수익 +20%", "모의투자 수익률 +20% 달성", "모의투자", s -> s.returnRate() != null && s.returnRate() >= 20),
            new Badge("BEAT_AI", "🥊", "AI를 이겼다", "AI 리그 1등 AI보다 높은 수익률", "모의투자", Stats::beatAi),

            new Badge("PREDICT_FIRST", "🎯", "첫 예측", "오를까 내릴까 첫 참여", "예측", s -> s.predictVotes() >= 1),
            new Badge("PREDICT_STREAK5", "🔥", "5연속 적중", "예측 5번 연속 적중", "예측", s -> s.predictBestStreak() >= 5),
            new Badge("PREDICT_SHARP", "🧠", "날카로운 눈", "예측 20문제 이상 + 적중률 60% 이상", "예측",
                    s -> s.predictGraded() >= 20 && s.predictHits() * 100 >= s.predictGraded() * 60),

            new Badge("QUIZ_FIRST", "📰", "첫 퀴즈", "뉴스 퀴즈 첫 참여", "퀴즈", s -> s.quizPlays() >= 1),
            new Badge("QUIZ_PERFECT", "🏆", "뉴스 만점", "뉴스 퀴즈 만점", "퀴즈", s -> s.quizPerfect() >= 1),
            new Badge("QUIZ_STREAK7", "📚", "뉴스 정독러", "뉴스 퀴즈 7일 연속", "퀴즈", s -> s.quizBestStreak() >= 7),

            new Badge("BOARD_FIRST", "💬", "첫 토론 글", "종목 토론에 첫 글", "커뮤니티", s -> s.boardPosts() >= 1),
            new Badge("BOARD_LIKED10", "👍", "인기 글쓴이", "내 글이 받은 좋아요 10개", "커뮤니티", s -> s.likesReceived() >= 10),
            new Badge("COMMENT_10", "🗨️", "수다쟁이", "댓글 10개", "커뮤니티", s -> s.comments() >= 10),

            new Badge("PERSONA_DONE", "🧭", "나를 알다", "투자 성향 테스트 완료", "탐험", Stats::personaDone),
            new Badge("AI_CHAT10", "🤖", "AI와 친구", "AI 비서와 대화 10번", "탐험", s -> s.aiChats() >= 10),
            new Badge("BEAUTY_FIRST", "🔬", "성분 탐정", "뷰티 랩 첫 분석", "탐험", s -> s.beautyLogs() >= 1),
            new Badge("WATCH_5", "⭐", "관심 많은 사람", "관심 종목 5개", "탐험", s -> s.watchCount() >= 5),
            new Badge("ALERT_FIRST", "🔔", "기다리는 법", "첫 목표가 알림", "탐험", s -> s.alertCount() >= 1),
            // 도전: 숫자로 판정하지 않고 그 일이 일어날 때 바로 줌 (awardOne)
            new Badge("COACH_CLEAR", "🎖️", "미션 클리어", "AI 코치 주간 미션 3개 모두 달성", "도전", s -> false),
            new Badge("DUEL_WIN", "⚔️", "대결 승리", "친구와 1:1 대결에서 승리", "도전", s -> false)
    );

    @Autowired
    private MeDao meDao;

    @Autowired
    private NotificationService notificationService;

    // 지금 숫자로 달성한 배지 코드들 (순수 계산, 테스트 가능)
    static List<String> earned(Stats s) {

        return BADGES.stream().filter(b -> b.rule().test(s)).map(Badge::code).toList();

    }

    // 새로 달성한 배지 저장 + 알림 → 이번에 새로 받은 코드들
    public List<String> award(Long userId, Stats stats) {

        Set<String> have = new HashSet<>();
        for (Map<String, Object> row : meDao.selectBadges(userId)) have.add(String.valueOf(row.get("code")));

        List<String> fresh = new ArrayList<>();

        for (String code : earned(stats)) {

            if (have.contains(code) || meDao.insertBadge(userId, code) == 0) continue;

            fresh.add(code);
            Badge b = find(code);
            notificationService.notify(userId, "BADGE", b.icon() + " 새 배지: " + b.name(), b.desc(), "/activity", "BADGE:" + code);

        }

        return fresh;

    }

    // 이벤트로 바로 주는 배지 (미션 클리어, 대결 승리) → 새로 받았으면 true
    public boolean awardOne(Long userId, String code) {

        Badge b = find(code);
        if (b == null || meDao.insertBadge(userId, code) == 0) return false;

        notificationService.notify(userId, "BADGE", b.icon() + " 새 배지: " + b.name(), b.desc(), "/activity", "BADGE:" + code);
        return true;

    }

    public static Badge find(String code) {

        return BADGES.stream().filter(b -> b.code().equals(code)).findFirst().orElse(null);

    }
}
