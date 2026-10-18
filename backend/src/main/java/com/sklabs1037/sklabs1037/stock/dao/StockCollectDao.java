package com.sklabs1037.sklabs1037.stock.dao;

import com.sklabs1037.sklabs1037.stock.vo.StockCollectVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 시세 수집 Dao
 * - 메서드 이름 = StockCollectMapper.xml의 id
 */
@Mapper
public interface StockCollectDao {

    // 수집 대상 종목
    List<StockCollectVO> selectTargetStocks();

    // 시세 전체 건수
    int countPrices();

    // 시세 이력이 부족한 종목
    List<StockCollectVO> selectStocksNeedingHistory(int minRows);

    // 일봉 저장 (UPSERT)
    void upsertStockPrice(StockCollectVO vo);
}