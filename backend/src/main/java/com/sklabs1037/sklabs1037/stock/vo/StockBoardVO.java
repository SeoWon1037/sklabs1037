package com.sklabs1037.sklabs1037.stock.vo;

import lombok.Data;

/**
 * 종목 토론방 VO (글 + 댓글 같이 사용)
 */
@Data
public class StockBoardVO {

    // ===== 요청 / 조건 =====
    private Long stockId;
    private Long postId;
    private Long commentId;
    private Long userId;            // 작성자 (세션에서 꺼낸 값만 넣음)
    private String opinion;         // 'UP' / 'DOWN' / null
    private String content;
    private Integer limit;
    private Integer offset;
    private Integer seconds;        // 도배 방지: 최근 n초

    // ===== 조회 결과 =====
    private String nickname;
    private Long insAt;             // 작성 시각 (밀리초)
    private Boolean deleted;        // 삭제된 글 (댓글이 남아 있어서 자리만 보여줌)
    private Integer commentCount;

    // ===== 의견 비율 =====
    private Integer upCount;
    private Integer downCount;
}