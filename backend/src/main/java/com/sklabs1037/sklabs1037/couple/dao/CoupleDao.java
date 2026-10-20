package com.sklabs1037.sklabs1037.couple.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 💕 커플 맺기 DAO (SQL: resources/mapper/CoupleMapper.xml)
 */
@Mapper
public interface CoupleDao {

    // 닉네임이 정확히 같은 회원 (탈퇴 제외)
    List<Map<String, Object>> selectUsersByNickname(@Param("nickname") String nickname);

    // 지금 커플 (나 기준: 상대 닉네임 · 시작일)
    Map<String, Object> selectActive(@Param("userId") Long userId);

    // 신청 중 (내가 보낸 것 / 받은 것)
    List<Map<String, Object>> selectSent(@Param("userId") Long userId);

    List<Map<String, Object>> selectReceived(@Param("userId") Long userId);

    Map<String, Object> selectCouple(@Param("id") Long id);

    int countActive(@Param("userId") Long userId);

    int countPendingBetween(@Param("a") Long a, @Param("b") Long b);

    int countSentPending(@Param("userId") Long userId);

    int insertRequest(@Param("requesterId") Long requesterId, @Param("partnerId") Long partnerId, @Param("startDate") LocalDate startDate);

    // 받은 사람만, 둘 다 커플이 없을 때만 1
    int accept(@Param("id") Long id, @Param("partnerId") Long partnerId);

    int decline(@Param("id") Long id, @Param("partnerId") Long partnerId);

    int cancel(@Param("id") Long id, @Param("requesterId") Long requesterId);

    // 둘 중 한 사람이 끊기
    int end(@Param("id") Long id, @Param("userId") Long userId);

    // 커플이 되면 둘에게 남은 다른 신청은 정리
    int cancelOthers(@Param("a") Long a, @Param("b") Long b);
}
