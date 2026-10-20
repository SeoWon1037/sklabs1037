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

    // ===== 💗 하트 =====
    int insertHeart(@Param("coupleId") Long coupleId, @Param("fromId") Long fromId);

    // 보낸 사람 기준 하트 수 (전체 / 오늘)
    Map<String, Object> selectHeartCounts(@Param("coupleId") Long coupleId, @Param("fromId") Long fromId);

    // 상대가 아직 안 읽은 하트 알림은 지우고 새로 1줄 (알림함이 하트로 가득 차지 않게)
    int deleteUnreadHeartNotifications(@Param("userId") Long userId);

    // ===== 📸 기념일 앨범 =====
    int insertPhoto(Map<String, Object> photo);   // coupleId, uploaderId, milestone, caption, contentType, bytes, data(byte[])

    // 사진 목록 (그림 데이터 빼고)
    List<Map<String, Object>> selectPhotos(@Param("coupleId") Long coupleId);

    // 그림 데이터 1장 { coupleId, contentType, data }
    Map<String, Object> selectPhotoData(@Param("id") Long id);

    int countPhotos(@Param("coupleId") Long coupleId, @Param("milestone") String milestone);

    int deletePhoto(@Param("id") Long id, @Param("coupleId") Long coupleId);
}
