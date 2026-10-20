package com.sklabs1037.sklabs1037.stock.service;

import com.sklabs1037.sklabs1037.ops.service.OpsAlertService;
import com.sklabs1037.sklabs1037.stock.dao.StockBoardDao;
import com.sklabs1037.sklabs1037.stock.vo.StockBoardVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 종목 토론방 Service 구현
 */
@Service
public class StockBoardServiceImpl implements IStockBoardService {

    // 글자 수 제한
    private static final int POST_MAX = 500;
    private static final int COMMENT_MAX = 200;

    // 도배 방지: 같은 사람은 n초 안에 연속으로 못 씀
    private static final int POST_INTERVAL_SEC = 30;
    private static final int COMMENT_INTERVAL_SEC = 10;

    // 고를 수 있는 의견
    private static final Set<String> OPINIONS = Set.of("UP", "DOWN");

    // 신고 사유 (도배 / 욕설·비방 / 광고 / 허위 정보 / 기타)
    private static final Set<String> REASONS = Set.of("SPAM", "ABUSE", "AD", "FALSE", "ETC");
    private static final int REPORT_MEMO_MAX = 200;

    @Autowired
    private StockBoardDao stockBoardDao;

    // 실시간 알림 (저장이 끝난 뒤에 보냄)
    @Autowired
    private BoardLiveHub boardLiveHub;

    // 신고가 쌓여 글이 가려지면 관리자 메일
    @Autowired
    private OpsAlertService opsAlertService;

    @Override
    public List<StockBoardVO> getPosts(Long stockId, int page, int size, Long viewerId, String sort) {

        StockBoardVO param = new StockBoardVO();
        param.setStockId(stockId);
        param.setViewerId(viewerId);
        param.setSort("popular".equals(sort) ? "popular" : "latest");
        param.setLimit(size);
        param.setOffset((page - 1) * size);

        return stockBoardDao.selectPostList(param);

    }

    @Override
    public List<StockBoardVO> getRecentPosts(int size) {

        return stockBoardDao.selectRecentPostList(size);

    }

    @Override
    public int countPosts(Long stockId) {

        return stockBoardDao.countPost(stockId);

    }

    @Override
    public StockBoardVO getOpinionCount(Long stockId) {

        return stockBoardDao.selectOpinionCount(stockId);

    }

    @Override
    public void writePost(Long userId, Long stockId, String opinion, String content) {

        if (stockId == null) throw new IllegalArgumentException("종목을 확인해주세요.");

        String text = clean(content, POST_MAX, "글");

        // 의견은 UP / DOWN 만, 그 외 값은 '선택 안 함'으로
        String safeOpinion = (opinion != null && OPINIONS.contains(opinion)) ? opinion : null;

        StockBoardVO param = new StockBoardVO();
        param.setUserId(userId);
        param.setSeconds(POST_INTERVAL_SEC);

        if (stockBoardDao.countRecentPost(param) > 0) {
            throw new IllegalArgumentException("글은 " + POST_INTERVAL_SEC + "초에 한 번만 쓸 수 있어요. 잠시 후 다시 시도해주세요.");
        }

        param.setStockId(stockId);
        param.setOpinion(safeOpinion);
        param.setContent(text);

        if (stockBoardDao.insertPost(param) == 0) {
            throw new IllegalArgumentException("없는 종목입니다.");
        }

        boardLiveHub.postCreated(stockId);

    }

    @Override
    public void deletePost(Long userId, boolean admin, Long postId) {

        StockBoardVO post = (postId == null) ? null : stockBoardDao.selectPost(postId);
        if (post == null) throw new IllegalArgumentException("이미 삭제되었거나 없는 글입니다.");

        if (!admin && !post.getUserId().equals(userId)) {
            throw new SecurityException("본인이 쓴 글만 삭제할 수 있습니다.");
        }

        StockBoardVO param = new StockBoardVO();
        param.setPostId(postId);
        param.setUserId(userId);     // 삭제한 사람

        stockBoardDao.deletePost(param);

        boardLiveHub.deleted(post.getStockId(), postId, true);

    }

    @Override
    public List<StockBoardVO> getComments(Long postId) {

        return stockBoardDao.selectCommentList(postId);

    }

    @Override
    public void writeComment(Long userId, Long postId, String content) {

        if (postId == null) throw new IllegalArgumentException("글을 확인해주세요.");

        String text = clean(content, COMMENT_MAX, "댓글");

        StockBoardVO param = new StockBoardVO();
        param.setUserId(userId);
        param.setSeconds(COMMENT_INTERVAL_SEC);

        if (stockBoardDao.countRecentComment(param) > 0) {
            throw new IllegalArgumentException("댓글은 " + COMMENT_INTERVAL_SEC + "초에 한 번만 쓸 수 있어요. 잠시 후 다시 시도해주세요.");
        }

        param.setPostId(postId);
        param.setContent(text);

        if (stockBoardDao.insertComment(param) == 0) {
            throw new IllegalArgumentException("삭제된 글에는 댓글을 달 수 없습니다.");
        }

        notifyComment(postId, false);

    }

    @Override
    public void deleteComment(Long userId, boolean admin, Long commentId) {

        StockBoardVO comment = (commentId == null) ? null : stockBoardDao.selectComment(commentId);
        if (comment == null) throw new IllegalArgumentException("이미 삭제되었거나 없는 댓글입니다.");

        if (!admin && !comment.getUserId().equals(userId)) {
            throw new SecurityException("본인이 쓴 댓글만 삭제할 수 있습니다.");
        }

        StockBoardVO param = new StockBoardVO();
        param.setCommentId(commentId);
        param.setUserId(userId);     // 삭제한 사람

        stockBoardDao.deleteComment(param);

        notifyComment(comment.getPostId(), true);

    }

