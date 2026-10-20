package com.sklabs1037.sklabs1037.persona.controller;

import com.sklabs1037.sklabs1037.persona.dao.PersonaDao;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 🧭 투자 성향 테스트
 *
 *   POST /api/persona/result { typeCode, risk, horizon, basis, discipline }  결과 저장 (누구나, 한 접속 3번까지) → { success, stats }
 *   GET  /api/persona/stats                                                  유형별 비율 → { success, stats: { total, types: {SLD: 12.3, ...} } }
 *
 * - 유형 계산은 화면(질문·점수표)에서, 서버는 값이 말이 되는지만 확인
 *   (축 점수 범위, 유형 코드가 점수와 맞는지) → 통계가 엉뚱한 값으로 오염되지 않게
 */
@Slf4j
@RestController
@RequestMapping("/api/persona")
public class PersonaController {

    private static final int AXIS_MAX = 6;         // 축마다 3문항 × ±2
    private static final int SAVE_PER_SESSION = 3;
    private static final String SESSION_KEY = "personaSaved";

    @Autowired
    private PersonaDao personaDao;

    @PostMapping("/result")
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        try {

            int risk = axis(req.get("risk"));
            int horizon = axis(req.get("horizon"));
            int basis = axis(req.get("basis"));
            int discipline = axis(req.get("discipline"));
            String code = String.valueOf(req.get("typeCode"));

            if (!code.equals(typeOf(risk, horizon, basis))) throw new IllegalArgumentException("결과가 점수와 맞지 않아요.");

            HttpSession session = request.getSession(true);
            int saved = session.getAttribute(SESSION_KEY) instanceof Integer n ? n : 0;

            if (saved < SAVE_PER_SESSION) {
                UserLoginVO user = (UserLoginVO) session.getAttribute(UserLoginController.LOGIN_USER);
                personaDao.insertResult(user == null ? null : user.getId(), code, risk, horizon, basis, discipline);
                session.setAttribute(SESSION_KEY, saved + 1);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("stats", stats());

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {

            return fail(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {

            log.error("투자 성향 저장 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "저장하지 못했습니다.");

        }

    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {

        try {

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("stats", stats());

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("투자 성향 통계 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "불러오지 못했습니다.");

        }

    }

    // 점수 → 유형 (화면과 같은 규칙: 0보다 크면 공격·장기·데이터)
    static String typeOf(int risk, int horizon, int basis) {

        return (risk > 0 ? "R" : "S") + (horizon > 0 ? "L" : "Q") + (basis > 0 ? "D" : "N");

    }

    private Map<String, Object> stats() {

        Map<String, Integer> counts = new TreeMap<>();
        int total = 0;

        for (Map<String, Object> row : personaDao.selectTypeCounts()) {
            int n = ((Number) row.get("count")).intValue();
            counts.put(String.valueOf(row.get("typeCode")), n);
            total += n;
        }

        Map<String, Double> pct = new TreeMap<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            pct.put(e.getKey(), Math.round(e.getValue() * 1000.0 / total) / 10.0);
        }

        Map<String, Object> m = new HashMap<>();
        m.put("total", total);
        m.put("types", pct);

        return m;

    }

    private static int axis(Object o) {

        if (!(o instanceof Number n)) throw new IllegalArgumentException("점수를 확인해주세요.");
        int v = n.intValue();
        if (v < -AXIS_MAX || v > AXIS_MAX) throw new IllegalArgumentException("점수 범위를 벗어났어요.");

        return v;

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
