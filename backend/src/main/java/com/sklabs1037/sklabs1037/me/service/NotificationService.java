package com.sklabs1037.sklabs1037.me.service;

import com.sklabs1037.sklabs1037.me.dao.MeDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 🔔 사이트 안 알림
 *
 * 보내는 곳
 *   - BadgeService        : 새 배지 (BADGE)
 *   - PredictService      : 예측 채점 끝 → 그 날 투표한 사람마다 "n문제 중 m개 적중" (PREDICT)
 *   - StockBoardService   : 내 토론 글에 댓글 (COMMENT), 신고가 쌓여 내 글이 가려짐 (REPORT)
 *   - PriceAlertService   : 목표가 도달 (ALERT, 메일과 같이)
 *
 * - 알림 저장이 실패해도 원래 일(댓글·채점·알림 메일)은 그대로 진행 (여기서 예외를 삼킴)
 * - 화면은 상단 🔔 (60초마다 안 읽은 수), 누르면 목록
 */
@Slf4j
@Service
public class NotificationService {

    @Autowired
    private MeDao meDao;

    public void notify(Long userId, String kind, String title, String body, String link) {

        notify(userId, kind, title, body, link, null);

    }

    // dedupeKey: 같은 키로는 한 사람에게 한 번만
    public void notify(Long userId, String kind, String title, String body, String link, String dedupeKey) {

        if (userId == null) return;

        try {
            meDao.insertNotification(userId, kind, cut(title, 120), cut(body, 300), link, dedupeKey);
        } catch (Exception e) {
            log.warn("알림 저장 실패 ({} → {}): {}", kind, userId, e.getMessage());
        }

    }

    public void predictGraded(LocalDate tradeDate) {

        try {
            meDao.insertPredictNotifications(tradeDate, "🎯 " + tradeDate.getMonthValue() + "/" + tradeDate.getDayOfMonth() + " 예측 결과가 나왔어요");
        } catch (Exception e) {
            log.warn("예측 결과 알림 실패 ({}): {}", tradeDate, e.getMessage());
        }

    }

    public List<Map<String, Object>> list(Long userId) {

        return meDao.selectNotifications(userId, 30);

    }

    public int unread(Long userId) {

        return meDao.countUnread(userId);

    }

    public void markRead(Long userId, Long id) {

        meDao.markRead(userId, id);

    }

    private static String cut(String s, int max) {

        if (s == null) return null;

        return s.length() <= max ? s : s.substring(0, max);

    }
}
