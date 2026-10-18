package com.sklabs1037.sklabs1037.service;

/**
 * 뉴스 수집 Service
 */
public interface INewsCollectService {

    // 시장 뉴스 + 종목별 뉴스 수집 → 새로 저장된 건수
    int collectAll();
}