package com.sklabs1037.sklabs1037.persona.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 🧭 투자 성향 테스트 DAO (SQL: resources/mapper/PersonaMapper.xml)
 */
@Mapper
public interface PersonaDao {

    int insertResult(@Param("userId") Long userId, @Param("typeCode") String typeCode,
                     @Param("risk") int risk, @Param("horizon") int horizon,
                     @Param("basis") int basis, @Param("discipline") int discipline);

    // 유형별 인원 [{TYPE_CODE, CNT}]
    List<Map<String, Object>> selectTypeCounts();
}
