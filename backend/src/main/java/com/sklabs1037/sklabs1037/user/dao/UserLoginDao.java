package com.sklabs1037.sklabs1037.dao;

import com.sklabs1037.sklabs1037.vo.UserLoginVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 로그인 화면 Dao
 * - @Mapper: MyBatis가 이 interface를 보고 실제 동작하는 코드를 자동으로 만들어줌
 * - 메서드 이름 = UserLoginMapper.xml의 id
 */
@Mapper
public interface UserLoginDao {

    // 이메일로 회원 조회 (없으면 null)
    UserLoginVO selectUserByEmail(UserLoginVO vo);
}