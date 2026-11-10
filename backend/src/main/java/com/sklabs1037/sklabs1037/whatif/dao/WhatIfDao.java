package com.sklabs1037.sklabs1037.whatif.dao;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * ⏳ "그때 샀더라면" DAO (SQL: resources/mapper/WhatIfMapper.xml) — 읽기만 함
 */
@Mapper
public interface WhatIfDao {

    // { id, symbol, name, market, securityType } (없거나 안 쓰는 종목이면 null)
    Map<String, Object> selectStock(String symbol);

    // 우리 DB에 쌓인 종가 (날짜순) [{ day(yyyy-MM-dd), close }]
    List<Map<String, Object>> selectCloses(Long stockId);
}
