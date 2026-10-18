package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.dao.UserFindDao;
import com.sklabs1037.sklabs1037.vo.UserFindVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * 아이디 찾기 / 비밀번호 재설정 Service 구현
 *
 * 보안
 *   - 가입되지 않은 이메일도 "보냈습니다"로 똑같이 응답 → 가입 여부를 떠볼 수 없음
 *   - 소셜 가입 계정은 비밀번호가 없으므로 코드 대신 "OO 로그인으로 가입된 계정" 안내 메일
 *   - 코드: 6자리, 5분 만료, 1분 내 재발송 금지
 */
@Slf4j
@Service
public class UserFindServiceImpl implements IUserFindService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<String, String> PROVIDER_NAMES = Map.of(
            "LOCAL", "이메일",
            "NAVER", "네이버",
            "KAKAO", "카카오"
    );

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private UserFindDao userFindDao;

    @Autowired
    private JavaMailSender mailSender;

    // 보내는 사람 = 로그인한 네이버 메일 주소
    @Value("${spring.mail.username}")
    private String mailFrom;

    // ============================================
    // 아이디 찾기
    // ============================================
    @Override
    public List<UserFindVO> findIds(String nickname) {

        UserFindVO param = new UserFindVO();
        param.setNickname(nickname);

        List<UserFindVO> users = userFindDao.selectUsersByNickname(param);

        for (UserFindVO u : users) {
            u.setMaskedEmail(maskEmail(u.getEmail()));
            u.setEmail(null);   // 원래 이메일은 화면으로 보내지 않음
        }

        return users;

    }

    // ============================================
    // 재설정 코드 발송
    // ============================================
    @Override
    public String sendResetCode(String email) throws Exception {

        UserFindVO param = new UserFindVO();
        param.setEmail(email);

        if (userFindDao.countRecentSend(param) > 0) return "TOO_SOON";

        UserFindVO user = userFindDao.selectUserByEmail(param);

        // 가입되지 않은 이메일: 아무것도 보내지 않지만 응답은 똑같이
        if (user == null) {
            log.info("비밀번호 재설정 요청: 가입되지 않은 이메일");
            return "SENT";
        }

        // 소셜 가입 계정: 안내 메일만
        if (!"LOCAL".equals(user.getProvider())) {
            String providerName = PROVIDER_NAMES.getOrDefault(user.getProvider(), user.getProvider());
            sendMail(email, "[SKLabs] 비밀번호 재설정 안내",
                    "이 이메일은 " + providerName + " 로그인으로 가입된 계정입니다.\n"
                            + "SKLabs 비밀번호가 따로 없으니, 로그인 화면에서 " + providerName + " 로그인 버튼을 이용해주세요.");
            return "SENT";
        }

        // 이메일 가입 계정: 인증코드
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        param.setCode(code);
        userFindDao.insertResetCode(param);

        sendMail(email, "[SKLabs] 비밀번호 재설정 인증코드",
                "비밀번호 재설정 인증코드: " + code + "\n\n"
                        + "5분 안에 입력해주세요.\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.");

        return "SENT";

    }

    // ============================================
    // 코드 확인
    // ============================================
    @Override
    public String verifyResetCode(String email, String code) {

        UserFindVO param = new UserFindVO();
        param.setEmail(email);

        UserFindVO latest = userFindDao.selectLatestResetCode(param);

        if (latest == null) return "NONE";
        if ("Y".equals(latest.getExpiredYn())) return "EXPIRED";
        if (!latest.getCode().equals(code)) return "WRONG";

        userFindDao.updateVerifySuccess(latest);

        return "OK";

    }

    // ============================================
    // 새 비밀번호 저장
    // ============================================
    @Override
    public boolean resetPassword(String email, String rawPassword) {

        UserFindVO param = new UserFindVO();
        param.setEmail(email);

        UserFindVO user = userFindDao.selectUserByEmail(param);
        if (user == null) return false;

        user.setPassword(passwordEncoder.encode(rawPassword));

        return userFindDao.updatePassword(user) > 0;

    }

    // ============================================
    // 공통
    // ============================================

    // 메일 발송
    private void sendMail(String to, String subject, String text) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        mailSender.send(message);

    }

    // 이메일 가리기: aptl1471@naver.com → ap******@naver.com
    private String maskEmail(String email) {

        if (email == null || !email.contains("@")) return "-";

        String id = email.substring(0, email.indexOf('@'));
        String domain = email.substring(email.indexOf('@'));
        int show = Math.min(2, id.length());

        return id.substring(0, show) + "*".repeat(Math.max(id.length() - show, 2)) + domain;

    }
}