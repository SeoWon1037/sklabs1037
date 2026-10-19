package com.sklabs1037.sklabs1037.alert.controller;

import com.sklabs1037.sklabs1037.alert.service.IPriceAlertService;
import com.sklabs1037.sklabs1037.alert.vo.PriceAlertVO;
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
import java.util.List;
import java.util.Map;

/**
 * 목표가 알림 Controller (로그인한 사람만, 본인 알림만)
 *
 * API 목록
 *   GET    /api/alerts?stockId=1                    내 알림 (stockId 없으면 전체)  → { success, loggedIn, alerts }
 *   POST   /api/alerts { stockId, condition, targetPrice }   알림 만들기       → { success }
 *   DELETE /api/alerts/{alertId}                     삭제                         → { success }
 *   POST   /api/alerts/{alertId}/reactivate          보낸 알림 다시 켜기          → { success }
 */
@Slf4j
@RestController
@RequestMapping("/api/alerts")
public class PriceAlertController {

    @Autowired
    private IPriceAlertService priceAlertService;

    // 내 알림
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) Long stockId,
                                                    HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("loggedIn", loginUser != null);

        if (loginUser == null) {
            body.put("alerts", List.of());
            return ResponseEntity.ok(body);
        }

        try {

            body.put("alerts", priceAlertService.getMyAlerts(loginUser.getId(), stockId));

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("가격 알림 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "알림을 불러오지 못했습니다.");

        }

    }

    // 알림 만들기
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody PriceAlertVO vo, HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 알림을 걸 수 있습니다.");

        try {

            priceAlertService.create(loginUser.getId(), vo.getStockId(), vo.getCondition(), vo.getTargetPrice());

            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("가격 알림 만들기 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "알림을 만들지 못했습니다.");

        }

    }

    // 삭제
    @DeleteMapping("/{alertId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long alertId, HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            priceAlertService.delete(loginUser.getId(), alertId);

            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("가격 알림 삭제 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "알림을 삭제하지 못했습니다.");

        }

    }

    // 다시 켜기
    @PostMapping("/{alertId}/reactivate")
    public ResponseEntity<Map<String, Object>> reactivate(@PathVariable Long alertId, HttpServletRequest request) {

        UserLoginVO loginUser = getLoginUser(request);
        if (loginUser == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");

        try {

            priceAlertService.reactivate(loginUser.getId(), alertId);

            return ok();

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("가격 알림 다시 켜기 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "알림을 다시 켜지 못했습니다.");

        }

    }

    // ============================================
    // 공통
    // ============================================

    private UserLoginVO getLoginUser(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        return (session == null) ? null : (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);

    }

    private ResponseEntity<Map<String, Object>> ok() {

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);

        return ResponseEntity.ok(body);

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
