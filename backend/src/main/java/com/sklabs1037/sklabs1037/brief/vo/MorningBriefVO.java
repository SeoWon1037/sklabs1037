package com.sklabs1037.sklabs1037.brief.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 아침 브리핑 메일 VO
 * - 사용 테이블: T_MORNING_BRIEF, T_USER
 */
@Getter
@Setter
public class MorningBriefVO {

    private Long userId;
    private String enabledYn;       // Y / N
    private LocalDate lastSentDay;  // 마지막으로 보낸 날
    private LocalDate today;        // 보내려는 날 (중복 발송 막기)

    // 받는 사람
    private String email;
    private String nickname;
}
