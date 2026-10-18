package com.sklabs1037.sklabs1037.user.service;

import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;

/**
 * 로그인 화면 Service
 * - "무슨 기능이 있는지" 목록만 적는 곳
 * - 실제 동작은 UserLoginServiceImpl에서 구현
 */
public interface IUserLoginService {

    // 로그인 (성공하면 회원 정보, 실패하면 IllegalArgumentException)
    UserLoginVO login(UserLoginVO vo) throws Exception;
}