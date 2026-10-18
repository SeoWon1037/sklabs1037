package com.sklabs1037.sklabs1037.user.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 마이페이지 화면에서 쓰는 값을 담는 VO
 * - 화면 1개 = VO 1개
 * - 사용 테이블: T_USER, T_WATCHLIST(탈퇴 시 삭제)
 */
@Getter
@Setter
public class UserMyPageVO {

    // ===== 회원 정보 (T_USER) =====
    private Long id;                   // 회원 번호
    private String email;              // 이메일
    private String password;           // DB의 암호화된 비밀번호 (화면에는 절대 안 보냄)
    private String nickname;           // 닉네임
    private String provider;           // LOCAL / KAKAO / NAVER
    private LocalDateTime insDay;      // 가입일

    // ===== 화면에서 받는 값 =====
    private String currentPassword;    // 현재 비밀번호 (비밀번호 변경, 탈퇴 시 확인용)
    private String newPassword;        // 새 비밀번호
    private String confirmText;        // 탈퇴 확인 문구
}