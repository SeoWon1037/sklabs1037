package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.vo.StockMainVO;

import java.util.List;
import java.util.Map;

/**
 * 주식 종목 화면 Service
 */
public interface IStockMainService {

    // 종목 요약 + 최근 N거래일 미니 차트 데이터
    List<StockMainVO> getDashboard(int days);

    // 종목 상세 (종목 정보 + 일봉) → 없는 종목이면 null
    Map<String, Object> getDetail(String symbol);
}