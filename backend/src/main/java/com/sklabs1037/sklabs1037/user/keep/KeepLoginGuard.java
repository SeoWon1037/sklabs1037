package com.sklabs1037.sklabs1037.user.keep;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 🔑 로그인 유지: 모든 /api 요청이 컨트롤러에 닿기 전에, 세션이 비어 있고 표(쿠키)가 있으면 로그인 상태를 되살림
 * - 컨트롤러들은 지금처럼 세션의 LOGIN_USER만 보면 됨 (코드 수정 없음)
 * - 로그아웃 요청은 되살리지 않음 (어차피 지울 것이므로)
 */
@Configuration
public class KeepLoginGuard implements WebMvcConfigurer, HandlerInterceptor {

    @Autowired
    private KeepLoginService keepLoginService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(this).addPathPatterns("/api/**");

    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        if (!"/api/user/logout".equals(request.getRequestURI())) keepLoginService.restore(request, response);
        return true;

    }
}
