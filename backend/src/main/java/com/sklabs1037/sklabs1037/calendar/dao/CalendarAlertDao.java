package com.sklabs1037.sklabs1037.calendar.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 🔔 증시 일정 알림 DAO (SQL: resources/mapper/CalendarAlertMapper.xml)
 */
@Mapper
public interface CalendarAlertDao {

    // 내 설정 (없으면 null = 꺼짐)
    String selectKinds(Long userId);

    int upsert(@Param("userId") Long userId, @Param("kinds") String kinds);

    int delete(Long userId);

    // 켠 회원 전부 { userId, kinds }
    List<Map<String, Object>> selectAll();
}
