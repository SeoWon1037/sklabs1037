package com.sklabs1037.sklabs1037.alert.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 목표가 알림 VO
 * - 사용 테이블: T_PRICE_ALERT (+ T_STOCK, T_USER)
 */
@Getter
@Setter
public class PriceAlertVO {

    // ===== 알림 =====
    private Long alertId;
    private Long userId;
    private Long stockId;
    private String condition;           // ABOVE: 이상 / BELOW: 이하
    private BigDecimal targetPrice;     // 목표가
    private String status;              // ACTIVE / SENT
    private BigDecimal sentPrice;       // 알림 보낼 때 가격
    private Long sentAt;                // 알림 보낸 시각 (밀리초)
    private Long insAt;                 // 만든 시각 (밀리초)

    // ===== 종목 / 받는 사람 =====
    private String symbol;
    private String name;
    private String email;
    private String nickname;
}
