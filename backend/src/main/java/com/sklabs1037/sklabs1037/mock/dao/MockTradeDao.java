package com.sklabs1037.sklabs1037.mock.dao;

import com.sklabs1037.sklabs1037.mock.vo.MockTradeVO;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 모의투자 Dao
 * - 메서드 이름 = MockTradeMapper.xml의 id
 */
@Mapper
public interface MockTradeDao {

    // 종목 + 최근 종가
    List<MockTradeVO> selectStocks();

    // ===== 계좌 =====
    int insertAccount(MockTradeVO vo);

    MockTradeVO selectAccount(Long userId);

    MockTradeVO selectAccountForUpdate(Long userId);

    int updateCash(MockTradeVO vo);

    int resetAccount(Long userId);

    // ===== 보유 종목 =====
    List<MockTradeVO> selectHoldings(Long userId);

    MockTradeVO selectHolding(MockTradeVO vo);

    int upsertHolding(MockTradeVO vo);

    int deleteHolding(MockTradeVO vo);

    int deleteAllHoldings(Long userId);

    // ===== 거래 내역 =====
    int insertTrade(MockTradeVO vo);

    List<MockTradeVO> selectTrades(MockTradeVO vo);

    int countTrades(Long userId);

    int deleteAllTrades(Long userId);

    // ===== 지정가 주문 =====
    int insertOrder(MockTradeVO vo);

    MockTradeVO selectOrder(Long orderId);

    MockTradeVO selectOrderForUpdate(Long orderId);

    int updateOrderStatus(MockTradeVO vo);

    List<MockTradeVO> selectPendingOrders(Long userId);

    int countPendingOrders(Long userId);

    int sumPendingSellQuantity(MockTradeVO vo);

    BigDecimal sumPendingCash(Long userId);

    List<MockTradeVO> selectMatchableOrders(LocalDate today);

    List<MockTradeVO> selectExpiredOrders(MockTradeVO vo);

    int deleteAllOrders(Long userId);

    // ===== 랭킹 =====
    List<MockTradeVO> selectRankingAccounts();

    List<MockTradeVO> selectRankingHoldings();
}
