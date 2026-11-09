package com.sklabs1037.sklabs1037.ops.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🚦 요청 횟수 제한 (IP별, 서버 메모리)
 * - 로그인 비밀번호 대입 · 인증 메일 폭탄 · 체험 계정 남용 · 문의 도배 · 통계 부풀리기 · AI 비용 폭주를 앞에서 막음
 * - IP는 nginx가 넣어 주는 X-Real-IP (백엔드는 nginx 뒤에만 있어서 사용자가 바꿀 수 없음)
 * - 넘으면 429 + Retry-After (몇 초 뒤에 다시)
 * - 서버가 1대라 메모리로 충분 (여러 대가 되면 Redis로 옮길 자리)
 */
@Configuration
public class RateLimitGuard implements WebMvcConfigurer, HandlerInterceptor {

    // 주소 앞부분 · 허용 횟수 · 기간(초)  — 위에서부터 처음 맞는 규칙 하나만
    public record Rule(String prefix, int limit, int seconds) {
    }

    static final List<Rule> RULES = List.of(
            new Rule("/api/user/login", 10, 300),
            new Rule("/api/user/signup", 10, 600),
            new Rule("/api/user/find/", 10, 600),
            new Rule("/api/user/demo", 20, 600),
            new Rule("/api/support", 5, 600),
            new Rule("/api/ai/chat", 30, 600),
            new Rule("/api/track", 120, 60));

    private static final byte[] BODY = "{\"success\":false,\"message\":\"요청이 너무 많아요. 잠시 후 다시 시도해주세요.\"}".getBytes(StandardCharsets.UTF_8);

    private record Window(long start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(this).addPathPatterns("/api/**");

    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        Rule rule = ruleFor(request.getRequestURI());
        if (rule == null) return true;

        String ip = request.getHeader("X-Real-IP");
        long wait = hit(rule, ip == null || ip.isBlank() ? "local" : ip, System.currentTimeMillis());
        if (wait == 0) return true;

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(wait));
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(BODY);
        return false;

    }

    static Rule ruleFor(String uri) {

        if (uri == null) return null;
        for (Rule r : RULES) if (uri.startsWith(r.prefix())) return r;
        return null;

    }

    // 이번 요청을 세고 → 0 = 통과, 그 외 = 몇 초 뒤에 다시 (순수 계산에 가까움, 시각을 받아서 테스트 가능)
    long hit(Rule rule, String ip, long now) {

        if (windows.size() > 20_000) windows.entrySet().removeIf(e -> now - e.getValue().start() > 3_600_000);

        String key = rule.prefix() + "|" + ip;
        long period = rule.seconds() * 1000L;
        Window w = windows.compute(key, (k, old) ->
                old == null || now - old.start() >= period ? new Window(now, 1) : new Window(old.start(), old.count() + 1));

        if (w.count() <= rule.limit()) return 0;
        return Math.max(1, (w.start() + period - now + 999) / 1000);

    }
}
