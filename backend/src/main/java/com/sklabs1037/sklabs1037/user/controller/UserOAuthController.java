package com.sklabs1037.sklabs1037.controller;

import com.sklabs1037.sklabs1037.service.IUserOAuthService;
import com.sklabs1037.sklabs1037.vo.UserLoginVO;
import com.sklabs1037.sklabs1037.vo.UserOAuthVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.net.URI;
import java.security.SecureRandom;

/**
 * 소셜 로그인 Controller (네이버, 카카오)
 * - JSON이 아니라 "다른 주소로 이동(redirect)"을 돌려주는 Controller
 * - 로그인 결과는 항상 프론트의 /oauth/result?status=... 로 보냄
 *   (팝업이면 그 페이지가 원래 창에 결과를 알려주고 스스로 닫힘)
 *
 * status 값
 *   success        로그인 성공
 *   cancel         사용자가 동의 화면에서 취소
 *   invalid        위조된 요청 (state 불일치)
 *   email_exists   같은 이메일로 이미 다른 방법으로 가입됨
 *   email_missing  이메일 제공 동의 안 함 / 인증 안 된 이메일 (카카오)
 *   fail           그 외 실패 (통신 오류 등)
 *
 * API 목록
 *   GET /api/oauth/naver/login      네이버 로그인 화면으로 이동
 *   GET /api/oauth/naver/callback   네이버 로그인 후 돌아오는 곳
 *   GET /api/oauth/kakao/login      카카오 로그인 화면으로 이동
 *   GET /api/oauth/kakao/callback   카카오 로그인 후 돌아오는 곳
 */
@Slf4j
@RestController
@RequestMapping("/api/oauth")
public class UserOAuthController {

    // 위조 방지용 랜덤값(state)을 세션에 저장할 때 쓰는 이름 (네이버/카카오 따로)
    private static final String NAVER_STATE = "NAVER_OAUTH_STATE";
    private static final String KAKAO_STATE = "KAKAO_OAUTH_STATE";

    @Autowired
    private IUserOAuthService userOAuthService;

    // 로그인 끝나고 돌려보낼 프론트 주소 (개발: http://localhost:5173)
    @Value("${app.frontend-url}")
    private String frontendUrl;

    // 예측 불가능한 랜덤값 생성기
    private final SecureRandom random = new SecureRandom();

    // "로그인 처리" 동작을 넘겨받기 위한 모양 (네이버/카카오가 각자 다른 처리를 넣음)
    @FunctionalInterface
    private interface OAuthLogin {
        UserOAuthVO login() throws Exception;
    }

    // ============================================
    // 네이버
    // ============================================

    // 네이버 로그인 화면으로 보내기
    @GetMapping("/naver/login")
    public ResponseEntity<Void> naverLogin(HttpServletRequest request) {

        String state = createState(request, NAVER_STATE);

        return redirect(userOAuthService.getNaverLoginUrl(state));

    }

    // 네이버 로그인 후 돌아오는 곳
    @GetMapping("/naver/callback")
    public ResponseEntity<Void> naverCallback(@RequestParam(required = false) String code,
                                              @RequestParam(required = false) String state,
                                              @RequestParam(required = false) String error,
                                              HttpServletRequest request) {

        return handleCallback(request, NAVER_STATE, code, state, error,
                () -> userOAuthService.loginWithNaver(code, state));

    }

    // ============================================
    // 카카오
    // ============================================

    // 카카오 로그인 화면으로 보내기
    @GetMapping("/kakao/login")
    public ResponseEntity<Void> kakaoLogin(HttpServletRequest request) {

        String state = createState(request, KAKAO_STATE);

        return redirect(userOAuthService.getKakaoLoginUrl(state));

    }

    // 카카오 로그인 후 돌아오는 곳
    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(@RequestParam(required = false) String code,
                                              @RequestParam(required = false) String state,
                                              @RequestParam(required = false) String error,
                                              HttpServletRequest request) {

        return handleCallback(request, KAKAO_STATE, code, state, error,
                () -> userOAuthService.loginWithKakao(code));

    }

    // ============================================
    // 공통
    // ============================================

    // 위조 방지용 랜덤값 만들어서 세션에 저장 (돌아왔을 때 같은 값인지 확인용)
    private String createState(HttpServletRequest request, String stateKey) {

        String state = new BigInteger(130, random).toString(32);
        request.getSession(true).setAttribute(stateKey, state);

        return state;

    }

    // 돌아온 요청 처리 (네이버/카카오 공통 흐름)
    private ResponseEntity<Void> handleCallback(HttpServletRequest request,
                                                String stateKey,
                                                String code,
                                                String state,
                                                String error,
                                                OAuthLogin loginAction) {

        // 1. 사용자가 동의 화면에서 취소한 경우
        if (error != null || code == null) {

            return result("cancel");

        }

        // 2. state 확인 (아까 저장한 값과 다르면 위조된 요청)
        HttpSession oldSession = request.getSession(false);
        String savedState = (oldSession == null) ? null : (String) oldSession.getAttribute(stateKey);

        if (savedState == null || !savedState.equals(state)) {

            return result("invalid");

        }

        try {

            // 3. 네이버/카카오에서 회원 정보 받아서 → 기존 회원 / 자동 가입
            UserOAuthVO user = loginAction.login();

            // 4. 세션 고정 공격 방지: 기존 세션 버리고 새로 만들기
            oldSession.invalidate();
            HttpSession session = request.getSession(true);

            // 5. 일반 로그인과 "같은 이름(LOGIN_USER), 같은 모양(UserLoginVO)"으로 저장
            //    → 상단바의 /api/user/me 가 코드 수정 없이 소셜 회원도 알아봄
            session.setAttribute(UserLoginController.LOGIN_USER, toLoginUser(user));

            return result("success");

        } catch (IllegalArgumentException e) {

            // email_exists, email_missing
            return result(e.getMessage());

        } catch (Exception e) {

            log.error("소셜 로그인 실패 ({})", stateKey, e);
            return result("fail");

        }

    }

    // 프론트 결과 페이지로 이동 (/oauth/result?status=...)
    private ResponseEntity<Void> result(String status) {

        return redirect(frontendUrl + "/oauth/result?status=" + status);

    }

    // 다른 주소로 이동시키는 응답 (HTTP 302)
    private ResponseEntity<Void> redirect(String url) {

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();

    }

    // 세션에 넣을 "로그인 회원" 모양으로 바꾸기 (일반 로그인과 같은 UserLoginVO)
    private UserLoginVO toLoginUser(UserOAuthVO user) {

        UserLoginVO loginUser = new UserLoginVO();
        loginUser.setId(user.getId());
        loginUser.setEmail(user.getEmail());
        loginUser.setNickname(user.getNickname());
        loginUser.setRole(user.getRole());

        return loginUser;

    }
}