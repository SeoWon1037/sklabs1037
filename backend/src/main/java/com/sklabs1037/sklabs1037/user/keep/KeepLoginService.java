package com.sklabs1037.sklabs1037.user.keep;

import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 🔑 로그인 유지
 *
 * 왜 필요한가
 *   - 로그인 상태(세션)는 서버 메모리에만 있음 → 배포로 서버가 다시 켜지면 모두 로그아웃됨, 30분 동안 아무것도 안 해도 로그아웃됨
 *   → 로그인할 때 기기에 "표"(쿠키 SKL_KEEP, 30일)를 하나 더 주고, 세션이 없으면 그 표로 다시 로그인 상태를 만들어 줌
 *
 * 흐름
 *   issue   : 로그인 · 소셜 로그인 성공 → 표 발급 (DB에는 SHA-256만)
 *   restore : 세션에 로그인 정보가 없고 표가 맞으면 → 회원을 다시 읽어 세션에 넣음 (닉네임 · 권한도 최신으로), 기한을 다시 30일로
 *   revoke  : 로그아웃 → 이 기기의 표만 지움 / revokeAll: 탈퇴 · 비밀번호 변경 → 모든 기기의 표를 지움
 *
 * 안전
 *   - 표는 무작위 32바이트, 쿠키는 HttpOnly · SameSite=Lax · (https면) Secure
 *   - 체험 계정 · AI 계정에는 주지 않음
 *   - 표가 아직 없는 DB(자동 SQL 실행 전)에서도 로그인은 그대로 됨 (실패해도 조용히 넘어감)
 */
@Slf4j
@Service
public class KeepLoginService {

    @Autowired
    private KeepLoginDao keepLoginDao;

    @Value("${app.frontend-url:}")
    private String frontendUrl;

    public void issue(Long userId, HttpServletResponse response) {

        if (userId == null) return;

        try {
            LocalDateTime now = LocalDateTime.now();
            String token = KeepLoginRules.newToken();
            keepLoginDao.deleteExpired(now);
            keepLoginDao.insert(KeepLoginRules.hash(token), userId, now.plusDays(KeepLoginRules.DAYS));
            response.addHeader("Set-Cookie", KeepLoginRules.cookie(token, KeepLoginRules.MAX_AGE_SECONDS, secure()));
        } catch (Exception e) {
            log.warn("로그인 유지 표 발급 실패 (로그인은 정상): {}", e.getMessage());
        }

    }

    /** 세션에 로그인 정보가 없을 때만: 표가 맞으면 세션을 다시 만들고 true */
    public boolean restore(HttpServletRequest request, HttpServletResponse response) {

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(UserLoginController.LOGIN_USER) != null) return false;

        String token = KeepLoginRules.tokenFrom(request.getHeader("Cookie"));
        if (token == null) return false;

        try {
            LocalDateTime now = LocalDateTime.now();
            String hash = KeepLoginRules.hash(token);
            UserLoginVO user = keepLoginDao.selectUser(hash, now);

            if (user == null) {
                // 기한이 지났거나 지워진 표 → 쿠키도 지움
                response.addHeader("Set-Cookie", KeepLoginRules.cookie(null, 0, secure()));
                return false;
            }

            user.setPassword(null);
            request.getSession(true).setAttribute(UserLoginController.LOGIN_USER, user);

            keepLoginDao.touch(hash, now.plusDays(KeepLoginRules.DAYS), now);
            response.addHeader("Set-Cookie", KeepLoginRules.cookie(token, KeepLoginRules.MAX_AGE_SECONDS, secure()));
            return true;

        } catch (Exception e) {
            log.warn("로그인 유지 확인 실패: {}", e.getMessage());
            return false;
        }

    }

    /** 로그아웃: 이 기기의 표만 */
    public void revoke(HttpServletRequest request, HttpServletResponse response) {

        try {
            String token = KeepLoginRules.tokenFrom(request.getHeader("Cookie"));
            if (token != null) keepLoginDao.delete(KeepLoginRules.hash(token));
        } catch (Exception e) {
            log.warn("로그인 유지 표 삭제 실패: {}", e.getMessage());
        }
        response.addHeader("Set-Cookie", KeepLoginRules.cookie(null, 0, secure()));

    }

    /** 탈퇴 · 비밀번호 변경: 모든 기기의 표 */
    public void revokeAll(Long userId) {

        try {
            if (userId != null) keepLoginDao.deleteByUser(userId);
        } catch (Exception e) {
            log.warn("로그인 유지 표 전체 삭제 실패: {}", e.getMessage());
        }

    }

    /** 비밀번호 찾기로 바꾼 경우 (로그인 전이라 이메일로) */
    public void revokeAllByEmail(String email) {

        try {
            if (email != null) keepLoginDao.deleteByEmail(email);
        } catch (Exception e) {
            log.warn("로그인 유지 표 전체 삭제 실패: {}", e.getMessage());
        }

    }

    // 운영(https)에서는 Secure 쿠키, 로컬(http://localhost)에서는 Secure를 붙이면 쿠키가 저장되지 않으므로 뺌
    private boolean secure() {

        return frontendUrl != null && frontendUrl.startsWith("https://");

    }
}
