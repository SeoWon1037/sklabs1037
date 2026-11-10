package com.sklabs1037.sklabs1037.ops.dao;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 🧪 자가 점검 DAO (SQL: resources/mapper/SelfCheckMapper.xml) — DB에 실제로 있는 표 · 칸 이름 읽기
 */
@Mapper
public interface SelfCheckDao {

    // 지금 스키마의 표 이름 (대문자)
    List<String> selectTables();

    // 지금 스키마의 "표.칸" (대문자)
    List<String> selectColumns();
}
