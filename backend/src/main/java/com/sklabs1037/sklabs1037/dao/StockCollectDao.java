package com.sklabs1037.sklabs1037.dao;

import com.sklabs1037.sklabs1037.vo.StockCollectVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 시세 수집 Dao
 * - 메서드 이름 = StockCollectMapper.xml의 id
 */
@Mapper
public interface StockCollectDao {

    // 수집 대상 종목 목록
    List<StockCollectVO> selectTargetStocks();

    // 쌓인 시세 건수
    int countPrices();

    // 시세 UPSERT (있으면 수정, 없으면 추가)
    void upsertStockPrice(StockCollectVO vo);
}