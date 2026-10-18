package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.vo.StockMainVO;

import java.util.List;

/**
 * 메인 화면(종목 대시보드) Service
 */
public interface IStockMainService {

    // 종목 요약 + 최근 N거래일 차트 데이터
    List<StockMainVO> getDashboard(int days);
}