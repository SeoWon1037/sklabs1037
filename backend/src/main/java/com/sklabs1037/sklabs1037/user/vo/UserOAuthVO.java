package com.sklabs1037.sklabs1037.user.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 소셜 로그인(네이버/카카오) 화면에서 쓰는 값을 담는 VO
 * - 화면 1개 = VO 1개
 * - 사용 테이블: T_USER
 */
@Getter
@Setter
public class UserOAuthVO {

    private Long id;            // 회원 번호
    private String email;       // 이메일 (네이버가 알려준 값)
    private String nickname;    // 닉네임 (네이버 별명)
    private String role;        // USER / ADMIN
    private String provider;    // 가입 경로 (NAVER / KAKAO)
    private String providerId;  // 네이버/카카오가 알려준 회원 고유 번호 (절대 안 바뀜)

}