package com.sklabs1037.sklabs1037.alert.service;

import java.util.List;
import java.util.Map;

/**
 * 목표가 알림 Service
 */
public interface IPriceAlertService {

    // 내 알림 (stockId가 있으면 그 종목만) + 종목 현재가
    List<Map<String, Object>> getMyAlerts(Long userId, Long stockId);

    // 알림 만들기 (condition: ABOVE / BELOW)
    void create(Long userId, Long stockId, String condition, java.math.BigDecimal targetPrice);

    // 삭제 (본인 것만)
    void delete(Long userId, Long alertId);

    // 보낸 알림 다시 켜기
    void reactivate(Long userId, Long alertId);

    // 조건을 넘은 알림 메일 발송 (스케줄러) → 보낸 개수
    int checkAndNotify();
}
