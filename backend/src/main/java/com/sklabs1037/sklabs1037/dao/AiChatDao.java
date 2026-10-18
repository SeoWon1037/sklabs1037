package com.sklabs1037.sklabs1037.dao;

import com.sklabs1037.sklabs1037.vo.AiStockVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI 주식 비서 Dao (도구가 DB를 조회할 때 사용)
 * - 메서드 이름 = AiChatMapper.xml의 id
 */
@Mapper
public interface AiChatDao {

    // 종목 찾기
    List<AiStockVO> selectStocks(AiStockVO vo);

    // 최근 N일 일봉
    List<AiStockVO> selectPriceHistory(AiStockVO vo);

    // 종목 설명 문서 검색 (RAG)
    List<AiStockVO> searchDocs(AiStockVO vo);
}