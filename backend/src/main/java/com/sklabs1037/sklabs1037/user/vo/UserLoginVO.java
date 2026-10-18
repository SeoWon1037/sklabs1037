package com.sklabs1037.sklabs1037.user.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 로그인 화면에서 쓰는 값을 담는 VO
 * - 화면 1개 = VO 1개
 * - 사용 테이블: T_USER
 * - 로그인 성공 후에는 이 객체가 세션에 저장됨 (비밀번호는 지우고 저장)
 */
@Getter
@Setter
public class UserLoginVO {

    private Long id;            // 회원 번호
    private String email;       // 로그인 아이디
    private String password;    // 입력한 비밀번호 / DB의 암호화된 비밀번호
    private String nickname;    // 닉네임
    private String role;        // USER / ADMIN

}