package com.sklabs1037.sklabs1037.stock.dao;

import com.sklabs1037.sklabs1037.stock.vo.StockMainVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 주식 종목 화면 Dao
 * - 메서드 이름 = StockMainMapper.xml의 id
 */
@Mapper
public interface StockMainDao {

    // 기본 목록: 시가총액 상위 N개 + 주요 종목
    List<StockMainVO> selectStockSummary(StockMainVO vo);

    // 종목 검색
    List<StockMainVO> selectStockSearch(StockMainVO vo);

    // 종목별 최근 N거래일 종가 (미니 차트용)
    List<StockMainVO> selectRecentPrices(StockMainVO vo);

    // 종목 정보 + 사업 개요 문서
    StockMainVO selectStockDetail(StockMainVO vo);

    // 캔들 차트용 일봉
    List<StockMainVO> selectCandles(StockMainVO vo);
}