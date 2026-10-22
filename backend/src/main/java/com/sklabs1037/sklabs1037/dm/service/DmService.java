package com.sklabs1037.sklabs1037.dm.service;

import com.sklabs1037.sklabs1037.dm.dao.DmDao;
import com.sklabs1037.sklabs1037.me.dao.MeDao;
import com.sklabs1037.sklabs1037.push.service.PushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 💬 1:1 메시지
 *
 * - 아무 회원에게나 (닉네임으로 찾기) / 탈퇴·AI 계정에는 안 됨
 * - 실시간: DmLiveHub(SSE) → 메시지 화면을 열어둔 사람에게 바로
 *   화면을 안 보고 있으면 📲 푸시 (같은 방 알림은 하나로 바뀜, Topic = DM{방})
 * - 읽음: 방마다 각자 "어디까지 읽었는지(메시지 ID)" → 내 메시지 옆 "1"이 사라짐
 * - 안전: 차단(서로 못 보냄), 0.7초에 1개 · 하루 500개, 1,000자, 대화 상대만 방을 볼 수 있음
 * - 🔔 알림 목록에는 안 쌓음 (대화마다 쌓이면 다른 알림이 묻힘) → 상단 💬 안 읽은 수로
 */
@Service
public class DmService {

    static final int MAX_BODY = 1000;
    static final int DAILY_LIMIT = 500;
    static final long GAP_MS = 700;
    static final int PAGE = 40;

    @Autowired
    private DmDao dmDao;

    @Autowired
    private DmLiveHub hub;

    @Autowired
    private PushService pushService;

    @Autowired
    private MeDao meDao;

    private final Map<Long, Long> lastSent = new ConcurrentHashMap<>();

    // ============================================
    // 찾기 · 방
    // ============================================
    public List<Map<String, Object>> search(Long me, String q) {

        String s = q == null ? "" : q.strip();
        if (s.isEmpty()) return List.of();
        if (s.length() > 20) s = s.substring(0, 20);
        return dmDao.searchUsers(s.replace("\\", "").replace("%", "").replace("_", "\\_"), me, 10);

    }

    public Map<String, Object> open(Long me, Long otherId) {

        if (otherId == null || otherId.equals(me)) throw new IllegalArgumentException("나에게는 보낼 수 없어요.");
        Map<String, Object> other = dmDao.selectUser(otherId);
        if (other == null || !Boolean.TRUE.equals(other.get("active"))) throw new IllegalArgumentException("메시지를 보낼 수 없는 회원이에요.");

        long[] p = pair(me, otherId);
        Long roomId = dmDao.upsertRoom(p[0], p[1]);
        return Map.of("roomId", roomId);

    }

    public Map<String, Object> rooms(Long me) {

        return Map.of("rooms", dmDao.selectRooms(me, 50), "unread", dmDao.countUnread(me));

    }

    public int unread(Long me) {

        return dmDao.countUnread(me);

    }

    // ============================================
    // 메시지
    // ============================================
    public Map<String, Object> messages(Long me, Long roomId, Long before) {

        Map<String, Object> room = room(me, roomId);
        boolean isA = me.equals(num(room.get("userA")));
        Long partnerId = isA ? num(room.get("userB")) : num(room.get("userA"));

        List<Map<String, Object>> list = new ArrayList<>(dmDao.selectMessages(roomId, before, PAGE));
        Collections.reverse(list);   // 오래된 → 최신
        for (Map<String, Object> m : list) m.put("mine", me.equals(num(m.get("senderId"))));

        Map<String, Object> partner = dmDao.selectUser(partnerId);
        boolean blockedByMe = dmDao.countBlocked(me, partnerId) > 0;
        boolean blocked = dmDao.countBlockBetween(me, partnerId) > 0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roomId", roomId);
        out.put("partner", Map.of("id", partnerId, "nickname", partner == null ? "(알 수 없음)" : String.valueOf(partner.get("nickname"))));
        out.put("partnerRead", num(room.get(isA ? "readB" : "readA")));
        out.put("messages", list);
        out.put("hasMore", list.size() == PAGE);
        out.put("blockedByMe", blockedByMe);
        out.put("canSend", !blocked && partner != null && Boolean.TRUE.equals(partner.get("active")));
        return out;

    }

