package com.sklabs1037.sklabs1037.stock.service;

import com.sklabs1037.sklabs1037.stock.vo.StockMainVO;

import java.util.List;
import java.util.Map;

/**
 * 주식 종목 화면 Service
 */
public interface IStockMainService {

    // 기본 목록 (시가총액 상위 limit개 + 주요 종목) + 최근 N거래일 미니 차트 데이터
    List<StockMainVO> getDashboard(int days, int limit);

    // 종목 검색 (이름 일부 / 종목코드 앞자리)
    List<StockMainVO> search(String query, int limit);

    // 종목 상세 (종목 정보 + 일봉) → 없는 종목이면 null
    // - 등록은 됐는데 시세가 아직 없는 종목이면 그 자리에서 토스에서 받아옴
    Map<String, Object> getDetail(String symbol);
}