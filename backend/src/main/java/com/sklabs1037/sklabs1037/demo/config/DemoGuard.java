package com.sklabs1037.sklabs1037.demo.config;

import com.sklabs1037.sklabs1037.demo.service.DemoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;

/**
 * 🧪 체험 계정 지킴이 (모든 /api/** 요청 앞에서)
 * - 체험 세션(DEMO = true)이 허용 목록에 없는 쓰기 요청을 보내면 403 + 안내 문구
 * - 일반 회원 · 로그인 안 한 사람은 그대로 통과 (아무 영향 없음)
 */
@Configuration
public class DemoGuard implements WebMvcConfigurer, HandlerInterceptor {

    @org.springframework.beans.factory.annotation.Autowired
    private com.sklabs1037.sklabs1037.ops.service.UsageStatService usageStatService;

    private static final byte[] BLOCKED = "{\"success\":false,\"demo\":true,\"message\":\"체험 계정에서는 할 수 없어요. 회원가입하면 바로 쓸 수 있어요.\"}"
            .getBytes(StandardCharsets.UTF_8);

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(this).addPathPatterns("/api/**");

    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        HttpSession session = request.getSession(false);
        if (session == null || !Boolean.TRUE.equals(session.getAttribute(DemoService.SESSION_KEY))) return true;

        // 📊 체험하다가 회원가입을 끝까지 누름 (체험 → 가입 전환)
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/user/signup".equals(request.getRequestURI())) usageStatService.event("demo_signup");
        if (DemoService.allowed(request.getMethod(), request.getRequestURI())) return true;

        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(BLOCKED);
        return false;

    }
}
