package com.sklabs1037.sklabs1037.user.dao;

import com.sklabs1037.sklabs1037.user.vo.UserMyPageVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 마이페이지 Dao
 * - 메서드 이름 = UserMyPageMapper.xml의 id
 */
@Mapper
public interface UserMyPageDao {

    // 내 정보 (탈퇴한 회원 제외)
    UserMyPageVO selectMyInfo(Long id);

    // 닉네임 변경
    int updateNickname(UserMyPageVO vo);

    // 비밀번호 변경
    int updatePassword(UserMyPageVO vo);

    // 탈퇴: 개인정보를 지우고 탈퇴일 기록 (소프트 삭제)
    int withdrawUser(Long id);

    // 탈퇴: 관심 종목 삭제
    int deleteWatchlist(Long id);

    // 내 AI 질문 기록 (최신순, limit/offset)
    List<UserMyPageVO> selectMyChats(UserMyPageVO vo);

    // 내 AI 질문 기록 전체 개수
    int countMyChats(Long id);
}