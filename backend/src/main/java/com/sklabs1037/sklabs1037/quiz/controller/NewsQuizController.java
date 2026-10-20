package com.sklabs1037.sklabs1037.quiz.controller;

import com.sklabs1037.sklabs1037.quiz.service.NewsQuizService;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 📰 오늘의 뉴스 퀴즈
 *
 *   GET  /api/quiz                      오늘 문제(정답 빼고) + 참여 통계 + 이번 주 랭킹 (+ 이미 풀었으면 내 결과, 연속 참여)
 *   POST /api/quiz/submit { answers }   채점 → { score, total, items(정답·해설·근거 기사), stats } (로그인하면 하루 한 번 기록)
 */
@Slf4j
@RestController
@RequestMapping("/api/quiz")
public class NewsQuizController {

    @Autowired
    private NewsQuizService newsQuizService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> today(HttpServletRequest request) {

        try {

            UserLoginVO user = getLoginUser(request);
            Map<String, Object> body = new HashMap<>(newsQuizService.today(user == null ? null : user.getId()));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("뉴스 퀴즈 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "퀴즈를 불러오지 못했습니다.");

        }

    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        try {

            List<Integer> answers = new ArrayList<>();
            if (req.get("answers") instanceof List<?> list) {
                for (Object o : list) answers.add(o instanceof Number n ? n.intValue() : null);
            }

            UserLoginVO user = getLoginUser(request);
            Map<String, Object> body = new HashMap<>(newsQuizService.submit(user == null ? null : user.getId(), answers));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("뉴스 퀴즈 채점 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "채점하지 못했습니다.");

        }

    }

    private UserLoginVO getLoginUser(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        return (session == null) ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
