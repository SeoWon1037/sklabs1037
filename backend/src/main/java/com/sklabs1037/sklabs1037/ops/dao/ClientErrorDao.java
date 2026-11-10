package com.sklabs1037.sklabs1037.ops.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 🐞 화면 오류 DAO (SQL: resources/mapper/ClientErrorMapper.xml)
 */
@Mapper
public interface ClientErrorDao {

    // 이미 있는 오류면 횟수 +1 (DONE이었으면 OPEN으로) → 바뀐 줄 수 (없던 오류면 0)
    int bump(Map<String, Object> report);

    // 새 오류 넣기 (그 사이 누가 먼저 넣었으면 0)
    int insert(Map<String, Object> report);

    // 오늘 새로 생긴 오류 종류 수 (하루 한도 확인용)
    int countNewToday();

    // 관리자 목록 (status: OPEN / DONE / IGNORED / null = 전체)
    List<Map<String, Object>> selectList(@Param("status") String status);

    // 상태별 개수 { open, done, ignored }
    Map<String, Object> selectCounts();

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    // 매일 개선 후보: 최근 이틀 안에 2번 이상 난 OPEN 오류 { sig, kind, message, stack, path, cnt }
    List<Map<String, Object>> selectCandidates();

    int deleteOld();
}
