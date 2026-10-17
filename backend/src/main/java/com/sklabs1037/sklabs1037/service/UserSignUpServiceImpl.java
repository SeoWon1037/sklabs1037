package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.dao.UserSignUpDao;
import com.sklabs1037.sklabs1037.vo.UserSignUpVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 화면 Service 구현
 * - 실제 업무 규칙(중복 체크, 암호화)을 처리하는 곳
 */
@Service
@Transactional(rollbackFor = Exception.class)   // 회사와 동일: 에러 나면 DB 작업 전부 되돌림
public class UserSignUpServiceImpl implements IUserSignUpService {

    @Autowired
    private UserSignUpDao userSignUpDao;

    // 비밀번호 암호화 도구 (BCrypt)
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 이메일 중복 체크
    @Override
    public boolean isEmailDuplicated(UserSignUpVO vo) throws Exception {

        return userSignUpDao.countByEmail(vo) > 0;

    }

    // 회원가입
    @Override
    public void signUp(UserSignUpVO vo) throws Exception {

        // 1. 이메일 중복이면 가입 막기
        if (isEmailDuplicated(vo)) {

            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");

        }

        // 2. 비밀번호 암호화 (1234 → $2a$10$Xk... 같은 값으로 바뀜)
        vo.setPassword(passwordEncoder.encode(vo.getPassword()));

        // 3. DB 저장
        userSignUpDao.insertUser(vo);

    }
}