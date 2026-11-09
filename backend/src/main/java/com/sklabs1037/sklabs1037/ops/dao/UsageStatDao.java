package com.sklabs1037.sklabs1037.ops.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 📊 사용 통계 DAO (SQL: resources/mapper/UsageStatMapper.xml)
 */
@Mapper
public interface UsageStatDao {

    int addStat(@Param("day") java.time.LocalDate day, @Param("metric") String metric, @Param("key") String key, @Param("cnt") long cnt);

    int addVisitors(@Param("day") java.time.LocalDate day, @Param("vids") List<String> vids);

    int deleteOld();

    int deleteOldVisitors();

    // 최근 n일 { day, visitors, pv }
    List<Map<String, Object>> selectDaily(int days);

    // 최근 n일 화면별 { key, pv }
    List<Map<String, Object>> selectPages(int days);

    // 최근 n일 이벤트 합계 { key, cnt }
    List<Map<String, Object>> selectEvents(int days);

    // 최근 n일 날짜별 가입 { day, signups } (AI · 체험 계정 제외)
    List<Map<String, Object>> selectSignups(int days);

    // 기능별 사용 { feature, today, week }
    List<Map<String, Object>> selectFeatures();
}
