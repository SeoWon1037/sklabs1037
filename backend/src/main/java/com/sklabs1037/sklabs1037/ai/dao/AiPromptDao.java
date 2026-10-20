package com.sklabs1037.sklabs1037.ai.dao;

import com.sklabs1037.sklabs1037.ai.vo.AiPromptVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 🔁 AI 비서 프롬프트 버전 DAO (SQL: resources/mapper/AiPromptMapper.xml)
 */
@Mapper
public interface AiPromptDao {

    // 지금 적용 중인 프롬프트 (없으면 null → 코드의 기본 프롬프트 = 버전 0)
    AiPromptVO selectActive();

    AiPromptVO selectByVersion(int version);

    int selectMaxVersion();

    // 최근 버전들 (관리자 화면)
    List<AiPromptVO> selectPrompts(int limit);

    // 마지막으로 개선안을 만든 시각 (밀리초, 없으면 null)
    Long selectLastRunAt();

    int insertPrompt(AiPromptVO vo);

    // 적용 중인 것 → RETIRED
    int retireActive(AiPromptVO vo);

    // 상태 바꾸기 (CANDIDATE → ACTIVE / REJECTED, RETIRED → ACTIVE 되돌리기)
    int updateStatus(AiPromptVO vo);

    // 👎 받은 대화 (sinceAt 이후, 최신순)
    List<AiPromptVO> selectDownChats(AiPromptVO vo);

    // 👍 받은 대화 (회귀 확인용, 최신순)
    List<AiPromptVO> selectUpChats(int limit);

    // 프롬프트 버전별 대화 수·평가 수·👍·👎
    List<AiPromptVO> selectVersionStats();
}
