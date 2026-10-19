package com.sklabs1037.sklabs1037.alert.scheduler;

import com.sklabs1037.sklabs1037.alert.service.IPriceAlertService;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 목표가 알림 스케줄러
 * - 정규장(평일 09:00~15:30) 1분마다, 매분 30초에 확인 (지정가 체결 확인과 시각을 엇갈리게)
 */
@Slf4j
@Component
public class PriceAlertScheduler {

    @Autowired
    private IPriceAlertService priceAlertService;

    @Scheduled(cron = "30 * 9-15 * * MON-FRI", zone = "Asia/Seoul")
    public void check() {

        if (!MockMarketHours.isOpen(MockMarketHours.now())) return;

        try {

            int sent = priceAlertService.checkAndNotify();
            if (sent > 0) log.info("가격 알림 메일 발송: {}건", sent);

        } catch (Exception e) {

            log.error("가격 알림 확인 실패", e);

        }

    }
}
