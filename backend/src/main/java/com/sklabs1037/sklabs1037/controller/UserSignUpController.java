package com.sklabs1037.sklabs1037.controller;

import com.sklabs1037.sklabs1037.service.IUserSignUpService;
import com.sklabs1037.sklabs1037.vo.UserSignUpVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 회원가입 화면 Controller
 * - 프론트(React)의 요청을 받아서 Service로 넘기고, 결과를 JSON으로 돌려줌
 * - @RestController: 화면(HTML)이 아니라 데이터(JSON)를 돌려주는 Controller
 */
@RestController
@RequestMapping("/api/user/signup")
public class UserSignUpController {

    @Autowired
    private IUserSignUpService userSignUpService;

    // 이메일 중복 체크
    // GET /api/user/signup/check-email?email=test@test.com
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

    // 회원가입
    // POST /api/user/signup
    // Body(JSON): { "email": "...", "password": "...", "nickname": "..." }
    @PostMapping
    public ResponseEntity<Map<String, Object>> signUp(@RequestBody UserSignUpVO vo) throws Exception {

        Map<String, Object> result = new HashMap<>();

        // 1. 필수값 체크
        if (isBlank(vo.getEmail()) || isBlank(vo.getPassword()) || isBlank(vo.getNickname())) {

            result.put("success", false);
            result.put("message", "이메일, 비밀번호, 닉네임을 모두 입력해주세요.");

            return ResponseEntity.badRequest().body(result);   // 400

        }

        // 2. 회원가입 처리
        try {

            userSignUpService.signUp(vo);

            result.put("success", true);
            result.put("message", "회원가입이 완료되었습니다.");

            return ResponseEntity.ok(result);   // 200

        } catch (IllegalArgumentException e) {

            // 이메일 중복 등 Service에서 막은 경우
            result.put("success", false);
            result.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);   // 409

        }

    }

    // 빈 값 체크 (null 이거나 공백만 있으면 true)
    private boolean isBlank(String value) {

        return value == null || value.trim().isEmpty();

    }
}