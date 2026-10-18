package com.sklabs1037.sklabs1037.user.service;

import com.sklabs1037.sklabs1037.user.dao.UserLoginDao;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 화면 Service 구현
 * - 이메일로 회원을 찾고, 비밀번호가 맞는지 확인하는 곳
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class UserLoginServiceImpl implements IUserLoginService {

    // 실패 메시지는 하나로 통일
    // (이메일이 없는지 / 비밀번호가 틀렸는지 구분해서 알려주면, 가입된 이메일인지 알아낼 수 있어서)
    private static final String LOGIN_FAIL_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

    @Autowired
    private UserLoginDao userLoginDao;

    // 비밀번호 비교 도구 (회원가입 때와 같은 BCrypt)
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 로그인
    @Override
    public UserLoginVO login(UserLoginVO vo) throws Exception {

        // 1. 이메일로 회원 조회
        UserLoginVO user = userLoginDao.selectUserByEmail(vo);

        // 2. 회원이 없으면 실패
        if (user == null) {

            throw new IllegalArgumentException(LOGIN_FAIL_MESSAGE);

        }

        // 3. 비밀번호 비교
        //    matches(입력한 원래 비밀번호, DB의 암호화된 비밀번호)
        //    → BCrypt는 암호화 결과가 매번 달라서 "==" 비교가 아니라 matches()로 비교해야 함
        if (!passwordEncoder.matches(vo.getPassword(), user.getPassword())) {

            throw new IllegalArgumentException(LOGIN_FAIL_MESSAGE);

        }

        // 4. 세션에 저장하기 전에 비밀번호는 지움 (필요 없는 민감 정보는 들고 다니지 않기)
        user.setPassword(null);

        return user;

    }
}