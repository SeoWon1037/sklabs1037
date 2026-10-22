package com.sklabs1037.sklabs1037.support.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 📮 문의함 DAO (SQL: resources/mapper/InquiryMapper.xml)
 */
@Mapper
public interface InquiryDao {

    int insertInquiry(Map<String, Object> row);

    int countToday(@Param("userId") Long userId);

    List<Map<String, Object>> selectMine(@Param("userId") Long userId);

    Map<String, Object> selectOne(@Param("id") Long id);

    // 관리자: status 가 null 이면 전체 (최근 100개)
    List<Map<String, Object>> selectForAdmin(@Param("status") String status);

    int countOpen();

    int updateTriage(Map<String, Object> triage);

    int reply(@Param("id") Long id, @Param("reply") String reply, @Param("adminId") Long adminId, @Param("status") String status);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    List<Long> selectAdminIds();
}
