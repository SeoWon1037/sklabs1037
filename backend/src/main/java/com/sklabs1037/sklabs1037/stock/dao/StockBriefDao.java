package com.sklabs1037.sklabs1037.stock.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 🧾 종목 한 장 요약 DAO (SQL: resources/mapper/StockBriefMapper.xml)
 */
@Mapper
public interface StockBriefDao {

    Map<String, Object> selectStock(@Param("stockId") Long stockId);

    // 최근 종가 (최신순)
    List<Map<String, Object>> selectCloses(@Param("stockId") Long stockId, @Param("limit") int limit);

    // 최근 n일 이 종목 뉴스 (제목이 같으면 하나만, 최신순)
    List<Map<String, Object>> selectNews(@Param("stockId") Long stockId, @Param("days") int days, @Param("limit") int limit);

    // 최근 n일 토론: 글 수 · 오를 것 · 내릴 것
    Map<String, Object> selectBoardCounts(@Param("stockId") Long stockId, @Param("days") int days);

    // 최근 글 내용 (지운 글 · 신고로 가려진 글 빼고)
    List<String> selectRecentPosts(@Param("stockId") Long stockId, @Param("limit") int limit);

    Map<String, Object> selectBrief(@Param("stockId") Long stockId);

    int upsertBrief(@Param("stockId") Long stockId, @Param("briefJson") String briefJson);
}
