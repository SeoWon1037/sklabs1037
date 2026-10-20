package com.sklabs1037.sklabs1037.me.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 🏅 내 활동·배지 / 🔔 알림 / 📅 출석 DAO (SQL: resources/mapper/MeMapper.xml)
 * - 결과는 Map (키는 SQL의 "camelCase" 별칭)
 */
@Mapper
public interface MeDao {

    // 활동 숫자 한 번에 (거래 수, 예측 투표 수, 퀴즈 참여·만점, 토론 글·받은 좋아요·댓글, AI 대화, 성분 분석, 관심 종목, 가격 알림, 투자 성향)
    Map<String, Object> selectActivity(@Param("userId") Long userId);

    // ===== 배지 =====
    List<Map<String, Object>> selectBadges(@Param("userId") Long userId);

    int insertBadge(@Param("userId") Long userId, @Param("code") String code);

    // ===== 알림 =====
    int insertNotification(@Param("userId") Long userId, @Param("kind") String kind, @Param("title") String title,
                           @Param("body") String body, @Param("link") String link, @Param("dedupeKey") String dedupeKey);

    // 예측 결과 알림: 그 날 문제에 투표한 사람마다 "n문제 중 m개 적중" (이미 보냈으면 건너뜀)
    int insertPredictNotifications(@Param("tradeDate") LocalDate tradeDate, @Param("title") String title);

    List<Map<String, Object>> selectNotifications(@Param("userId") Long userId, @Param("limit") int limit);

    int countUnread(@Param("userId") Long userId);

    int markRead(@Param("userId") Long userId, @Param("id") Long id);

    // ===== 출석 =====
    int insertAttendance(@Param("userId") Long userId, @Param("day") LocalDate day);

    List<LocalDate> selectAttendDays(@Param("userId") Long userId);

    // ===== 오늘의 루틴 =====
    int countPredictVotes(@Param("userId") Long userId, @Param("tradeDate") LocalDate tradeDate);

    int countPredictItems(@Param("tradeDate") LocalDate tradeDate);

    int countBoardPostsToday(@Param("userId") Long userId, @Param("day") LocalDate day);
}
