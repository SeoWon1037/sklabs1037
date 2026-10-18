package com.sklabs1037.sklabs1037.controller;

import com.sklabs1037.sklabs1037.service.IUserSignUpService;
import com.sklabs1037.sklabs1037.vo.UserSignUpVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 회원가입 화면 Controller
 * - 프론트(React)의 요청을 받아서 Service로 넘기고, 결과를 JSON으로 돌려줌
 * - @RestController: 화면(HTML)이 아니라 데이터(JSON)를 돌려주는 Controller
 *
 * API 목록
 *   GET  /api/user/signup/check-email?email=...   이메일 중복 체크
 *   POST /api/user/signup/send-code               인증번호 발송   { "email" }
 *   POST /api/user/signup/verify-code             인증번호 확인   { "email", "code" }
 *   POST /api/user/signup                         회원가입        { "email", "password", "nickname" }
 */

@Slf4j
@RestController
@RequestMapping("/api/user/signup")
public class UserSignUpController {

    @Autowired
    private IUserSignUpService userSignUpService;

    // 이메일 중복 체크
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestParam String email) throws Exception {

        UserSignUpVO vo = new UserSignUpVO();
        vo.setEmail(email);

        boolean duplicated = userSignUpService.isEmailDuplicated(vo);

        Map<String, Object> result = new HashMap<>();
        result.put("duplicated", duplicated);
        result.put("message", duplicated ? "이미 사용 중인 이메일입니다." : "사용 가능한 이메일입니다.");

        return ResponseEntity.ok(result);

    }

    // 인증번호 발송
    @PostMapping("/send-code")
    public ResponseEntity<Map<String, Object>> sendCode(@RequestBody UserSignUpVO vo) throws Exception {

        if (isBlank(vo.getEmail())) {

            return fail(HttpStatus.BAD_REQUEST, "이메일을 입력해주세요.");

        }

        try {

            userSignUpService.sendVerifyCode(vo);

            return success("인증번호를 발송했습니다. 메일함을 확인해주세요.");

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());          // 중복, 1분 제한 등

        } catch (MailException e) {

            // 원인을 콘솔에 남김 (사용자에게는 간단한 메시지만)
            log.error("인증 메일 발송 실패: {}", vo.getEmail(), e);

            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");

        }

    }

    // 인증번호 확인
    @PostMapping("/verify-code")
    public ResponseEntity<Map<String, Object>> verifyCode(@RequestBody UserSignUpVO vo) throws Exception {

        if (isBlank(vo.getEmail()) || isBlank(vo.getCode())) {

            return fail(HttpStatus.BAD_REQUEST, "이메일과 인증번호를 입력해주세요.");

        }

        try {

            userSignUpService.verifyCode(vo);

            return success("이메일 인증이 완료되었습니다.");

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());          // 불일치, 만료 등

        }

    }

    // 회원가입
    @PostMapping
    public ResponseEntity<Map<String, Object>> signUp(@RequestBody UserSignUpVO vo) throws Exception {

        // 1. 필수값 체크
        if (isBlank(vo.getEmail()) || isBlank(vo.getPassword()) || isBlank(vo.getNickname())) {

            return fail(HttpStatus.BAD_REQUEST, "이메일, 비밀번호, 닉네임을 모두 입력해주세요.");

        }

        // 2. 회원가입 처리
        try {

            userSignUpService.signUp(vo);

            return success("회원가입이 완료되었습니다.");

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());          // 중복, 인증 안 함 등

        }

    }

    // ============================================
    // 공통 응답 만들기
    // ============================================

    // 성공 응답 (200)
    private ResponseEntity<Map<String, Object>> success(String message) {

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", message);

        return ResponseEntity.ok(result);

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