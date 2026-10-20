package com.sklabs1037.sklabs1037.predict.controller;

import com.sklabs1037.sklabs1037.predict.service.PredictService;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 🎯 오를까 내릴까
 *
 *   GET  /api/predict              지금 라운드 5문제 + 지난 라운드 결과 + 내 기록 + 랭킹 + 모멘텀 봇·다수결 성적 (누구나)
 *   POST /api/predict/vote { itemId, pick: UP|DOWN }   투표 (로그인, 마감 전까지 바꿀 수 있음)
 */
@Slf4j
@RestController
@RequestMapping("/api/predict")
public class PredictController {

    @Autowired
    private PredictService predictService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> overview(HttpServletRequest request) {

        try {

            UserLoginVO user = getLoginUser(request);
            Map<String, Object> body = new HashMap<>(predictService.overview(user == null ? null : user.getId()));
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("예측 게임 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "불러오지 못했습니다.");

        }

    }

    @PostMapping("/vote")
    public ResponseEntity<Map<String, Object>> vote(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        UserLoginVO user = getLoginUser(request);
        if (user == null) return fail(HttpStatus.UNAUTHORIZED, "로그인하면 예측에 참여할 수 있어요.");

        try {

            Long itemId = req.get("itemId") instanceof Number n ? n.longValue() : null;
            predictService.vote(user.getId(), itemId, String.valueOf(req.get("pick")));

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("예측 투표 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "저장하지 못했습니다.");

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
