package com.sklabs1037.sklabs1037.user.service;

import com.sklabs1037.sklabs1037.user.dao.UserMyPageDao;
import com.sklabs1037.sklabs1037.user.vo.UserMyPageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 마이페이지 Service 구현
 *
 * - 비밀번호는 회원가입과 같은 BCrypt로 확인/저장
 * - 소셜 로그인(카카오/네이버) 회원은 비밀번호가 없으므로 비밀번호 변경 불가, 탈퇴는 확인 문구만
 * - 탈퇴는 "회원 정보 지우기 + 관심 종목 삭제"를 한 트랜잭션으로 (둘 중 하나만 되는 일 없게)
 */
@Service
public class UserMyPageServiceImpl implements IUserMyPageService {

    private static final String LOCAL = "LOCAL";

    // 탈퇴할 때 입력해야 하는 문구
    public static final String WITHDRAW_TEXT = "탈퇴합니다";

    // 닉네임 길이 (T_USER.NICKNAME 은 50자지만 화면에 보기 좋게 제한)
    private static final int NICKNAME_MIN = 2;
    private static final int NICKNAME_MAX = 20;

    // 비밀번호 규칙: 영문 + 숫자 포함 8~20자 (아이디/비밀번호 찾기와 같은 규칙)
    private static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,20}$";

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private UserMyPageDao userMyPageDao;

    @Override
    public UserMyPageVO getMyInfo(Long id) {

        UserMyPageVO user = userMyPageDao.selectMyInfo(id);

        if (user == null) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }

        return user;

    }

    @Override
    public String changeNickname(Long id, String nickname) {

        String value = (nickname == null) ? "" : nickname.trim();

        if (value.length() < NICKNAME_MIN || value.length() > NICKNAME_MAX) {
            throw new IllegalArgumentException("닉네임은 " + NICKNAME_MIN + "~" + NICKNAME_MAX + "자로 입력해주세요.");
        }

        UserMyPageVO vo = new UserMyPageVO();
        vo.setId(id);
        vo.setNickname(value);

        if (userMyPageDao.updateNickname(vo) == 0) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }

        return value;

    }

    @Override
    public void changePassword(Long id, String currentPassword, String newPassword) {

        UserMyPageVO user = getMyInfo(id);

        // 1. 소셜 로그인 회원은 비밀번호가 없음
        if (!LOCAL.equals(user.getProvider())) {
            throw new IllegalArgumentException("소셜 로그인 회원은 비밀번호를 변경할 수 없습니다.");
        }

        // 2. 현재 비밀번호 확인
        if (isBlank(currentPassword) || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 3. 새 비밀번호 규칙
        if (newPassword == null || !newPassword.matches(PASSWORD_RULE)) {
            throw new IllegalArgumentException("새 비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요.");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호와 다른 비밀번호를 입력해주세요.");
        }

        // 4. 암호화해서 저장
        UserMyPageVO vo = new UserMyPageVO();
        vo.setId(id);
        vo.setPassword(passwordEncoder.encode(newPassword));

        userMyPageDao.updatePassword(vo);

    }

    @Override
    @Transactional
    public void withdraw(Long id, String currentPassword, String confirmText) {

        UserMyPageVO user = getMyInfo(id);

        // 1. 확인 문구
        if (!WITHDRAW_TEXT.equals(confirmText == null ? "" : confirmText.trim())) {
            throw new IllegalArgumentException("확인 문구 \"" + WITHDRAW_TEXT + "\"를 정확히 입력해주세요.");
        }

        // 2. 이메일 가입 회원은 비밀번호도 확인
        if (LOCAL.equals(user.getProvider())
                && (isBlank(currentPassword) || !passwordEncoder.matches(currentPassword, user.getPassword()))) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 3. 관심 종목 삭제 → 회원 정보 지우기 (둘 다 성공해야 반영, 중간에 실패하면 전부 되돌림)
        userMyPageDao.deleteWatchlist(id);
        userMyPageDao.withdrawUser(id);

    }

    @Override
    public List<UserMyPageVO> getMyChats(Long id, int page, int size) {

        UserMyPageVO vo = new UserMyPageVO();
        vo.setId(id);
        vo.setLimit(size);
        vo.setOffset((Math.max(page, 1) - 1) * size);

        return userMyPageDao.selectMyChats(vo);

    }

    @Override
    public int countMyChats(Long id) {

        return userMyPageDao.countMyChats(id);

    }

    private boolean isBlank(String value) {

        return value == null || value.trim().isEmpty();

    }
}