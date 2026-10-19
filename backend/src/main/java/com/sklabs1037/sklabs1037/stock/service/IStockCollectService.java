package com.sklabs1037.sklabs1037.stock.service;

/**
 * 시세 수집 Service
 */
public interface IStockCollectService {

    // 코스피·코스닥 전 종목 목록을 T_STOCK에 맞춤 (새 종목 추가, 이름 변경 반영) → 저장한 종목 수
    int syncStockMaster();

    // 종목 목록 동기화 + 전 종목 최근 count일 수집 + 상장 주식 수 → 저장 건수
    int collectAll(int count);

    // 시세 테이블이 비어 있는지
    boolean isEmpty();

    // 이력이 minRows개보다 적은 종목만 count일 수집 (새 종목 자동 보충)
    int collectMissingHistory(int minRows, int count);

    // 한 종목 바로 수집 (아직 시세가 없는 종목을 화면에서 처음 열었을 때) → 저장 건수
    int collectHistoryNow(Long stockId, String symbol);

    // 전 종목 상장 주식 수 갱신 (200종목씩 묶어서 토스 호출) → 갱신한 종목 수
    int updateSharesOutstanding();
}
