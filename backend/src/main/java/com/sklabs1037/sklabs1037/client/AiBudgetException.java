package com.sklabs1037.sklabs1037.client;

/**
 * 💸 AI 하루 한도를 다 썼거나 관리자가 긴급 정지 → Claude를 부르지 않음
 * - IllegalStateException이라 기존 기능들은 이 메시지를 그대로 사용자에게 보여줌
 * - 작업(스케줄)은 실패 메일 대신 "건너뜀"으로 기록 (JobRunService)
 */
public class AiBudgetException extends IllegalStateException {

    public AiBudgetException(String message) {
        super(message);
    }
}
