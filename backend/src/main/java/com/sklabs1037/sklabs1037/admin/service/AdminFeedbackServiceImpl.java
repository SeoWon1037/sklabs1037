package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.dao.AdminFeedbackDao;
import com.sklabs1037.sklabs1037.vo.AdminFeedbackVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 피드백 대시보드 Service 구현
 */
@Service
public class AdminFeedbackServiceImpl implements IAdminFeedbackService {

    @Autowired
    private AdminFeedbackDao adminFeedbackDao;

    @Override
    public boolean isAdmin(Long userId) {

        if (userId == null) return false;

        AdminFeedbackVO param = new AdminFeedbackVO();
        param.setUserId(userId);

        return "ADMIN".equals(adminFeedbackDao.selectUserRole(param));

    }

    @Override
    public Map<String, Object> getDashboard() {

        Map<String, Object> result = new HashMap<>();
        result.put("summary", adminFeedbackDao.selectSummary());
        result.put("tools", adminFeedbackDao.selectToolUsage());
        result.put("daily", adminFeedbackDao.selectDailyStats());

        return result;

    }

    @Override
    public List<AdminFeedbackVO> getChats(AdminFeedbackVO vo) {

        return adminFeedbackDao.selectChats(vo);

    }
}