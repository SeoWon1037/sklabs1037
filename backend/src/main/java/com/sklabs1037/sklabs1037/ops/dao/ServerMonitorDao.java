package com.sklabs1037.sklabs1037.ops.dao;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 🖥 DB 서버 상태 (PostgreSQL 통계 뷰, SQL: resources/mapper/ServerMonitorMapper.xml)
 * - pg_stat_database / pg_stat_activity / pg_stat_user_tables 는 PostgreSQL 기본 통계 → 따로 설치할 것 없음
 * - RDS 의 CPU·디스크 IO 같은 장비 숫자는 CloudWatch 에만 있어서 여기서는 DB가 직접 알려주는 것만
 */
@Mapper
public interface ServerMonitorDao {

    // 응답 속도 재기용
    int ping();

    // 이 DB: 버전·켜진 시각·크기·연결 수·누적 트랜잭션·캐시 적중·교착
    Map<String, Object> selectDbStats();

    // 연결 상태별 수 (active / idle / idle in transaction …)
    List<Map<String, Object>> selectConnStates();

    // 지금 가장 오래 돌고 있는 쿼리 (초)
    Double selectLongestActive();

    // 큰 테이블 6개
    List<Map<String, Object>> selectTopTables();

    List<Long> selectAdminIds();
}
