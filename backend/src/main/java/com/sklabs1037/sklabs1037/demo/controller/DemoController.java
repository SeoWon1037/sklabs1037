package com.sklabs1037.sklabs1037.demo.controller;

import com.sklabs1037.sklabs1037.demo.service.DemoService;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 🧪 로그인 없이 체험하기
 *   POST /api/user/demo   체험 계정으로 로그인 (세션에 DEMO = true)
 */
@RestController
public class DemoController {

    @Autowired
    private DemoService demoService;

    @PostMapping("/api/user/demo")
    public ResponseEntity<Map<String, Object>> demo(HttpServletRequest request) {

        Map<String, Object> body = new HashMap<>();

        try {

            UserLoginVO user = demoService.loginUser();

            // 일반 로그인과 같게: 쓰던 세션은 버리고 새 세션 (세션 고정 공격 방지)
            HttpSession old = request.getSession(false);
            if (old != null) old.invalidate();
            HttpSession session = request.getSession(true);
            session.setAttribute(UserLoginController.LOGIN_USER, user);
            session.setAttribute(DemoService.SESSION_KEY, true);

            Map<String, Object> info = new HashMap<>();
            info.put("id", user.getId());
            info.put("nickname", user.getNickname());
            info.put("role", user.getRole());
            info.put("demo", true);

            body.put("success", true);
            body.put("message", "체험 계정으로 들어왔어요. 모의투자·AI 비서·뷰티 랩을 자유롭게 써 보세요!");
            body.put("user", info);
            return ResponseEntity.ok(body);

        } catch (IllegalStateException e) {

            body.put("success", false);
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);

        }

    }
}
