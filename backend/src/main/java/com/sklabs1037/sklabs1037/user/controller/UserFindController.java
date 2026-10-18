package com.sklabs1037.sklabs1037.controller;

import com.sklabs1037.sklabs1037.service.IUserFindService;
import com.sklabs1037.sklabs1037.vo.UserFindVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 아이디 찾기 / 비밀번호 재설정 Controller
 *
 * API 목록
 *   GET  /api/user/find/id?nickname=      아이디 찾기 → { success, users: [{ maskedEmail, provider, insDay }] }
 *   POST /api/user/find/reset/send-code   { email }        인증코드 발송
 *   POST /api/user/find/reset/verify      { email, code }  코드 확인
 *   POST /api/user/find/reset/password    { password }     새 비밀번호 (코드 확인한 세션에서만)
 *
 * 세션에 저장하는 값 (코드 확인 결과를 브라우저가 아니라 서버가 기억)
 *   RESET_EMAIL       코드 확인이 끝난 이메일
 *   RESET_VERIFIED_AT 확인한 시각 (10분 안에만 비밀번호 변경 가능)
 *   RESET_FAIL        코드 틀린 횟수 (5번 틀리면 다시 발송해야 함)
 */
@Slf4j
@RestController
@RequestMapping("/api/user/find")
public class UserFindController {

    private static final String RESET_EMAIL = "RESET_EMAIL";
    private static final String RESET_VERIFIED_AT = "RESET_VERIFIED_AT";
    private static final String RESET_FAIL = "RESET_FAIL";

    private static final int MAX_FAIL = 5;
    private static final long RESET_LIMIT_MS = 10 * 60 * 1000;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    // 비밀번호: 8~20자, 영문 + 숫자 포함
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,20}$");

    @Autowired
    private IUserFindService userFindService;

    // ============================================
    // 아이디 찾기
    // ============================================
    @GetMapping("/id")
    public ResponseEntity<Map<String, Object>> findId(@RequestParam(required = false) String nickname) {

        String name = (nickname == null) ? "" : nickname.trim();
        if (name.isEmpty() || name.length() > 50) return fail(HttpStatus.BAD_REQUEST, "닉네임을 입력해주세요.");

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("users", userFindService.findIds(name));

        return ResponseEntity.ok(body);

    }

    // ============================================
    // 비밀번호 재설정 ① 코드 발송
    // ============================================
    @PostMapping("/reset/send-code")
    public ResponseEntity<Map<String, Object>> sendCode(@RequestBody UserFindVO vo, HttpServletRequest request) {

        String email = normalize(vo.getEmail());
        if (!EMAIL_PATTERN.matcher(email).matches()) return fail(HttpStatus.BAD_REQUEST, "이메일 형식을 확인해주세요.");

        try {

            if ("TOO_SOON".equals(userFindService.sendResetCode(email))) {
                return fail(HttpStatus.TOO_MANY_REQUESTS, "1분 후에 다시 요청해주세요.");
            }

            // 새로 발송했으니 틀린 횟수, 이전 확인 기록 초기화
            HttpSession session = request.getSession();
            session.setAttribute(RESET_FAIL, 0);
            session.removeAttribute(RESET_EMAIL);
            session.removeAttribute(RESET_VERIFIED_AT);

            return ok("가입된 이메일이라면 인증코드를 보냈습니다. 메일함을 확인해주세요.");

        } catch (MailException e) {

            log.error("비밀번호 재설정 메일 발송 실패", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");

        } catch (Exception e) {

            log.error("비밀번호 재설정 코드 발송 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "처리 중 오류가 발생했습니다.");

        }

    }

    // ============================================
    // 비밀번호 재설정 ② 코드 확인
    // ============================================
    @PostMapping("/reset/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody UserFindVO vo, HttpServletRequest request) {

        HttpSession session = request.getSession();

        int failCount = (session.getAttribute(RESET_FAIL) instanceof Integer n) ? n : 0;
        if (failCount >= MAX_FAIL) return fail(HttpStatus.TOO_MANY_REQUESTS, "인증코드를 5번 틀렸습니다. 코드를 다시 받아주세요.");

        String email = normalize(vo.getEmail());
        String code = (vo.getCode() == null) ? "" : vo.getCode().trim();

        switch (userFindService.verifyResetCode(email, code)) {

            case "OK":
                session.setAttribute(RESET_EMAIL, email);
                session.setAttribute(RESET_VERIFIED_AT, System.currentTimeMillis());
                session.removeAttribute(RESET_FAIL);
                return ok("인증되었습니다. 새 비밀번호를 입력해주세요.");

            case "EXPIRED":
                return fail(HttpStatus.BAD_REQUEST, "인증코드가 만료되었습니다. 다시 받아주세요.");

            case "WRONG":
                session.setAttribute(RESET_FAIL, failCount + 1);
                return fail(HttpStatus.BAD_REQUEST, "인증코드가 일치하지 않습니다. (" + (failCount + 1) + "/" + MAX_FAIL + ")");

            default:
                return fail(HttpStatus.BAD_REQUEST, "인증코드를 먼저 받아주세요.");

        }

    }

    // ============================================
    // 비밀번호 재설정 ③ 새 비밀번호
    // ============================================
    @PostMapping("/reset/password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody UserFindVO vo, HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        String email = (session == null) ? null : (String) session.getAttribute(RESET_EMAIL);
        Object verifiedAt = (session == null) ? null : session.getAttribute(RESET_VERIFIED_AT);

        if (email == null || !(verifiedAt instanceof Long at) || System.currentTimeMillis() - at > RESET_LIMIT_MS) {
            return fail(HttpStatus.UNAUTHORIZED, "인증 시간이 지났습니다. 처음부터 다시 진행해주세요.");
        }

        String password = (vo.getPassword() == null) ? "" : vo.getPassword();
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            return fail(HttpStatus.BAD_REQUEST, "비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요.");
        }

        if (!userFindService.resetPassword(email, password)) {
            return fail(HttpStatus.BAD_REQUEST, "비밀번호를 변경할 수 없는 계정입니다.");
        }

        // 한 번 쓴 인증은 끝
        session.removeAttribute(RESET_EMAIL);
        session.removeAttribute(RESET_VERIFIED_AT);

        return ok("비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.");

    }

    // ============================================
    // 공통
    // ============================================

    private String normalize(String email) {

        return (email == null) ? "" : email.trim().toLowerCase();

    }

    private ResponseEntity<Map<String, Object>> ok(String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", message);

        return ResponseEntity.ok(body);

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}