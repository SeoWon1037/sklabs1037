package com.sklabs1037.sklabs1037.autofix.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 🤖 AI 자동 수정 DAO (SQL: resources/mapper/AutofixMapper.xml)
 */
@Mapper
public interface AutofixDao {

    int insertRun(Map<String, Object> row);

    int countToday();

    // 이 문의에 아직 끝나지 않은 수정이 있는지 (REQUESTED · WORKING · READY)
    int countActive(@Param("inquiryId") Long inquiryId);

    Map<String, Object> selectRun(@Param("id") Long id);

    Map<String, Object> selectByIssue(@Param("issueNo") int issueNo);

    List<Map<String, Object>> selectRuns();

    int countReady();

    int updateFromCallback(Map<String, Object> row);

    int handle(@Param("id") Long id, @Param("status") String status, @Param("adminId") Long adminId, @Param("error") String error);
}
