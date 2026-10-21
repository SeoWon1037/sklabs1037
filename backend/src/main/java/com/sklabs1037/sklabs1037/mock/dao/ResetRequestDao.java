package com.sklabs1037.sklabs1037.mock.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * ♻️ 모의투자 계좌 초기화 신청 DAO (SQL: resources/mapper/ResetRequestMapper.xml)
 */
@Mapper
public interface ResetRequestDao {

    // 같은 사람이 이미 기다리는 신청이 있으면 0 (ON CONFLICT DO NOTHING)
    int insertRequest(@Param("userId") Long userId, @Param("reason") String reason);

    // 내 기다리는 신청 / 마지막으로 처리된 신청
    Map<String, Object> selectMyPending(@Param("userId") Long userId);

    Map<String, Object> selectMyLastHandled(@Param("userId") Long userId);

    int cancel(@Param("userId") Long userId);

    // 관리자: 기다리는 신청 + 최근 처리 30개
    List<Map<String, Object>> selectForAdmin();

    int countPending();

    Map<String, Object> selectRequest(@Param("id") Long id);

    // PENDING일 때만 1
    int handle(@Param("id") Long id, @Param("status") String status, @Param("adminId") Long adminId, @Param("note") String note);

    List<Long> selectAdminIds();
}
