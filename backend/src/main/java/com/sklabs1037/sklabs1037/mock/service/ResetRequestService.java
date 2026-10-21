package com.sklabs1037.sklabs1037.mock.service;

import com.sklabs1037.sklabs1037.me.dao.MeDao;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.mock.dao.ResetRequestDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * ♻️ 모의투자 계좌 초기화 신청 (관리자가 승인해야 초기화)
 *
 * - 사용자: "초기화 신청"(이유 선택) → 기다리는 중 (한 사람당 1개) · 직접 취소 가능
 *   → 관리자에게 🔔 알림
 * - 관리자(/admin/resets): 승인 → 그 자리에서 초기화(보유·주문·거래·그래프 지우고 3,000만 원) / 거절(한마디)
 *   → 신청한 사람에게 🔔 알림
 */
@Slf4j
@Service
public class ResetRequestService {

    @Autowired
    private ResetRequestDao resetRequestDao;

    @Autowired
    private IMockTradeService mockTradeService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MeDao meDao;

    // ===== 사용자 =====
    public Map<String, Object> mine(Long userId) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", resetRequestDao.selectMyPending(userId));
        m.put("last", resetRequestDao.selectMyLastHandled(userId));
        return m;

    }

    public Map<String, Object> request(Long userId, String reason) {

        String r = reason == null ? "" : reason.strip();
        if (r.length() > 200) r = r.substring(0, 200);

        if (resetRequestDao.insertRequest(userId, r.isEmpty() ? null : r) == 0) {
            throw new IllegalArgumentException("이미 초기화 신청이 들어가 있어요. 관리자 승인을 기다려 주세요.");
        }

        for (Long adminId : resetRequestDao.selectAdminIds()) {
            notificationService.notify(adminId, "ADMIN", "♻️ 모의투자 초기화 신청",
                    java.util.Objects.toString(meDao.selectNickname(userId), "회원") + "님" + (r.isEmpty() ? "" : " · " + r), "/admin/resets");
        }

        return mine(userId);

    }

    public Map<String, Object> cancel(Long userId) {

        if (resetRequestDao.cancel(userId) == 0) throw new IllegalArgumentException("취소할 신청이 없어요.");
        return mine(userId);

    }

    // ===== 관리자 =====
    public Map<String, Object> adminList() {

        List<Map<String, Object>> list = new ArrayList<>();

        for (Map<String, Object> r : resetRequestDao.selectForAdmin()) {
            Map<String, Object> row = new LinkedHashMap<>(r);
            // 기다리는 신청은 지금 계좌 상태도 같이 (승인하면 무엇이 지워지는지)
            if ("PENDING".equals(r.get("status")) && r.get("userId") instanceof Number uid) {
                try {
                    row.put("account", summary(uid.longValue()));
                } catch (Exception e) {
                    log.warn("초기화 신청 계좌 요약 실패 user={} {}", uid, e.getMessage());
                }
            }
            row.remove("userId");
            list.add(row);
        }

        return Map.of("requests", list, "pending", resetRequestDao.countPending());

    }

    public int pendingCount() {

        return resetRequestDao.countPending();

    }

    // 승인 → 바로 초기화 (초기화가 실패하면 승인도 되돌림)
    @Transactional
    public void approve(Long adminId, Long id) {

        Map<String, Object> r = resetRequestDao.selectRequest(id);
        if (r == null || resetRequestDao.handle(id, "APPROVED", adminId, null) == 0) {
            throw new IllegalArgumentException("이미 처리된 신청이에요.");
        }

        Long userId = ((Number) r.get("userId")).longValue();
        mockTradeService.reset(userId);

        notificationService.notify(userId, "MOCK", "♻️ 계좌 초기화가 승인됐어요",
                "가상 3,000만 원으로 새로 시작해요", "/mock");

    }

    public void reject(Long adminId, Long id, String note) {

        Map<String, Object> r = resetRequestDao.selectRequest(id);
        String n = note == null ? "" : note.strip();
        if (n.length() > 200) n = n.substring(0, 200);

        if (r == null || resetRequestDao.handle(id, "REJECTED", adminId, n.isEmpty() ? null : n) == 0) {
            throw new IllegalArgumentException("이미 처리된 신청이에요.");
        }

        notificationService.notify(((Number) r.get("userId")).longValue(), "MOCK", "♻️ 계좌 초기화 신청이 거절됐어요",
                n.isEmpty() ? "궁금한 점은 관리자에게 문의해 주세요" : n, "/mock");

    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> summary(Long userId) {

        Map<String, Object> all = mockTradeService.getAccount(userId);
        Map<String, Object> a = (Map<String, Object>) all.get("account");
        List<?> holdings = (List<?>) all.get("holdings");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalAsset", a.get("totalAsset"));
        m.put("returnRate", a.get("returnRate"));
        m.put("holdings", holdings == null ? 0 : holdings.size());
        m.put("resetCount", a.get("resetCount"));
        return m;

    }
}
