package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.ops.dao.ClientErrorDao;
import com.sklabs1037.sklabs1037.ops.service.ClientErrorRules.Report;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 🐞 화면 오류 모으기 — 사용자 브라우저에서 난 오류를 서버가 모아 관리자에게 보여줌
 * - 화면(frontend/src/errorReport.js)이 POST /api/track/error 로 보냄 (로그인 없이도, 한 사람이 한 번 접속에 5개까지)
 * - 같은 오류는 한 줄에 횟수만 올림 (ClientErrorRules.sig). 고쳤다고 해둔 오류가 또 생기면 다시 OPEN
 * - 하루에 새 오류 종류는 200개까지만 받음 (누가 일부러 쏟아부어도 표가 불어나지 않게), 30일 지나면 지움
 * - 최근 이틀 안에 2번 이상 난 오류는 🔁 매일 개선(DailyImproveService)의 후보가 됨 → AI가 고치고 사람이 승인
 */
@Slf4j
@Service
public class ClientErrorService {

    static final int NEW_PER_DAY = 200;

    @Autowired
    private ClientErrorDao clientErrorDao;

    // 보고 받기 → 저장했으면 true (잡음 · 한도 초과면 false)
    public boolean report(Map<String, Object> in, String userAgent) {

        Report r = ClientErrorRules.clean(in, userAgent);
        if (r == null) return false;

        Map<String, Object> row = new HashMap<>();
        row.put("sig", r.sig());
        row.put("kind", r.kind());
        row.put("message", r.message());
        row.put("stack", r.stack());
        row.put("path", r.path());
        row.put("ua", r.ua());

        if (clientErrorDao.bump(row) > 0) return true;
        if (clientErrorDao.countNewToday() >= NEW_PER_DAY) return false;
        if (clientErrorDao.insert(row) == 0) clientErrorDao.bump(row);   // 그 사이 다른 요청이 먼저 넣음
        return true;

    }

    // 관리자 목록 { counts, errors }
    public Map<String, Object> list(String status) {

        String s = status == null ? null : status.toUpperCase();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counts", clientErrorDao.selectCounts());
        out.put("errors", clientErrorDao.selectList(ClientErrorRules.STATUSES.contains(s) ? s : null));
        return out;

    }

    public void status(Long id, String status) {

        String s = status == null ? "" : status.toUpperCase();
        if (!ClientErrorRules.STATUSES.contains(s)) throw new IllegalArgumentException("바꿀 수 없는 상태예요.");
        if (clientErrorDao.updateStatus(id, s) == 0) throw new IllegalArgumentException("없는 오류예요.");

    }

    // 매일 04:40 — 30일 동안 다시 안 생긴 오류 지우기
    @Scheduled(cron = "0 40 4 * * *", zone = "Asia/Seoul")
    public void cleanup() {

        try {
            int n = clientErrorDao.deleteOld();
            if (n > 0) log.info("오래된 화면 오류 {}줄 지움", n);
        } catch (Exception e) {
            log.warn("화면 오류 정리 실패: {}", e.getMessage());
        }

    }
}
