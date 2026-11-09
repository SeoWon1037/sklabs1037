package com.sklabs1037.sklabs1037.ops.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

/**
 * ⏪ 배포 직후 살아 있는지 확인 (deploy.yml이 3분 동안 부름 → OK가 아니면 바로 전 버전으로 되돌림)
 *   GET /api/health → 200 { ok: true } / 503 { ok: false }
 * - 서버가 떴고 DB에 연결되는지만 봄 (외부 서비스 장애로 배포를 되돌리지 않게)
 * - 로그인 없이 누구나, 내부 정보는 내보내지 않음
 */
@RestController
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {

        try (Connection c = dataSource.getConnection()) {

            if (c.isValid(2)) return ResponseEntity.ok(Map.of("ok", true));

        } catch (Exception ignored) {
            // 아래 503
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("ok", false));

    }
}
