package com.sklabs1037.sklabs1037.stock.service;

import com.sklabs1037.sklabs1037.stock.vo.CollectResult;

/**
 * 시세 수집 Service
 */
public interface IStockCollectService {

    // 코스피·코스닥 전 종목 목록을 T_STOCK에 맞춤 (새 종목 추가, 이름 변경 반영) → 저장한 종목 수
    int syncStockMaster();

    // 종목 목록 동기화 + 전 종목 최근 count일 수집 + 상장 주식 수 → 종목 수·저장 건수·실패 수
    // - 토스 키가 없으면 예외 (운영 기록에 실패로 남게)
    CollectResult collectAll(int count);

    // 시세 테이블이 비어 있는지
    boolean isEmpty();

    // 이력이 minRows개보다 적은 종목만 count일 수집 (새 종목 자동 보충)
    CollectResult collectMissingHistory(int minRows, int count);

    // 한 종목 바로 수집 (아직 시세가 없는 종목을 화면에서 처음 열었을 때) → 저장 건수
    int collectHistoryNow(Long stockId, String symbol);

    // 전 종목 상장 주식 수 갱신 (200종목씩 묶어서 토스 호출) → 갱신한 종목 수
    int updateSharesOutstanding();

    // 한 종목의 마지막 시세 날짜가 최근 거래일보다 오래됐으면 최근 몇 일치를 바로 받기 (종목 화면을 열 때)
    // - 같은 종목은 10분에 한 번만 (공휴일처럼 새 시세가 없는 날 계속 부르지 않게)
    boolean refreshIfStale(Long stockId, String symbol, java.time.LocalDate lastDate);

    // 정기 수집을 놓친 종목 따라잡기 (서버가 꺼져 있었거나 토스 오류로 20:30 수집이 안 된 날)
    CollectResult collectStale(int count);

    // 종가가 아직 없는 종목에 토스 현재가를 임시로 (200개씩 묶어서 → 목록에 바로 나오게)
    int fillMissingPrices();
}
