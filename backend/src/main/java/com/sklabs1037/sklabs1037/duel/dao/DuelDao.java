package com.sklabs1037.sklabs1037.duel.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * ⚔️ 친구 대결 DAO (SQL: resources/mapper/DuelMapper.xml)
 */
@Mapper
public interface DuelDao {

    int insertDuel(@Param("code") String code, @Param("hostId") Long hostId);

    Map<String, Object> selectByCode(@Param("code") String code);

    List<Map<String, Object>> selectMine(@Param("userId") Long userId, @Param("limit") int limit);

    // 기다리는 중 + 진행 중 (한 사람 3개까지)
    int countOpen(@Param("userId") Long userId);

    // 초대 받기: 기다리는 중이고, 3일 안 지났고, 만든 사람이 아닐 때만 1
    int accept(@Param("code") String code, @Param("guestId") Long guestId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    // 만든 사람이 아직 아무도 안 받은 대결 취소
    int cancel(@Param("code") String code, @Param("hostId") Long hostId);

    // 끝난 날이 지난 진행 중 대결
    List<Map<String, Object>> selectToSettle(@Param("today") LocalDate today);

    int settle(@Param("id") Long id, @Param("hostScore") int hostScore, @Param("guestScore") int guestScore, @Param("winnerId") Long winnerId);

    // 3일 동안 아무도 안 받은 초대 → EXPIRED
    int expireWaiting();
}
