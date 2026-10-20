package com.sklabs1037.sklabs1037.brief.dao;

import com.sklabs1037.sklabs1037.brief.vo.MorningBriefVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 아침 브리핑 메일 Dao
 * - 메서드 이름 = MorningBriefMapper.xml의 id
 */
@Mapper
public interface MorningBriefDao {

    // 내 신청 상태 (없으면 null) + 이메일
    MorningBriefVO selectMyBrief(Long userId);

    // 켜기 / 끄기 (처음이면 추가)
    int upsertEnabled(MorningBriefVO vo);

    // 오늘 아직 안 받은 신청자 (이메일 있는 회원만)
    List<MorningBriefVO> selectTargets(MorningBriefVO vo);

    // 오늘 보냄으로 먼저 표시 → 1이면 내가 보낼 차례 (서버가 두 대여도 한 번만)
    int markSent(MorningBriefVO vo);

    // 메일 실패 → 되돌리기 (다음 실행 때 다시)
    int revertSent(MorningBriefVO vo);
}
