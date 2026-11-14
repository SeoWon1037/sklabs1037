package com.sklabs1037.sklabs1037.plan.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🗓️ 내 약속 + 🔊 음성 브리핑 설정 DAO (SQL: resources/mapper/PlanMapper.xml)
 */
@Mapper
public interface PlanDao {

    // ===== 약속 =====
    // row: { userId, title, place, planAt } → 저장 후 row.id
    int insertPlan(Map<String, Object> row);

    int countUpcoming(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    // 내 약속 (from 이상, to 미만 — to가 null이면 끝없이) 시간순
    List<Map<String, Object>> selectMine(@Param("userId") Long userId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    int deletePlan(@Param("userId") Long userId, @Param("id") Long id);

    // 그 기간에 약속이 있는 모든 회원의 약속 (알림용) { userId, title, place, planAt }
    List<Map<String, Object>> selectAllBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 지난 약속 정리 (90일 넘은 것)
    int deleteOld(@Param("before") LocalDateTime before);

    // ===== 음성 브리핑 설정 =====
    Map<String, Object> selectSetting(Long userId);

    int upsertSetting(@Param("userId") Long userId, @Param("morning") String morning, @Param("close") String close, @Param("region") String region);

    // 아침 브리핑을 켠 회원 { userId, nickname, region }
    List<Map<String, Object>> selectMorningUsers();

    // 장 마감 브리핑을 켠 회원 { userId, nickname }
    List<Map<String, Object>> selectCloseUsers();

    String selectNickname(Long userId);

    // 내 관심 종목 + 오늘 전 마지막 종가 { stockId, name, prev, todayClose }
    List<Map<String, Object>> selectWatchPrev(@Param("userId") Long userId, @Param("today") LocalDate today);
}
