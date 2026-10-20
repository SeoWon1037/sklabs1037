package com.sklabs1037.sklabs1037.predict.scheduler;

import com.sklabs1037.sklabs1037.ops.service.OpsJobs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 🎯 예측 게임 채점 (평일 21:10, 20:30 시세 수집이 끝난 뒤 → 진짜 종가로)
 */
@Component
public class PredictScheduler {

    @Autowired
    private OpsJobs opsJobs;

    @Scheduled(cron = "${predict.cron:0 10 21 * * MON-FRI}", zone = "Asia/Seoul")
    public void grade() {

        opsJobs.run("PREDICT_GRADE", "SCHEDULE");

    }
}
