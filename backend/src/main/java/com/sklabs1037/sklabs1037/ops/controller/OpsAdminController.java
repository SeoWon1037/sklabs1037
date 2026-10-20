package com.sklabs1037.sklabs1037.ops.controller;

import com.sklabs1037.sklabs1037.admin.service.IAdminFeedbackService;
import com.sklabs1037.sklabs1037.client.TossApiClient;
import com.sklabs1037.sklabs1037.ops.dao.JobRunDao;
import com.sklabs1037.sklabs1037.ops.service.JobRunService;
import com.sklabs1037.sklabs1037.ops.service.OpsAlertService;
import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import com.sklabs1037.sklabs1037.ops.vo.JobRunVO;
import com.sklabs1037.sklabs1037.user.controller.UserLoginController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자: 운영 상태 Controller
 *
 * API 목록 (관리자만)
 *   GET  /api/admin/ops                    → { success, jobs, runs, toss, data, running, manualJobs }
 *   POST /api/admin/ops/run { job }        → { success, started }   작업 지금 실행 (별도 스레드)
 *   POST /api/admin/ops/test-mail          → { success, sent }      관리자 메일 테스트
 *
 * - 로그인 + T_USER.ROLE = 'ADMIN' 인 회원만 (매 요청마다 DB에서 확인, AI 피드백 화면과 같은 방식)
 * - 토스 토큰 값·키는 절대 내려보내지 않음 (발급 시각·마지막 성공/실패만)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ops")
public class OpsAdminController {

    // 화면에 보여줄 작업 순서 + 언제 도는지
    private static final List<String[]> JOB_SCHEDULE = List.of(
            new String[]{"STOCK_DAILY", "평일 20:30"},
            new String[]{"STOCK_STARTUP", "서버 시작 시"},
            new String[]{"STOCK_STALE", "21:30 감시에서 / 수동"},
            new String[]{"NEWS_COLLECT", "30분마다"},
            new String[]{"NEWS_SUMMARY", "매일 07:30, 16:30"},
            new String[]{"MORNING_BRIEF", "평일 08:00"},
            new String[]{"AI_LEAGUE", "평일 09:10"},
            new String[]{"AI_IMPROVE", "매일 04:10 (새 👎 3개 이상일 때)"},
            new String[]{"MOCK_EXPIRE", "평일 15:35"},
            new String[]{"MOCK_SNAPSHOT", "평일 15:40"},
            new String[]{"MOCK_MATCH", "장중 1분마다 (실패만 기록)"},
            new String[]{"ALERT_CHECK", "장중 1분마다 (실패만 기록)"}
    );

    @Autowired
    private IAdminFeedbackService adminFeedbackService;

    @Autowired
    private JobRunDao jobRunDao;

    @Autowired
    private OpsJobs opsJobs;

    @Autowired
    private JobRunService jobRunService;

    @Autowired
    private OpsAlertService opsAlertService;

    @Autowired
    private TossApiClient tossApiClient;

    @Autowired
    private JsonMapper jsonMapper;

    // 운영 상태 한 번에
    @GetMapping
    public ResponseEntity<Map<String, Object>> status(HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        try {

            // 작업별 마지막 실행
            Map<String, JobRunVO> latest = new HashMap<>();
            for (JobRunVO r : jobRunDao.selectLatestPerJob()) latest.put(r.getJobName(), r);

            List<Map<String, Object>> jobs = new ArrayList<>();

            for (String[] j : JOB_SCHEDULE) {

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("job", j[0]);
                row.put("label", JobRunService.label(j[0]));
                row.put("schedule", j[1]);
                row.put("manual", OpsJobs.MANUAL_JOBS.contains(j[0]));
                row.put("last", latest.get(j[0]));
                jobs.add(row);

            }

            JobRunVO param = new JobRunVO();
            param.setLimit(50);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("jobs", jobs);
            body.put("runs", jobRunDao.selectRecentRuns(param));
            body.put("toss", tossApiClient.health());
            body.put("data", jobRunDao.selectDataHealth());
            body.put("running", opsJobs.runningJobs());
            body.put("backgroundJobs", jobRunService.backgroundEnabled());   // 이 서버에서 스케줄러가 도는지 (로컬 PC면 false)

            return ResponseEntity.ok(body);

        } catch (Exception e) {

            log.error("운영 상태 조회 오류", e);
            return fail(HttpStatus.INTERNAL_SERVER_ERROR, "운영 상태를 불러오지 못했습니다. (T_JOB_RUN 테이블이 있는지 확인)");

        }

    }

    // 작업 지금 실행
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@RequestBody Map<String, Object> req, HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        String job = req.get("job") == null ? null : String.valueOf(req.get("job"));
        if (job == null || !OpsJobs.MANUAL_JOBS.contains(job)) return fail(HttpStatus.BAD_REQUEST, "실행할 수 없는 작업입니다.");

        boolean started = opsJobs.runAsync(job);
        log.info("관리자 수동 실행: {} → {}", job, started ? "시작" : "이미 실행 중");

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("started", started);
        body.put("message", started ? JobRunService.label(job) + " 시작했어요." : "이미 실행 중이에요.");

        return ResponseEntity.ok(body);

    }

    // 관리자 메일 테스트
    @PostMapping("/test-mail")
    public ResponseEntity<Map<String, Object>> testMail(HttpServletRequest request) {

        ResponseEntity<Map<String, Object>> denied = checkAdmin(request);
        if (denied != null) return denied;

        boolean sent = opsAlertService.send("[SKLabs 운영] 테스트 메일", "관리자 장애 알림 메일이 잘 도착하는지 확인하는 테스트 메일이에요.");

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("sent", sent);
        body.put("message", sent ? "관리자 메일로 보냈어요." : "보내지 못했어요. 관리자 계정 이메일과 메일 설정을 확인해주세요.");

        return ResponseEntity.ok(body);

    }

    // ============================================
    // 공통 (AdminFeedbackController와 같은 방식)
    // ============================================

    private ResponseEntity<Map<String, Object>> checkAdmin(HttpServletRequest request) {

        Long userId = getLoginUserId(request);

        if (userId == null) return fail(HttpStatus.UNAUTHORIZED, "로그인 후 이용할 수 있습니다.");
        if (!adminFeedbackService.isAdmin(userId)) return fail(HttpStatus.FORBIDDEN, "관리자만 볼 수 있습니다.");

        return null;

    }

    @SuppressWarnings("unchecked")
    private Long getLoginUserId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null) return null;

        Object loginUser = session.getAttribute(UserLoginController.LOGIN_USER);
        if (loginUser == null) return null;

        Map<String, Object> map = jsonMapper.convertValue(loginUser, Map.class);
        Object id = map.get("id");

        return (id instanceof Number n) ? n.longValue() : null;

    }

    private ResponseEntity<Map<String, Object>> fail(HttpStatus status, String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);

    }
}
