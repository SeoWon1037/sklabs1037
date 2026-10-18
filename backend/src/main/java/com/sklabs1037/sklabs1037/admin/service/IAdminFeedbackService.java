package com.sklabs1037.sklabs1037.admin.service;

import com.sklabs1037.sklabs1037.admin.vo.AdminFeedbackVO;

import java.util.List;
import java.util.Map;

/**
 * 관리자 피드백 대시보드 Service
 */
public interface IAdminFeedbackService {

    // 관리자인지 (DB의 ROLE 확인)
    boolean isAdmin(Long userId);

    // 요약 + 도구별 + 날짜별
    Map<String, Object> getDashboard();

    // 대화 목록
    List<AdminFeedbackVO> getChats(AdminFeedbackVO vo);
}