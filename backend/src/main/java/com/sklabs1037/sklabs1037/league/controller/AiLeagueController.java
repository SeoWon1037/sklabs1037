package com.sklabs1037.sklabs1037.league.controller;

import com.sklabs1037.sklabs1037.league.service.AiLeagueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 🤖 AI vs 사람 투자 리그
 *
 *   GET /api/league   AI 투자자 성적·보유 종목·수익률 추이 + 사람 평균·1등 + 최근 매매 일지 (누구나, 1분 캐시)
 *
 * - 매매 실행은 스케줄러(평일 09:10) 또는 관리자 운영 화면 "지금 실행" (작업 이름 AI_LEAGUE)
 */
@Slf4j
@RestController
@RequestMapping("/api/league")
public class AiLeagueController {

    @Autowired
    private AiLeagueService aiLeagueService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> league() {

        try {

            Map<String, Object> body = new HashMap<>(aiLeagueService.getLeague());
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("AI 리그 조회 오류", e);

            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", "AI 리그 정보를 불러오지 못했습니다.");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);

        }

    }
}
