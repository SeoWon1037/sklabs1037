package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.ops.dao.JobRunDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 관리자 장애 알림 메일
 * - 받는 사람: T_USER.ROLE = 'ADMIN' 인 회원의 이메일 (설정 따로 없음)
 * - 같은 알림(key)은 1시간에 한 번만 → 1분마다 실패하는 작업이 메일 폭탄이 되지 않게
 * - 메일 발송이 실패해도 작업에는 영향 없음 (로그만)
 */
@Slf4j
@Service
public class OpsAlertService {

    // 같은 알림 다시 보내기까지 (초)
    private static final long THROTTLE_SECONDS = 3600;

    @Autowired
    private JobRunDao jobRunDao;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${app.frontend-url}")
    private String siteUrl;

    // 알림 key → 마지막으로 보낸 시각
    private final Map<String, Instant> sentAt = new ConcurrentHashMap<>();

    // 장애 알림 (1시간에 한 번) → 실제로 보냈으면 true
    public boolean alert(String key, String title, String body) {

        Instant now = Instant.now();
        Instant before = sentAt.get(key);

        if (before != null && now.isBefore(before.plusSeconds(THROTTLE_SECONDS))) return false;

        sentAt.put(key, now);

        return send("[SKLabs 운영] " + title, body);

    }

    // 바로 보내기 (관리자 화면의 "테스트 메일")
    public boolean send(String subject, String body) {

        List<String> to;

        try {
            to = jobRunDao.selectAdminEmails();
        } catch (Exception e) {
            log.warn("관리자 이메일 조회 실패: {}", e.getMessage());
            return false;
        }

        if (to.isEmpty() || mailFrom == null || mailFrom.isBlank()) {
            log.warn("관리자 메일을 보낼 수 없음 (관리자 이메일 또는 메일 설정 없음): {}", subject);
            return false;
        }

        try {

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to.toArray(String[]::new));
            message.setSubject(subject);
            message.setText(body + "\n\n운영 상태 보기: " + siteUrl + "/admin/ops");

            mailSender.send(message);
            log.info("관리자 메일 발송: {}", subject);

            return true;

        } catch (Exception e) {

            log.warn("관리자 메일 발송 실패: {} ({})", subject, e.getMessage());
            return false;

        }

    }
}
