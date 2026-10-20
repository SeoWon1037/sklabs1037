package com.sklabs1037.sklabs1037.quiz.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 📰 오늘의 뉴스 퀴즈 DAO (SQL: resources/mapper/NewsQuizMapper.xml)
 * - 결과는 Map (키는 SQL에서 "camelCase" 별칭)
 */
@Mapper
public interface NewsQuizDao {

    // 출제 재료: 최근 n시간 기사 (스포츠 제외, 같은 제목 한 번) [{newsId, title, link, press, stockName, pubAt}]
    List<Map<String, Object>> selectRecentNews(@Param("hours") int hours, @Param("limit") int limit);

    int insertQuiz(@Param("quizDate") LocalDate quizDate, @Param("questionsJson") String questionsJson, @Param("newsCount") int newsCount);

    // 가장 최근 퀴즈 (오늘 또는 그 전) {quizDate, questionsJson}
    Map<String, Object> selectLatestQuiz(@Param("upTo") LocalDate upTo);

    int countQuiz(@Param("quizDate") LocalDate quizDate);

    // 내 기록 {score, answersJson}
    Map<String, Object> selectPlay(@Param("quizDate") LocalDate quizDate, @Param("userId") Long userId);

    int insertPlay(@Param("quizDate") LocalDate quizDate, @Param("userId") Long userId,
                   @Param("score") int score, @Param("answersJson") String answersJson);

    // 그 날 참여 통계 {players, avgScore} + 점수별 인원 [{score, cnt}]
    Map<String, Object> selectDayStats(@Param("quizDate") LocalDate quizDate);

    List<Map<String, Object>> selectScoreCounts(@Param("quizDate") LocalDate quizDate);

    // 이번 주 랭킹 [{nickname, total, days}]
    List<Map<String, Object>> selectWeekRanking(@Param("from") LocalDate from, @Param("limit") int limit);

    // 내가 참여한 날짜들 (최신순, 연속 참여 계산)
    List<LocalDate> selectMyDates(@Param("userId") Long userId);
}
