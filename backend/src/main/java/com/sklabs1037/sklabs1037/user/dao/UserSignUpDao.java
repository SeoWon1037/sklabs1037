package com.sklabs1037.sklabs1037.user.dao;

import com.sklabs1037.sklabs1037.user.vo.UserSignUpVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 회원가입 화면 Dao
 * - @Mapper: MyBatis가 이 interface를 보고 실제 동작하는 코드를 자동으로 만들어줌
 * - 메서드 이름 = UserSignUpMapper.xml의 id
 */
@Mapper
public interface UserSignUpDao {

    // ===== 회원 (T_USER) =====

    // 이메일 중복 체크 (0이면 가입 가능)
    int countByEmail(UserSignUpVO vo);

    // 회원 등록
    void insertUser(UserSignUpVO vo);

    // ===== 이메일 인증 (T_EMAIL_VERIFY) =====

    // 최근 1분 안에 보낸 인증번호 개수 (재발송 제한)
    int countRecentSend(UserSignUpVO vo);

    // 인증번호 저장
    void insertEmailVerify(UserSignUpVO vo);

    // 가장 최근 인증번호 1건 조회
    UserSignUpVO selectLatestVerify(UserSignUpVO vo);

    // 인증 성공 처리
    void updateVerifySuccess(UserSignUpVO vo);

    // 가입 가능한 인증 개수 (인증 후 30분 이내)
    int countVerified(UserSignUpVO vo);

    // 가입 완료 후 인증 기록 사용 처리
    void deleteEmailVerify(UserSignUpVO vo);
}