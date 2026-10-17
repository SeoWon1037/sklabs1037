package com.sklabs1037.sklabs1037.dao;

import com.sklabs1037.sklabs1037.vo.UserSignUpVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 회원가입 화면 Dao
 * - @Mapper: MyBatis가 이 interface를 보고 실제 동작하는 코드를 자동으로 만들어줌
 * - 메서드 이름 = UserSignUpMapper.xml의 id
 * implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.1.0' < 라이브러리가 객체 자동 생성 해줌
 */
@Mapper
public interface UserSignUpDao {

    // 이메일 중복 체크 (0이면 가입 가능)
    int countByEmail(UserSignUpVO vo);

    // 회원 등록
    void insertUser(UserSignUpVO vo);


}