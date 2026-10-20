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
    private String symbol;          // 종목코드 (메인 화면 최근 글)
    private String stockName;       // 종목 이름 (메인 화면 최근 글)
    private String nickname;
    private Long insAt;             // 작성 시각 (밀리초)
    private Boolean deleted;        // 삭제된 글 (댓글이 남아 있어서 자리만 보여줌)
    private Integer commentCount;

    // ===== 의견 비율 =====
    private Integer upCount;
    private Integer downCount;
    private Integer likeCount;      // 좋아요 수
    private Boolean liked;          // 내가 좋아요 눌렀는지
    private Integer reportCount;    // 처리 안 된 신고 수
    private Boolean reported;       // 내가 신고했는지
    private Long viewerId;          // 목록을 보는 사람 (좋아요·신고 여부 확인용, 로그아웃이면 null)
    private String sort;            // 'latest' / 'popular'
    private String reason;          // 신고 사유
    private String memo;            // 신고 메모
    private String status;          // 신고 처리 결과 (DISMISSED / DELETED)
    private String reasons;         // 관리자 화면: 사유별 개수 "SPAM:2,ABUSE:1"
    private String memos;           // 관리자 화면: 신고 메모 모음
    private Long firstReportAt;     // 관리자 화면: 처음 신고된 시각 (밀리초)
    private Boolean blinded;        // 신고가 쌓여 가려진 글
}