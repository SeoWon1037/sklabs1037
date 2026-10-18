package com.sklabs1037.sklabs1037.dao;

import com.sklabs1037.sklabs1037.vo.AiChatVO;
import com.sklabs1037.sklabs1037.vo.AiStockVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI 주식 비서 Dao
 * - 메서드 이름 = AiChatMapper.xml의 id
 */
@Mapper
public interface AiChatDao {

    // [도구] 종목 찾기
    List<AiStockVO> selectStocks(AiStockVO vo);

    // [도구] 최근 N일 일봉
    List<AiStockVO> selectPriceHistory(AiStockVO vo);

    // [도구] 종목 설명 문서 검색 (RAG)
    List<AiStockVO> searchDocs(AiStockVO vo);

    // [도구] 전 종목 N거래일 수익률 순위
    List<AiStockVO> selectReturnRanking(AiStockVO vo);

    // [도구] 최근 뉴스 검색 (RAG)
    List<AiStockVO> searchNews(AiStockVO vo);

    // 대화 기록 저장
    void insertChatLog(AiChatVO vo);

    // 피드백 저장 (수정된 건수 반환)
    int updateRating(AiChatVO vo);
}