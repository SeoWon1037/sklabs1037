package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.dao.UserSignUpDao;
import com.sklabs1037.sklabs1037.vo.UserSignUpVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * 회원가입 화면 Service 구현
 * - 실제 업무 규칙(중복 체크, 이메일 인증, 암호화)을 처리하는 곳
 * - 사용자가 고칠 수 있는 문제(중복, 인증번호 틀림 등)는 IllegalArgumentException으로 알림
 */
@Service
@Transactional(rollbackFor = Exception.class)   // 에러 나면 DB 작업 전부 되돌림 (메일 발송 실패 시 인증번호 저장도 취소)
public class UserSignUpServiceImpl implements IUserSignUpService {

    @Autowired
    private UserSignUpDao userSignUpDao;

    // 메일 발송 도구 (application.properties의 spring.mail 설정으로 자동 생성됨)
    @Autowired
    private JavaMailSender mailSender;

    // 보내는 사람 주소 (네이버는 로그인 계정과 같아야 발송됨)
    @Value("${spring.mail.username:}")
    private String mailFrom;

    // 비밀번호 암호화 도구 (BCrypt)
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 인증번호 생성기 (일반 Random보다 예측하기 어려운 보안용 난수)
    private final SecureRandom random = new SecureRandom();

    // 이메일 중복 체크
    @Override
    public boolean isEmailDuplicated(UserSignUpVO vo) throws Exception {

        return userSignUpDao.countByEmail(vo) > 0;

    }

    // 인증번호 메일 발송
    @Override
    public void sendVerifyCode(UserSignUpVO vo) throws Exception {

        // 1. 이미 가입된 이메일이면 발송 안 함
        if (isEmailDuplicated(vo)) {

            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");

        }

        // 2. 1분 안에 다시 요청하면 막기 (메일 폭탄 방지)
        if (userSignUpDao.countRecentSend(vo) > 0) {

            throw new IllegalArgumentException("인증번호는 1분에 한 번만 받을 수 있습니다.");

        }

        // 3. 6자리 인증번호 생성 (000000 ~ 999999, 앞자리 0도 유지)
        String code = String.format("%06d", random.nextInt(1_000_000));
        vo.setCode(code);

        // 4. DB 저장 (만료 시각은 SQL에서 NOW() + 5분)
        userSignUpDao.insertEmailVerify(vo);

        // 5. 메일 발송
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(vo.getEmail());
        message.setSubject("[SKLabs] 회원가입 인증번호 안내");
        message.setText(
                "안녕하세요, SKLabs입니다.\n\n"
                        + "회원가입 인증번호는 [" + code + "] 입니다.\n"
                        + "5분 안에 입력해주세요.\n\n"
                        + "본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다."
        );
        mailSender.send(message);

    }

    // 인증번호 확인
    @Override
    public void verifyCode(UserSignUpVO vo) throws Exception {

        // 1. 가장 최근에 보낸 인증번호 조회
        UserSignUpVO latest = userSignUpDao.selectLatestVerify(vo);

        if (latest == null) {

            throw new IllegalArgumentException("인증번호를 먼저 받아주세요.");

        }

        // 2. 이미 인증 완료된 경우 → 그대로 성공 처리
        if ("Y".equals(latest.getVerifyYn())) {

            return;

        }

        // 3. 5분 지났으면 만료
        if ("Y".equals(latest.getExpiredYn())) {

            throw new IllegalArgumentException("인증번호가 만료되었습니다. 다시 받아주세요.");

        }

        // 4. 번호 비교
        if (!latest.getCode().equals(vo.getCode())) {

            throw new IllegalArgumentException("인증번호가 일치하지 않습니다.");

        }

        // 5. 인증 성공 처리
        userSignUpDao.updateVerifySuccess(latest);

    }

    // 회원가입
    @Override
    public void signUp(UserSignUpVO vo) throws Exception {

        // 1. 이메일 중복이면 가입 막기
        if (isEmailDuplicated(vo)) {

            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");

        }

        // 2. 이메일 인증을 안 했으면 가입 막기
        //    (화면에서만 막으면 API를 직접 호출해서 우회할 수 있으니 서버에서도 검사)
        if (userSignUpDao.countVerified(vo) == 0) {

            throw new IllegalArgumentException("이메일 인증을 완료해주세요.");

        }

        // 3. 비밀번호 암호화 (1234 → $2a$10$Xk... 같은 값으로 바뀜)
        vo.setPassword(passwordEncoder.encode(vo.getPassword()));

        // 4. DB 저장
        userSignUpDao.insertUser(vo);

        // 5. 사용한 인증 기록 정리 (같은 인증으로 다시 가입 못 하게)
        userSignUpDao.deleteEmailVerify(vo);

    }
}