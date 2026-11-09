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

    // ===== 🔁 매일 스스로 개선: 고칠 거리 후보 =====

    // 최근 이틀 QA 봇 결과 중 문제 있는 마지막 1건 (FINDINGS_JSON)
    Map<String, Object> selectLatestQaIssues();

    // 최근 이틀 외부 연동 실패 (서비스·기능별)
    List<Map<String, Object>> selectApiFailures();

    // 최근 이틀 실패한 작업
    List<Map<String, Object>> selectJobFailures();

    // 최근 14일 안에 매일 개선으로 이미 고른 문제
    List<String> selectRecentSignatures();

    // 끝나지 않은 수정 (REQUESTED · WORKING · READY) 이 하나라도 있는지
    int countAnyActive();
}
