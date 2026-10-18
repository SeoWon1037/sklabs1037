package com.sklabs1037.sklabs1037.user.service;

import com.sklabs1037.sklabs1037.user.vo.UserOAuthVO;

/**
 * 소셜 로그인 화면 Service
 * - "무슨 기능이 있는지" 목록만 적는 곳
 * - 실제 동작은 UserOAuthServiceImpl에서 구현
 */
public interface IUserOAuthService {

    // ===== 네이버 =====

    // 네이버 로그인 화면 주소 만들기
    String getNaverLoginUrl(String state);

    // 네이버 로그인 처리 (기존 회원이면 그대로, 없으면 자동 가입)
    UserOAuthVO loginWithNaver(String code, String state) throws Exception;

    // ===== 카카오 =====

    // 카카오 로그인 화면 주소 만들기
    String getKakaoLoginUrl(String state);

    // 카카오 로그인 처리 (기존 회원이면 그대로, 없으면 자동 가입)
    UserOAuthVO loginWithKakao(String code) throws Exception;
}