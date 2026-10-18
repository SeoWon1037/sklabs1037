package com.sklabs1037.sklabs1037.user.service;

import com.sklabs1037.sklabs1037.user.vo.UserFindVO;

import java.util.List;

/**
 * 아이디 찾기 / 비밀번호 재설정 Service
 */
public interface IUserFindService {

    // 아이디 찾기: 닉네임 → 가린 이메일 + 가입 방식
    List<UserFindVO> findIds(String nickname);

    // 재설정 코드 발송 → "SENT" / "TOO_SOON"
    String sendResetCode(String email) throws Exception;

    // 코드 확인 → "OK" / "WRONG" / "EXPIRED" / "NONE"
    String verifyResetCode(String email, String code);

    // 새 비밀번호 저장
    boolean resetPassword(String email, String rawPassword);
}