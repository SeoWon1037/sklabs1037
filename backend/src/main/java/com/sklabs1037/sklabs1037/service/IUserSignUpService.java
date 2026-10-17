package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.vo.UserSignUpVO;

/**
 * 회원가입 화면 Service
 * - "무슨 기능이 있는지" 목록만 적는 곳
 * - 실제 동작은 UserSignUpServiceImpl에서 구현
 */
public interface IUserSignUpService {

    // 이메일 중복 체크 (true면 이미 사용 중)
    boolean isEmailDuplicated(UserSignUpVO vo) throws Exception;

    // 인증번호 메일 발송
    void sendVerifyCode(UserSignUpVO vo) throws Exception;

    // 인증번호 확인
    void verifyCode(UserSignUpVO vo) throws Exception;

    // 회원가입
    void signUp(UserSignUpVO vo) throws Exception;
}