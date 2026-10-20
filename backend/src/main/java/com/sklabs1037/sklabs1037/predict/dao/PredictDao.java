package com.sklabs1037.sklabs1037.predict.dao;

import com.sklabs1037.sklabs1037.predict.vo.PredictVO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 🎯 오를까 내릴까 DAO (SQL: resources/mapper/PredictMapper.xml)
 */
@Mapper
public interface PredictDao {

    // 문제 후보: 주식 시가총액 상위 N개 + 최근 5일 수익률
    List<PredictVO> selectCandidates(int limit);

    // 라운드 문제 넣기 (이미 있으면 0)
    int insertItem(PredictVO vo);

    // 라운드 문제들 (+ 종목 정보, 최근 종가·5일 수익률, 투표 수, 내 선택)
    List<PredictVO> selectRound(PredictVO vo);

    PredictVO selectItem(Long itemId);

    // 투표 (다시 고르면 바꿈)
    int upsertVote(PredictVO vo);

    // 채점할 문제 (결과 없음, 거래일 ≤ upTo)
    List<PredictVO> selectUngraded(LocalDate upTo);

    // 그 날 종가 / 그 전 마지막 종가
    PredictVO selectClose(PredictVO vo);

    PredictVO selectPrevClose(PredictVO vo);

    int gradeItem(PredictVO vo);

    int gradeVotes(PredictVO vo);

    // 가장 최근에 채점된 거래일
    LocalDate selectLastGradedDate();

    // 내 기록 (채점된 것, 최신순)
    List<PredictVO> selectMyGraded(PredictVO vo);

    // 랭킹 (fromDate 이후, 채점 minGraded개 이상)
    List<PredictVO> selectLeaderboard(PredictVO vo);

    // 모멘텀 봇·사람들(다수결) 성적
    PredictVO selectBotRecord();

    PredictVO selectCrowdRecord();
}
