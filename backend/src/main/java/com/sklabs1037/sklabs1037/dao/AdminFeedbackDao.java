package com.sklabs1037.sklabs1037.dao;

import com.sklabs1037.sklabs1037.vo.AdminFeedbackVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 관리자 피드백 대시보드 Dao
 * - 메서드 이름 = AdminFeedbackMapper.xml의 id
 */
@Mapper
public interface AdminFeedbackDao {

    // 회원 권한
    String selectUserRole(AdminFeedbackVO vo);

    // 요약
    AdminFeedbackVO selectSummary();

    // 도구별 사용 횟수
    List<AdminFeedbackVO> selectToolUsage();

    // 최근 7일 날짜별
    List<AdminFeedbackVO> selectDailyStats();

    // 대화 목록
    List<AdminFeedbackVO> selectChats(AdminFeedbackVO vo);
}