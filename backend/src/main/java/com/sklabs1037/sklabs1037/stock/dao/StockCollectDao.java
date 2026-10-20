package com.sklabs1037.sklabs1037.stock.dao;

import com.sklabs1037.sklabs1037.stock.vo.StockCollectVO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 시세 수집 Dao
 * - 메서드 이름 = StockCollectMapper.xml의 id
 */
@Mapper
public interface StockCollectDao {

    // 수집 대상 종목
    List<StockCollectVO> selectTargetStocks();

    // 아직 종가가 없는 종목
    List<StockCollectVO> selectStocksWithoutPrice();

    // 최신 시세가 기준 거래일보다 오래된 종목
    List<StockCollectVO> selectStaleStocks(LocalDate expected);

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

    // 일봉 여러 개 한 번에 저장 (UPSERT)
    int upsertStockPriceBatch(List<StockCollectVO> list);

    // 종목 목록 여러 개 한 번에 저장
    int upsertStockMasterBatch(List<StockCollectVO> list);

    // 상장 주식 수 여러 개 한 번에 저장
    int updateSharesBatch(List<StockCollectVO> list);

    // 이력이 아직 없는 종목에 현재가를 임시 종가로
    int fillMissingLastPrice(List<StockCollectVO> list);

    // 최신 종가 · 전일 종가 · 거래량을 T_STOCK에 복사 (목록·시가총액을 빠르게)
    int updateLatestPrice(Long stockId);
}