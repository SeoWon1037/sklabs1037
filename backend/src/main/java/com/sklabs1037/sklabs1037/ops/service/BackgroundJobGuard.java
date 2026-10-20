package com.sklabs1037.sklabs1037.ops.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 백그라운드 작업(스케줄러 · 서버 시작 작업)을 이 서버에서 돌릴지
 *
 * 왜 필요한가 (2026-10-20 장애)
 *   - 로컬 PC와 운영 서버가 같은 토스 키를 씀 → 토스는 키 하나에 토큰 하나만 유효
 *   - 로컬에서 코드를 받아 서버가 다시 켜지는 순간 운영 서버와 동시에 3,000종목 수집을 시작
 *     → 서로 토큰을 새로 받아 상대 토큰을 무효로 만듦(401 invalid-token) → 대부분 실패 + 장애 메일
 *   - 같은 이유로 로컬에서도 뉴스 요약(Claude 비용), 아침 브리핑 메일, 감시 메일이 운영과 겹쳐 나갈 수 있음
 *
 * ops.background-jobs (환경 변수 OPS_BACKGROUND_JOBS)
 *   - auto (기본): 운영(FRONTEND_URL이 localhost가 아님)에서만 켬 → 로컬 PC에서는 자동으로 꺼짐
 *   - true / false: 강제로 켜기 / 끄기
 * - 꺼져 있어도 관리자 화면의 "지금 실행"(MANUAL)과 화면에서 쓰는 기능(현재가, 종목 열기 등)은 그대로
 */
@Slf4j
@Component
public class BackgroundJobGuard {

    private final boolean enabled;

    public BackgroundJobGuard(@Value("${ops.background-jobs:auto}") String mode,
                              @Value("${app.frontend-url:}") String frontendUrl) {

        String m = mode == null ? "auto" : mode.trim().toLowerCase();
        boolean local = frontendUrl == null || frontendUrl.isBlank()
                || frontendUrl.contains("localhost") || frontendUrl.contains("127.0.0.1");

        this.enabled = switch (m) {
            case "true" -> true;
            case "false" -> false;
            default -> !local;
        };

        log.info("백그라운드 작업(스케줄러·서버 시작 작업): {} (설정 {}, 주소 {})",
                enabled ? "켜짐" : "꺼짐 - 로컬 PC에서는 토스 토큰·메일이 운영과 겹치지 않게", m, frontendUrl);

    }

    public boolean enabled() {

        return enabled;

    }
}
