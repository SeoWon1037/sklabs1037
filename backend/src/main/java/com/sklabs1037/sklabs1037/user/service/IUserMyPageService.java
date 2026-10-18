package com.sklabs1037.sklabs1037.user.service;

import com.sklabs1037.sklabs1037.user.vo.UserMyPageVO;

/**
 * 마이페이지 Service
 * - 검사에 걸리면 IllegalArgumentException(화면에 보여줄 메시지)을 던짐
 */
public interface IUserMyPageService {

    // 내 정보
    UserMyPageVO getMyInfo(Long id);

    // 닉네임 변경 → 바뀐 닉네임
    String changeNickname(Long id, String nickname);

    // 비밀번호 변경 (이메일 가입 회원만)
    void changePassword(Long id, String currentPassword, String newPassword);

    // 회원 탈퇴
    void withdraw(Long id, String currentPassword, String confirmText);
}