package com.sklabs1037.sklabs1037.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 회원가입 화면에서 쓰는 값을 담는 VO
 * - 화면 1개 = VO 1개
 * - 사용 테이블: T_USER, T_EMAIL_VERIFY
 * - @Getter/@Setter: Lombok이 getEmail(), setEmail() 같은 메서드를 자동으로 만들어줌
 */
@Getter
@Setter
public class UserSignUpVO {

    // ===== T_USER =====
    private Long id;                 // 회원 번호 (가입 후 DB가 만들어준 번호)
    private String email;            // 로그인 아이디
    private String password;         // 비밀번호 (DB에는 암호화해서 저장)
    private String nickname;         // 닉네임
    private String role;             // USER / ADMIN

    private LocalDateTime insDay;    // 가입일
    private Long insUserid;          // 등록자

    // ===== T_EMAIL_VERIFY (이메일 인증) =====
    private String code;             // 6자리 인증번호
    private Long verifyId;           // 인증 기록 번호 (T_EMAIL_VERIFY.ID)
    private String verifyYn;         // 인증 완료 여부 (Y/N)
    private String expiredYn;        // 만료 여부 (Y/N, DB에서 계산해서 넘겨줌)


}