    public Map<String, Object> send(Long me, Long roomId, String body) {

        String text = cleanBody(body);
        Map<String, Object> room = room(me, roomId);
        boolean isA = me.equals(num(room.get("userA")));
        Long partnerId = isA ? num(room.get("userB")) : num(room.get("userA"));

        long now = System.currentTimeMillis();
        Long last = lastSent.put(me, now);
        if (last != null && now - last < GAP_MS) throw new IllegalArgumentException("조금 천천히 보내주세요.");
        if (dmDao.countSentToday(me) >= DAILY_LIMIT) throw new IllegalArgumentException("메시지는 하루 " + DAILY_LIMIT + "개까지 보낼 수 있어요.");
        if (dmDao.countBlockBetween(me, partnerId) > 0) throw new IllegalArgumentException("메시지를 보낼 수 없는 상대예요.");

        Map<String, Object> partner = dmDao.selectUser(partnerId);
        if (partner == null || !Boolean.TRUE.equals(partner.get("active"))) throw new IllegalArgumentException("메시지를 보낼 수 없는 회원이에요.");

        Map<String, Object> msg = new HashMap<>();
        msg.put("roomId", roomId);
        msg.put("senderId", me);
        msg.put("body", text);
        dmDao.insertMessage(msg);
        Long msgId = num(msg.get("id"));
        dmDao.touchRoom(roomId, msgId, isA);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", msgId);
        view.put("senderId", me);
        view.put("body", text);
        view.put("at", now);

        hub.send(me, "msg", Map.of("roomId", roomId, "message", view));
        hub.send(partnerId, "msg", Map.of("roomId", roomId, "message", view));

        // 메시지 화면을 안 보고 있으면 📲 푸시
        if (!hub.online(partnerId)) {
            String name = Objects.toString(meDao.selectNickname(me), "회원");
            pushService.send(partnerId, "DM" + roomId, "💬 " + name, preview(text), "/messages?room=" + roomId);
        }

        return Map.of("message", view);

    }

    public Map<String, Object> read(Long me, Long roomId, Long msgId) {

        if (msgId == null) return Map.of();
        Map<String, Object> room = room(me, roomId);
        boolean isA = me.equals(num(room.get("userA")));
        dmDao.markRead(roomId, isA, msgId);

        Long partnerId = isA ? num(room.get("userB")) : num(room.get("userA"));
        Map<String, Object> after = dmDao.selectRoom(roomId);
        Long readId = num(after.get(isA ? "readA" : "readB"));
        hub.send(partnerId, "read", Map.of("roomId", roomId, "readId", readId));
        return Map.of("unread", dmDao.countUnread(me));

    }

    public void block(Long me, Long roomId, boolean on) {

        Map<String, Object> room = room(me, roomId);
        Long partnerId = me.equals(num(room.get("userA"))) ? num(room.get("userB")) : num(room.get("userA"));
        if (on) dmDao.insertBlock(me, partnerId);
        else dmDao.deleteBlock(me, partnerId);

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================
    static long[] pair(long x, long y) {

        return x < y ? new long[]{x, y} : new long[]{y, x};

    }

    static String cleanBody(String body) {

        String t = body == null ? "" : body.replace("\r\n", "\n").strip().replaceAll("\n{4,}", "\n\n\n");
        if (t.isEmpty()) throw new IllegalArgumentException("메시지를 입력해주세요.");
        if (t.length() > MAX_BODY) throw new IllegalArgumentException("메시지는 " + MAX_BODY + "자까지 보낼 수 있어요.");
        return t;

    }

    static String preview(String text) {

        String one = text.replaceAll("\\s+", " ");
        return one.length() > 80 ? one.substring(0, 79) + "…" : one;

    }

    // 이 방의 두 사람만
    private Map<String, Object> room(Long me, Long roomId) {

        Map<String, Object> room = roomId == null ? null : dmDao.selectRoom(roomId);
        if (room == null || !(me.equals(num(room.get("userA"))) || me.equals(num(room.get("userB"))))) {
            throw new IllegalArgumentException("대화방을 찾을 수 없어요.");
        }
        return room;

    }

    private static Long num(Object o) {

        return o instanceof Number n ? n.longValue() : null;

    }
}
