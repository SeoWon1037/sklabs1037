package com.sklabs1037.sklabs1037.user.controller;

import com.sklabs1037.sklabs1037.user.service.IUserMyPageService;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import com.sklabs1037.sklabs1037.user.vo.UserMyPageVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 마이페이지 Controller
 *
 * API 목록
 *   GET  /api/user/mypage            내 정보                                  → { success, user: {...} }
 *   POST /api/user/mypage/nickname   닉네임 변경  { nickname }                → { success, nickname }
 *   POST /api/user/mypage/password   비밀번호 변경 { currentPassword, newPassword } → { success }
 *   POST /api/user/mypage/withdraw   회원 탈퇴    { currentPassword, confirmText }  → { success }
 *
 * - 전부 로그인한 사람만, 본인 정보만 (회원 번호는 화면에서 받지 않고 세션에서 꺼냄)
 */
@Slf4j
@RestController
@RequestMapping("/api/user/mypage")
public class UserMyPageController {

    @Autowired
    private IUserMyPageService userMyPageService;

    // 내 정보
    @GetMapping
    public ResponseEntity<Map<String, Object>> myInfo(HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            UserMyPageVO user = userMyPageService.getMyInfo(loginUser.getId());

            // 화면에 보낼 값만 (비밀번호 제외)
            Map<String, Object> info = new HashMap<>();
            info.put("email", user.getEmail());
            info.put("nickname", user.getNickname());
            info.put("provider", user.getProvider());
            info.put("insDay", user.getInsDay());

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("user", info);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.NOT_FOUND, e.getMessage());

        }

    }

    // 닉네임 변경
    @PostMapping("/nickname")
    public ResponseEntity<Map<String, Object>> changeNickname(@RequestBody UserMyPageVO vo,
                                                              HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            String nickname = userMyPageService.changeNickname(loginUser.getId(), vo.getNickname());

            // 세션에 들어 있는 로그인 정보도 바꿔야 상단바 닉네임이 바로 바뀜
            loginUser.setNickname(nickname);

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("nickname", nickname);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        }

    }

    // 비밀번호 변경
    @PostMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody UserMyPageVO vo,
                                                              HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            userMyPageService.changePassword(loginUser.getId(), vo.getCurrentPassword(), vo.getNewPassword());

            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        }

    }

    // 회원 탈퇴
    @PostMapping("/withdraw")
    public ResponseEntity<Map<String, Object>> withdraw(@RequestBody UserMyPageVO vo,
                                                        HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            userMyPageService.withdraw(loginUser.getId(), vo.getCurrentPassword(), vo.getConfirmText());

            // 탈퇴하면 바로 로그아웃
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();

            log.info("회원 탈퇴: {}", loginUser.getId());

            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        }

    }

    // ============================================
    // 공통
    // ============================================

    // 세션에 저장된 로그인 정보 (로그아웃 상태면 null)
    private UserLoginVO getLoginUser(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        return (session == null) ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);

    }

    private ResponseEntity<Map<String, Object>> ok() {

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);

        return ResponseEntity.ok(body);

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}