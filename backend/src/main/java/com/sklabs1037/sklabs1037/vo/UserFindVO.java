package com.sklabs1037.sklabs1037.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 아이디 찾기 / 비밀번호 재설정 화면 VO
 * - 사용 테이블: T_USER, T_EMAIL_VERIFY
 */
@Getter
@Setter
public class UserFindVO {

    // ===== 요청 =====
    private String nickname;         // 아이디 찾기: 닉네임
    private String email;            // 비밀번호 재설정: 이메일
    private String code;             // 인증코드
    private String password;         // 새 비밀번호

    // ===== T_USER =====
    private Long userId;
    private String provider;         // LOCAL / NAVER / KAKAO
    private String maskedEmail;      // 가린 이메일 (화면 표시용)
    private LocalDateTime insDay;    // 가입일

    // ===== T_EMAIL_VERIFY =====
    private Long verifyId;
    private String verifyYn;
    private String expiredYn;
}