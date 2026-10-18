package com.sklabs1037.sklabs1037.service;

/**
 * 시세 수집 Service
 * - "무슨 기능이 있는지" 목록만 적는 곳
 */
public interface IStockCollectService {

    // 전체 종목 시세 수집 (최근 count일) → 저장한 건수
    int collectAll(int count);

    // 시세가 하나도 없는지 (초기 적재 필요 여부)
    boolean isEmpty();
}