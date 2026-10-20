package com.sklabs1037.sklabs1037.stock.dao;

import com.sklabs1037.sklabs1037.stock.vo.StockBoardVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 종목 토론방 Dao
 * - 메서드 이름 = StockBoardMapper.xml의 id
 */
@Mapper
public interface StockBoardDao {

    // ===== 글 =====

    // 종목 글 목록 (최신순)
    List<StockBoardVO> selectPostList(StockBoardVO vo);

    // 전 종목 최근 글 (메인 화면, 삭제된 글 제외)
    List<StockBoardVO> selectRecentPostList(int limit);

    // 종목 글 개수
    int countPost(Long stockId);

    // 최근 7일 의견 비율 (오를 것 / 내릴 것 개수)
    StockBoardVO selectOpinionCount(Long stockId);

    // 글 1개 (삭제 권한 확인용, 삭제된 글 제외)
    StockBoardVO selectPost(Long postId);

    // 최근 n초 안에 쓴 내 글 개수 (도배 방지)
    int countRecentPost(StockBoardVO vo);

    // 글 쓰기 (없는 종목이면 0)
    int insertPost(StockBoardVO vo);

    // 글 삭제 (소프트 삭제)
    int deletePost(StockBoardVO vo);

    // ===== 댓글 =====

    // 글의 댓글 목록 (오래된 순)
    List<StockBoardVO> selectCommentList(Long postId);

    // 댓글 1개 (삭제 권한 확인용, 삭제된 댓글 제외)
    StockBoardVO selectComment(Long commentId);

    // 최근 n초 안에 쓴 내 댓글 개수 (도배 방지)
    int countRecentComment(StockBoardVO vo);

    // 댓글 쓰기 (삭제된 글이면 0)
    int insertComment(StockBoardVO vo);

    // 댓글 삭제 (소프트 삭제)
    int deleteComment(StockBoardVO vo);

    // 좋아요 (이미 눌렀으면 0) / 취소 (안 눌렀으면 0) / 개수
    int insertLike(StockBoardVO vo);

    int deleteLike(StockBoardVO vo);

    int countLike(Long postId);

    // 신고 (이미 신고했으면 0) / 처리 안 된 신고 수
    int insertReport(StockBoardVO vo);

    int countOpenReport(Long postId);

    // 관리자: 처리할 신고가 있는 글 / 그 개수 / 한꺼번에 처리 / 삭제된 글 포함 글 정보
    List<StockBoardVO> selectReportedPosts();

    int countReportedPosts();

    int handleReports(StockBoardVO vo);

    StockBoardVO selectPostAny(Long postId);
}