package com.sklabs1037.sklabs1037.dao;

import com.sklabs1037.sklabs1037.vo.StockMainVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 메인 화면(종목 대시보드) Dao
 * - 메서드 이름 = StockMainMapper.xml의 id
 */
@Mapper
public interface StockMainDao {

    // 종목별 최신 종가 + 전일 대비
    List<StockMainVO> selectStockSummary();

    // 종목별 최근 N거래일 종가 (차트용)
    List<StockMainVO> selectRecentPrices(StockMainVO vo);
}