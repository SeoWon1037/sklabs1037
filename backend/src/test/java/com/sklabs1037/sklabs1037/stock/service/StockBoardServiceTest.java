package com.sklabs1037.sklabs1037.stock.service;

import com.sklabs1037.sklabs1037.ops.service.OpsAlertService;
import com.sklabs1037.sklabs1037.stock.dao.StockBoardDao;
import com.sklabs1037.sklabs1037.stock.vo.StockBoardVO;
import com.sklabs1037.sklabs1037.support.Fakes;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 토론방 좋아요·신고 규칙
 * - 내 글에는 좋아요·신고 불가, 좋아요는 다시 누르면 취소
 * - 같은 사람 중복 신고 불가, 신고가 3개가 되는 순간에만 "가려짐"으로 알림
 * - 관리자 처리: DELETE는 글 삭제 + 신고 정리, DISMISS는 신고만 정리
 */
class StockBoardServiceTest {

    private static final long WRITER = 1L;
    private static final long OTHER = 2L;

    // 글 1개(작성자 WRITER) + 좋아요/신고를 메모리에 들고 있는 가짜 DAO
    private final Set<Long> likes = new HashSet<>();
    private final Set<Long> reporters = new HashSet<>();
    private final AtomicInteger openReports = new AtomicInteger();
    private final List<Fakes.Call> calls = new ArrayList<>();

    private StockBoardServiceImpl service() {

        StockBoardVO post = new StockBoardVO();
        post.setPostId(10L);
        post.setStockId(5L);
        post.setUserId(WRITER);

        Map<String, Function<Object[], Object>> b = new HashMap<>();
        b.put("selectPost", a -> post);
        b.put("selectPostAny", a -> post);
        b.put("deleteLike", a -> likes.remove(((StockBoardVO) a[0]).getUserId()) ? 1 : 0);
        b.put("insertLike", a -> likes.add(((StockBoardVO) a[0]).getUserId()) ? 1 : 0);
        b.put("countLike", a -> likes.size());
        b.put("insertReport", a -> {
            boolean added = reporters.add(((StockBoardVO) a[0]).getUserId());
            if (added) openReports.incrementAndGet();
            return added ? 1 : 0;
        });
        b.put("countOpenReport", a -> openReports.get());
        b.put("handleReports", a -> {
            int n = openReports.getAndSet(0);
            return n;
        });

        StockBoardServiceImpl s = new StockBoardServiceImpl();
        Fakes.set(s, "stockBoardDao", Fakes.fake(StockBoardDao.class, b, calls));
        Fakes.set(s, "boardLiveHub", new BoardLiveHub());
        Fakes.set(s, "opsAlertService", new OpsAlertService());   // 설정이 없어서 메일은 실제로 안 나감

        return s;

    }

    @Test
    void 좋아요는_다시_누르면_취소되고_내_글에는_못_누른다() {

        StockBoardServiceImpl s = service();

        StockBoardVO first = s.toggleLike(OTHER, 10L);
        assertTrue(first.getLiked());
        assertEquals(1, (int) first.getLikeCount());

        StockBoardVO second = s.toggleLike(OTHER, 10L);
        assertFalse(second.getLiked());
        assertEquals(0, (int) second.getLikeCount());

        assertThrows(IllegalArgumentException.class, () -> s.toggleLike(WRITER, 10L));

    }

    @Test
    void 신고는_사유가_필요하고_중복_신고와_내_글_신고는_막는다() {

        StockBoardServiceImpl s = service();

        assertThrows(IllegalArgumentException.class, () -> s.report(OTHER, 10L, null, null));
        assertThrows(IllegalArgumentException.class, () -> s.report(OTHER, 10L, "HACK", null));
        assertThrows(IllegalArgumentException.class, () -> s.report(WRITER, 10L, "SPAM", null));

        s.report(OTHER, 10L, "SPAM", "  ");
        assertThrows(IllegalArgumentException.class, () -> s.report(OTHER, 10L, "AD", null));

    }

    @Test
    void 신고가_3개가_되면_가려진다() {

        StockBoardServiceImpl s = service();

        assertFalse(s.report(2L, 10L, "SPAM", null).getBlinded());
        assertFalse(s.report(3L, 10L, "AD", null).getBlinded());
        assertTrue(s.report(4L, 10L, "ABUSE", "욕설").getBlinded());
        assertTrue(s.report(5L, 10L, "ETC", null).getBlinded());   // 이후 신고도 가려진 상태 유지

    }

    @Test
    void 관리자_처리_삭제는_글도_지우고_문제없음은_신고만_정리() {

        StockBoardServiceImpl s = service();

        s.report(2L, 10L, "SPAM", null);
        s.handleReport(99L, 10L, "DISMISS");
        assertEquals(0, Fakes.count(calls, "deletePost"));
        assertEquals(0, openReports.get());

        s.report(3L, 10L, "AD", null);
        s.handleReport(99L, 10L, "DELETE");
        assertEquals(1, Fakes.count(calls, "deletePost"));

        // 처리할 신고가 없으면 거절, 모르는 처리 방법도 거절
        assertThrows(IllegalArgumentException.class, () -> s.handleReport(99L, 10L, "DISMISS"));
        assertThrows(IllegalArgumentException.class, () -> s.handleReport(99L, 10L, "BAN"));

    }
}
