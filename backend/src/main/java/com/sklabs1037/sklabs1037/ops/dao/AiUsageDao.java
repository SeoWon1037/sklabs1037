package com.sklabs1037.sklabs1037.ops.dao;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 💸 AI 비용 DAO (SQL: resources/mapper/AiUsageMapper.xml)
 */
@Mapper
public interface AiUsageDao {

    // 날짜 · 기능별로 더하기 (없으면 새 줄)
    int upsert(Map<String, Object> row);

    // 오늘 쓴 돈 (한국 날짜)
    Double selectTodayCost();

    // 설정 { dailyUsd, paused, updAt }
    Map<String, Object> selectBudget();

    int updateBudget(Map<String, Object> row);

    // 최근 n일 날짜별 { day, calls, fails, cost }
    List<Map<String, Object>> selectDays(int days);

    // 오늘 기능별
    List<Map<String, Object>> selectTodayFeatures();

    // 이번 달 합계 { calls, cost }
    Map<String, Object> selectMonth();
}
