package com.sklabs1037.sklabs1037.user.dao;

import com.sklabs1037.sklabs1037.user.vo.UserFindVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 아이디 찾기 / 비밀번호 재설정 Dao
 * - 메서드 이름 = UserFindMapper.xml의 id
 */
@Mapper
public interface UserFindDao {

    // 닉네임으로 회원 조회
    List<UserFindVO> selectUsersByNickname(UserFindVO vo);

    // 이메일로 회원 조회
    UserFindVO selectUserByEmail(UserFindVO vo);

    // 1분 안에 보낸 코드 수
    int countRecentSend(UserFindVO vo);

    // 재설정 인증코드 저장
    void insertResetCode(UserFindVO vo);

    // 가장 최근 재설정 코드
    UserFindVO selectLatestResetCode(UserFindVO vo);

    // 인증 성공 표시
    void updateVerifySuccess(UserFindVO vo);

    // 새 비밀번호 저장 (수정된 건수)
    int updatePassword(UserFindVO vo);
}