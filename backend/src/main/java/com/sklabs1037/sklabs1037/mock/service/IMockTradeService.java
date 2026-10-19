package com.sklabs1037.sklabs1037.mock.service;

import java.util.Map;

/**
 * 모의투자 Service
 */
public interface IMockTradeService {

    // 내 계좌 요약 + 보유 종목 + 주문 가능 종목 (계좌가 없으면 1,000만 원으로 만들어 줌)
    Map<String, Object> getAccount(Long userId);

    // 내 거래 내역 (최신순)
    Map<String, Object> getTrades(Long userId, int page, int size);

    // 시장가 주문 (side: BUY / SELL) → 체결 결과
    Map<String, Object> order(Long userId, Long stockId, String side, int quantity);

    // 초기화: 보유 종목·거래 내역 삭제, 현금을 시작 자금으로
    void reset(Long userId);

    // 수익률 랭킹 TOP N (+ 로그인했으면 내 순위)
    Map<String, Object> getRanking(Long userId, int size);
}
