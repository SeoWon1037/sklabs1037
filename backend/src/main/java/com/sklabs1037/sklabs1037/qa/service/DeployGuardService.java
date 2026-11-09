package com.sklabs1037.sklabs1037.qa.service;

import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.ops.service.OpsAlertService;
import com.sklabs1037.sklabs1037.qa.dao.QaRunDao;
import com.sklabs1037.sklabs1037.qa.vo.QaRunVO;
import com.sklabs1037.sklabs1037.support.dao.InquiryDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * ⏪ 배포 지킴이 (배포 직후 문제가 생기면 바로 전 버전으로 자동 되돌리기)
 *
 * GitHub Actions(deploy.yml) 흐름
 *   1. 배포 전: 지금 돌고 있는 이미지에 :prev 표시
 *   2. 배포 후 1차 (서버 안에서): /api/health 가 3분 안에 OK가 아니면 → 바로 :prev 로 되돌리기
 *   3. 배포 후 2차 (QA 봇): 점검 결과가 나오면 verdict(runId)로 되돌릴지 물어봄
 *   4. 되돌렸으면 rolledBack()으로 알려줌 → 관리자 메일 + 📲
 *
 * 되돌리는 기준 = 서버 규칙만 (AI 화면 판정은 알림만, 되돌리지 않음)
 *   - 이번 점검의 "규칙(RULE)으로 찾은 심각(HIGH)" 문제 중 바로 전 점검에는 없던 것이 있을 때
 *     (페이지 오류 · 요청 실패 5xx/연결 안 됨 · 기능 흐름 실패)
 *   - 전에도 있던 문제(예: 외부 시세 서버 장애)는 이번 배포 탓이 아니므로 되돌리지 않음
 */
@Slf4j
@Service
public class DeployGuardService {

    @Autowired
    private QaRunDao qaRunDao;

    @Autowired
    private OpsAlertService opsAlertService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private InquiryDao inquiryDao;

    @Autowired
    private JsonMapper jsonMapper;

    // ============================================
    // QA 점검 결과 → 되돌릴까? { status: ANALYZING|PASS|WARN|FAIL, rollback, reasons[] }
    // ============================================
    public Map<String, Object> verdict(Long runId) {

        QaRunVO run = qaRunDao.selectRun(runId);
        if (run == null) throw new IllegalArgumentException("없는 점검 기록입니다.");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("status", run.getStatus());

        if ("ANALYZING".equals(run.getStatus())) {
            out.put("rollback", false);
            out.put("reasons", List.of());
            return out;
        }

        QaRunVO prev = qaRunDao.selectPrevRun(runId);
        List<String> reasons = newSeriousProblems(findings(run), prev == null ? List.of() : findings(prev));

        out.put("rollback", "DEPLOY".equals(run.getSource()) && !reasons.isEmpty());
        out.put("reasons", reasons);
        return out;

    }

    // ============================================
    // 되돌렸다는 알림 (Actions → 되돌린 뒤의 옛 서버가 받음)
    // ============================================
    public void rolledBack(String kind, String commit, String reason) {

        String k = "HEALTH".equals(kind) ? "서버가 3분 안에 정상으로 뜨지 않음" : "배포 직후 QA 봇이 새 심각한 문제를 찾음";
        String sha = cut(commit, 7);
        String why = cut(reason, 500);

        log.warn("⏪ 배포 되돌림 ({}): {} {}", kind, sha, why);

        opsAlertService.alert("DEPLOY_ROLLBACK_" + sha, "⏪ 배포를 자동으로 되돌렸어요 (" + sha + ")",
                "이유: " + k + "\n" + why + "\n\n지금은 바로 전 버전이 돌고 있어요. 고친 뒤 다시 push 하면 새로 배포돼요.");

        for (Long admin : inquiryDao.selectAdminIds()) {
            notificationService.notify(admin, "ADMIN", "⏪ 배포를 자동으로 되돌렸어요", k + " · " + sha, "/admin/qa", "ROLLBACK:" + sha);
        }

    }

    // 이번에 새로 생긴 "규칙 · 심각" 문제 (순수 계산, 테스트 가능)
    static List<String> newSeriousProblems(List<Map<String, Object>> now, List<Map<String, Object>> before) {

        Set<String> old = new HashSet<>();
        for (Map<String, Object> f : before) if (serious(f)) old.add(key(f));

        List<String> out = new ArrayList<>();
        for (Map<String, Object> f : now) {
            if (serious(f) && !old.contains(key(f)) && !out.contains(key(f))) out.add(key(f));
        }
        return out;

    }

    private static boolean serious(Map<String, Object> f) {

        return "HIGH".equals(f.get("severity")) && "RULE".equals(f.get("by"));

    }

    private static String key(Map<String, Object> f) {

        return f.get("key") + " · " + f.get("title");

    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findings(QaRunVO r) {

        if (r.getFindingsJson() == null || r.getFindingsJson().isBlank()) return List.of();
        try {
            List<Map<String, Object>> list = jsonMapper.readValue(r.getFindingsJson(), List.class);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("QA 결과 읽기 실패: {}", e.getMessage());
            return List.of();
        }

    }

    private static String cut(String s, int max) {

        String t = s == null ? "" : s.strip();
        return t.length() > max ? t.substring(0, max) : t;

    }
}
