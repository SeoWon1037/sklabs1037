package com.sklabs1037.sklabs1037.league.dao;

import com.sklabs1037.sklabs1037.league.vo.AiLeagueVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI 투자 리그 DAO (SQL: resources/mapper/AiLeagueMapper.xml)
 */
@Mapper
public interface AiLeagueDao {

    // 매매하는 AI 투자자들
    List<AiLeagueVO> selectTraders();

    // 그날 일지가 이미 있는지
    int countLog(AiLeagueVO vo);

    // 일지 저장 (같은 날 두 번이면 0)
    int insertLog(AiLeagueVO vo);

    // 최근 일지 (전체 AI)
    List<AiLeagueVO> selectRecentLogs(int limit);

    // 후보 종목: 시가총액 상위 + 5일·20일 수익률 + 변동성
    List<AiLeagueVO> selectUniverse(int limit);

    // 최근 종목 뉴스 제목 (종목 연결된 것)
    List<AiLeagueVO> selectStockNews(int limit);

    // 최근 시장 뉴스 제목
    List<AiLeagueVO> selectMarketNews(int limit);
}
