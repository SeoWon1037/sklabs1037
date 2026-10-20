package com.sklabs1037.sklabs1037.mock.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 🩺 포트폴리오 AI 진단 기록 DAO (SQL: resources/mapper/DiagnosisMapper.xml)
 */
@Mapper
public interface DiagnosisDao {

    Map<String, Object> selectLast(@Param("userId") Long userId);

    int countToday(@Param("userId") Long userId);

    int insertDiagnosis(@Param("userId") Long userId, @Param("statsJson") String statsJson, @Param("resultJson") String resultJson);
}
