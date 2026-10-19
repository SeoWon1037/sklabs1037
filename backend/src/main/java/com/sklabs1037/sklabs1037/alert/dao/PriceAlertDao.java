package com.sklabs1037.sklabs1037.alert.dao;

import com.sklabs1037.sklabs1037.alert.vo.PriceAlertVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 목표가 알림 Dao
 * - 메서드 이름 = PriceAlertMapper.xml의 id
 */
@Mapper
public interface PriceAlertDao {

    List<PriceAlertVO> selectMyAlerts(PriceAlertVO vo);

    int countActiveAlerts(Long userId);

    int insertAlert(PriceAlertVO vo);

    int deleteAlert(PriceAlertVO vo);

    PriceAlertVO selectMyAlert(PriceAlertVO vo);

    int reactivateAlert(PriceAlertVO vo);

    // ===== 스케줄러 =====
    List<PriceAlertVO> selectActiveAlerts();

    int markSent(PriceAlertVO vo);

    int revertSent(Long alertId);
}
