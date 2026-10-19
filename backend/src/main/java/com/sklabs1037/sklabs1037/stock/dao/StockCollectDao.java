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

    // 상장 주식 수 저장
    int updateSharesOutstanding(StockCollectVO vo);

    // 종목 목록 저장 (종목코드가 있으면 이름·시장·종류 갱신, 없으면 추가)
    int upsertStockMaster(StockCollectVO vo);

    // 최신 종가 · 전일 종가 · 거래량을 T_STOCK에 복사 (목록·시가총액을 빠르게)
    int updateLatestPrice(Long stockId);
}