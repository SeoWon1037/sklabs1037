package com.sklabs1037.sklabs1037.stock.service;

import com.sklabs1037.sklabs1037.stock.vo.StockBoardVO;

import java.util.List;

/**
 * 종목 토론방 Service
 * - 입력값이 잘못되면 IllegalArgumentException (메시지를 그대로 화면에 보여줌)
 * - 삭제 권한이 없으면 SecurityException
 */
public interface IStockBoardService {

    // 종목 글 목록 (page: 1부터)
    List<StockBoardVO> getPosts(Long stockId, int page, int size);

    // 종목 글 개수
    int countPosts(Long stockId);

    // 최근 7일 의견 개수 (upCount / downCount)
    StockBoardVO getOpinionCount(Long stockId);

    // 글 쓰기
    void writePost(Long userId, Long stockId, String opinion, String content);

    // 글 삭제 (본인 또는 관리자)
    void deletePost(Long userId, boolean admin, Long postId);

    // 댓글 목록
    List<StockBoardVO> getComments(Long postId);

    // 댓글 쓰기
    void writeComment(Long userId, Long postId, String content);

    // 댓글 삭제 (본인 또는 관리자)
    void deleteComment(Long userId, boolean admin, Long commentId);
}