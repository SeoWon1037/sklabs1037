package com.sklabs1037.sklabs1037.demo.dao;

import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/**
 * 🧪 체험 계정 DAO (SQL: resources/mapper/DemoMapper.xml)
 */
@Mapper
public interface DemoDao {

    // 체험 계정 { id, email, nickname } (sql/demo.sql을 안 돌렸으면 null)
    Map<String, Object> selectDemoUser();

    // 매일 처음 상태로: 알림 · 가격 알림
    int deleteNotifications(Long userId);

    int deletePriceAlerts(Long userId);
}