    @Override
    public StockBoardVO toggleLike(Long userId, Long postId) {

        StockBoardVO post = (postId == null) ? null : stockBoardDao.selectPost(postId);
        if (post == null) throw new IllegalArgumentException("삭제되었거나 없는 글입니다.");
        if (post.getUserId().equals(userId)) throw new IllegalArgumentException("내 글에는 좋아요를 누를 수 없어요.");

        StockBoardVO param = new StockBoardVO();
        param.setPostId(postId);
        param.setUserId(userId);

        // 이미 눌렀으면 취소, 아니면 누르기 (동시에 두 번 눌러도 PK 때문에 한 번만 들어감)
        boolean liked = stockBoardDao.deleteLike(param) == 0;
        if (liked) stockBoardDao.insertLike(param);

        StockBoardVO result = new StockBoardVO();
        result.setLiked(liked);
        result.setLikeCount(stockBoardDao.countLike(postId));

        return result;

    }

    @Override
    public StockBoardVO report(Long userId, Long postId, String reason, String memo) {

        StockBoardVO post = (postId == null) ? null : stockBoardDao.selectPost(postId);
        if (post == null) throw new IllegalArgumentException("삭제되었거나 없는 글입니다.");
        if (post.getUserId().equals(userId)) throw new IllegalArgumentException("내 글은 신고할 수 없어요.");
        if (reason == null || !REASONS.contains(reason)) throw new IllegalArgumentException("신고 사유를 골라주세요.");

        String text = (memo == null) ? "" : memo.trim();
        if (text.length() > REPORT_MEMO_MAX) throw new IllegalArgumentException("신고 내용은 " + REPORT_MEMO_MAX + "자까지 쓸 수 있습니다.");

        StockBoardVO param = new StockBoardVO();
        param.setPostId(postId);
        param.setUserId(userId);
        param.setReason(reason);
        param.setMemo(text.isEmpty() ? null : text);

        if (stockBoardDao.insertReport(param) == 0) throw new IllegalArgumentException("이미 신고한 글이에요.");

        int open = stockBoardDao.countOpenReport(postId);

        // 딱 기준에 닿은 순간에만 (그 뒤 신고마다 메일이 가지 않게)
        boolean blinded = open == BLIND_AT;

        if (blinded) {

            // 보고 있는 화면들이 목록을 다시 받아서 가림 처리
            boardLiveHub.deleted(post.getStockId(), postId, true);

            // 메일은 시간이 걸리니 따로 (신고 응답은 바로)
            Thread mail = new Thread(() -> opsAlertService.alert("BOARD_REPORT_" + postId,
                    "신고가 쌓여 토론 글이 가려졌어요",
                    "토론 글 #" + postId + "에 신고가 " + BLIND_AT + "개 쌓여 화면에서 가렸어요.\n"
                            + "관리자 화면 → 🚩 신고 관리에서 삭제하거나 \"문제 없음\"으로 되돌려주세요."), "board-report-mail");
            mail.setDaemon(true);
            mail.start();

        }

        StockBoardVO result = new StockBoardVO();
        result.setBlinded(open >= BLIND_AT);
        result.setReportCount(open);

        return result;

    }

    @Override
    public List<StockBoardVO> getReportedPosts() {

        return stockBoardDao.selectReportedPosts();

    }

    @Override
    public int countReportedPosts() {

        return stockBoardDao.countReportedPosts();

    }

    @Override
    public void handleReport(Long adminId, Long postId, String action) {

        StockBoardVO post = (postId == null) ? null : stockBoardDao.selectPostAny(postId);
        if (post == null) throw new IllegalArgumentException("없는 글입니다.");

        StockBoardVO param = new StockBoardVO();
        param.setPostId(postId);
        param.setUserId(adminId);

        if ("DELETE".equals(action)) {

            stockBoardDao.deletePost(param);          // 이미 지워진 글이면 0건 (그래도 신고는 정리)
            param.setStatus("DELETED");

        } else if ("DISMISS".equals(action)) {

            param.setStatus("DISMISSED");             // 신고만 정리 → 가림 풀림

        } else {

            throw new IllegalArgumentException("처리 방법을 확인해주세요.");

        }

        if (stockBoardDao.handleReports(param) == 0) throw new IllegalArgumentException("이미 처리된 신고입니다.");

        boardLiveHub.deleted(post.getStockId(), postId, true);

    }

    // 댓글이 달린 글의 종목을 찾아서 그 종목 화면에 알림 (알림 실패는 댓글 저장과 무관)
    private void notifyComment(Long postId, boolean delete) {

        try {

            StockBoardVO post = stockBoardDao.selectPost(postId);
            if (post == null) return;

            if (delete) boardLiveHub.deleted(post.getStockId(), postId, false);
            else boardLiveHub.commentCreated(post.getStockId(), postId);

        } catch (Exception ignore) {
            // 알림만 빠짐 (화면은 다음 새로고침 때 반영)
        }

    }

    // ============================================
    // 공통
    // ============================================

    // 내용 정리: 앞뒤 공백 제거, 빈 줄 3개 이상은 2개로, 글자 수 확인
    private String clean(String content, int max, String label) {

        String text = (content == null) ? "" : content.strip().replaceAll("(\\r?\\n){3,}", "\n\n");

        if (text.isEmpty()) throw new IllegalArgumentException(label + " 내용을 입력해주세요.");
        if (text.length() > max) throw new IllegalArgumentException(label + "은(는) " + max + "자까지 쓸 수 있습니다.");

        return text;

    }
}