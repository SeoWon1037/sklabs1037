package com.sklabs1037.sklabs1037.qa.dao;

import com.sklabs1037.sklabs1037.qa.vo.QaRunVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 🤖 AI QA 봇 DAO (SQL: resources/mapper/QaRunMapper.xml)
 */
@Mapper
public interface QaRunDao {

    // 새 점검 (runId 채워짐)
    int insertRun(QaRunVO vo);

    // 분석 결과 저장
    int updateRun(QaRunVO vo);

    // 최근 점검 목록 (캡처 제외)
    List<QaRunVO> selectRuns(int limit);

    // 점검 하나 (캡처 포함)
    QaRunVO selectRun(Long runId);

    // 최근 keep개만 남기기
    int deleteOld(QaRunVO vo);
}
