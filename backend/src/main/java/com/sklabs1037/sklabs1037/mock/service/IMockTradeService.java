package com.sklabs1037.sklabs1037.mock.service;

import com.sklabs1037.sklabs1037.mock.vo.MockTradeVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 모의투자 Service
 */
public interface IMockTradeService {

    // 내 계좌 요약 + 보유 종목 + 미체결 주문 + 주문 가능 종목 (계좌가 없으면 1,000만 원으로 만들어 줌)
    Map<String, Object> getAccount(Long userId);

    // 내 거래 내역 (최신순)
    Map<String, Object> getTrades(Long userId, int page, int size);

    // 주문 (request: stockId, side, quantity, orderType=MARKET|LIMIT, limitPrice)
    // → 시장가는 바로 체결 / 지정가는 대기 (조건이 이미 맞으면 바로 체결)
    Map<String, Object> order(Long userId, MockTradeVO request);

    // 내 지정가 주문 취소
    void cancelOrder(Long userId, Long orderId);

    // 초기화: 주문·보유 종목·거래 내역 삭제, 현금을 시작 자금으로
    void reset(Long userId);

    // 수익률 랭킹 TOP N (+ 로그인했으면 내 순위)
    Map<String, Object> getRanking(Long userId, int size);

    // ===== 스케줄러용 =====

    // 지금 가격으로 체결 조건이 맞는 오늘 주문 번호들
    List<Long> findFillableOrderIds();

    // 주문 1건 체결 (그사이 취소됐거나 가격이 바뀌었으면 false)
    boolean fillOrder(Long orderId);

    // 유효한 거래일이 기준일 이전·같은 날인 미체결 주문 번호들
    List<Long> findExpiredOrderIds(LocalDate upTo);

    // 주문 1건 만료 (묶어둔 돈 돌려줌)
    boolean expireOrder(Long orderId);
}
