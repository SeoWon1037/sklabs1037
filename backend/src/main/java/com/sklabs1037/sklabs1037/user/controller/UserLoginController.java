package com.sklabs1037.sklabs1037.user.controller;

import com.sklabs1037.sklabs1037.user.service.IUserLoginService;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 로그인 화면 Controller
 * - 로그인 성공 시 회원 정보를 "세션"에 저장 (서버 메모리)
 * - 브라우저는 번호표 쿠키(JSESSIONID)만 갖고 다니고, 요청마다 자동으로 보냄
 *
 * API 목록
 *   POST /api/user/login     로그인        { "email", "password" }
 *   GET  /api/user/me        내 정보 (로그인 상태 확인)
 *   POST /api/user/logout    로그아웃
 */
@RestController
@RequestMapping("/api/user")
public class UserLoginController {

    // 세션에 회원 정보를 저장할 때 쓰는 이름 (꺼낼 때도 같은 이름으로)
    public static final String LOGIN_USER = "LOGIN_USER";

    @Autowired
    private IUserLoginService userLoginService;

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody UserLoginVO vo,
                                                     HttpServletRequest request) throws Exception {

        // 1. 필수값 체크
        if (isBlank(vo.getEmail()) || isBlank(vo.getPassword())) {

            return fail(HttpStatus.BAD_REQUEST, "이메일과 비밀번호를 입력해주세요.");

        }

        try {

            // 2. 이메일 + 비밀번호 확인
            UserLoginVO user = userLoginService.login(vo);

            // 3. 세션 고정 공격 방지: 로그인 전에 쓰던 세션이 있으면 버리고
            HttpSession oldSession = request.getSession(false);   // false = 없으면 새로 만들지 말고 null
            if (oldSession != null) {

                oldSession.invalidate();

            }

            // 4. 새 세션을 만들어서 회원 정보 저장 → 브라우저에 새 번호표(JSESSIONID) 발급
            HttpSession session = request.getSession(true);       // true = 새로 만들기
            session.setAttribute(LOGIN_USER, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", user.getNickname() + "님, 환영합니다!");
            result.put("user", toUserInfo(user));

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.UNAUTHORIZED, e.getMessage());   // 401 = 인증 실패

        }

    }

    // 내 정보 (로그인 상태 확인)
    // - 상단바가 화면 뜰 때마다 불러서 "로그인 / 서원님" 중 뭘 보여줄지 결정
    // - 로그인 안 했어도 에러가 아니라 loggedIn: false 로 정상 응답
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {

        Map<String, Object> result = new HashMap<>();

        HttpSession session = request.getSession(false);
        UserLoginVO user = (session == null) ? null : (UserLoginVO) session.getAttribute(LOGIN_USER);

        if (user == null) {

            result.put("loggedIn", false);
            return ResponseEntity.ok(result);

        }

        result.put("loggedIn", true);
        result.put("user", toUserInfo(user));

        return ResponseEntity.ok(result);

    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {

        // 세션을 통째로 삭제 → 번호표가 무효가 됨
        HttpSession session = request.getSession(false);
        if (session != null) {

            session.invalidate();

        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "로그아웃되었습니다.");

        return ResponseEntity.ok(result);

    }

    // ============================================
    // 공통
    // ============================================

    // 화면에 보내줄 회원 정보만 골라 담기 (비밀번호 같은 건 절대 안 보냄)
    private Map<String, Object> toUserInfo(UserLoginVO user) {

        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("email", user.getEmail());
        info.put("nickname", user.getNickname());
        info.put("role", user.getRole());

        return info;

    }

    // 실패 응답 (상태코드 + 메시지)
    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);

        return ResponseEntity.status(status).body(result);

    }

    // 빈 값 체크 (null 이거나 공백만 있으면 true)
    private boolean isBlank(String value) {

        return value == null || value.trim().isEmpty();

    }
}