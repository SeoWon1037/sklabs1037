package com.sklabs1037.sklabs1037.stock.service;

/**
 * 시세 수집 Service
 */
public interface IStockCollectService {

    // 전 종목 최근 count일 수집 → 저장 건수
    int collectAll(int count);

    // 시세 테이블이 비어 있는지
    boolean isEmpty();

    // 이력이 minRows개보다 적은 종목만 count일 수집 (새 종목 자동 보충)
    int collectMissingHistory(int minRows, int count);
}