package com.sklabs1037.sklabs1037.news.service;

import com.sklabs1037.sklabs1037.news.vo.NewsSummaryVO;

/**
 * AI 뉴스 요약 Service
 */
public interface INewsSummaryService {

    // 시장 + 전 종목 요약 → 저장한 건수
    int summarizeAll();

    // 최근 n시간 안에 만든 요약이 있는지
    boolean hasRecentSummary(int withinHours);

    // 화면용 요약 조회 (stockId 가 없으면 시장 요약)
    NewsSummaryVO getSummary(Long stockId);
}