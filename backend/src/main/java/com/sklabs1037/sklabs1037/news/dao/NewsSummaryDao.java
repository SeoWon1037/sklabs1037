package com.sklabs1037.sklabs1037.news.dao;

import com.sklabs1037.sklabs1037.news.vo.NewsSummaryVO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 뉴스 요약 Dao
 * - 메서드 이름 = NewsSummaryMapper.xml의 id
 */
@Mapper
public interface NewsSummaryDao {

    // 요약 대상 종목 목록
    List<NewsSummaryVO> selectTargetStocks();

    // 요약에 쓸 최근 뉴스 제목 (시장 또는 종목)
    List<NewsSummaryVO> selectRecentTitles(NewsSummaryVO vo);

    // 요약 저장 (있으면 덮어쓰기)
    int upsertSummary(NewsSummaryVO vo);

    // 요약 삭제 (뉴스가 부족해 새로 못 만든 경우, 예전 요약이 남지 않게)
    int deleteSummary(String targetKey);

    // 이 시각 이후에 만든 요약 개수
    int countSummarySince(LocalDateTime fromAt);

    // 요약 1건 조회 (화면용)
    NewsSummaryVO selectSummary(String targetKey);
}