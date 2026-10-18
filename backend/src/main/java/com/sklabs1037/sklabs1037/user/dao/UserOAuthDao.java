package com.sklabs1037.sklabs1037.user.dao;

import com.sklabs1037.sklabs1037.user.vo.UserOAuthVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 소셜 로그인 화면 Dao
 * - 메서드 이름 = UserOAuthMapper.xml의 id
 */
@Mapper
public interface UserOAuthDao {

    // 소셜 회원 조회 (없으면 null)
    UserOAuthVO selectUserByProvider(UserOAuthVO vo);

    // 같은 이메일 회원 수
    int countByEmail(UserOAuthVO vo);

    // 소셜 회원 자동 가입
    void insertOAuthUser(UserOAuthVO vo);
}