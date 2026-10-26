package com.sklabs1037.sklabs1037.ops.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🔌 외부 연동 기록 Dao (SQL: resources/mapper/ApiCallLogMapper.xml)
 */
@Mapper
public interface ApiCallLogDao {

    // 모아 둔 기록 한꺼번에 저장 (INSERT 한 번에 여러 줄)
    int insertBatch(@Param("list") List<Map<String, Object>> list);

    // 서비스별: 호출 수 · 실패 수 · 평균 / 느린 5%(p95) 응답 시간 · 마지막 호출 · 마지막 실패와 이유
    List<Map<String, Object>> selectServiceStats(@Param("since") OffsetDateTime since);

    // 시간대별 (최근 24시간, 한국 시간 기준 1시간 단위) 서비스별 호출 수 · 실패 수
    List<Map<String, Object>> selectHourly(@Param("since") OffsetDateTime since);

    // 기능별 (서비스 + 경로) 호출 수 · 실패 수 · 평균 응답 — 실패 많은 순
    List<Map<String, Object>> selectActionStats(@Param("since") OffsetDateTime since, @Param("limit") int limit);

    // 최근 실패
    List<Map<String, Object>> selectRecentFails(@Param("limit") int limit);

    // 오래된 기록 삭제
    int deleteOld(@Param("keepDays") int keepDays);
}
