package com.sklabs1037.sklabs1037.dm.service;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 💬 메시지 실시간 전달 (SSE, 토론방 BoardLiveHub와 같은 방식)
 *
 * - 사람마다 연결 묶음 (휴대폰·PC 둘 다 열어도 둘 다 받음)
 * - 이벤트: msg { roomId, message } / read { roomId, readId }
 * - 연결이 하나도 없는 사람 = 메시지 화면을 안 보고 있음 → DmService가 📲 푸시로 대신 알림
 * - 25초마다 ping (끊긴 화면 정리)
 */
@Component
public class DmLiveHub {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private static final int MAX_CONNECTIONS = 500;

    private final Map<Long, Set<SseEmitter>> users = new ConcurrentHashMap<>();
    private final AtomicInteger connections = new AtomicInteger();

    public SseEmitter subscribe(Long userId) {

        if (connections.incrementAndGet() > MAX_CONNECTIONS) {
            connections.decrementAndGet();
            return null;
        }

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        users.compute(userId, (k, set) -> {
            Set<SseEmitter> s = set == null ? ConcurrentHashMap.newKeySet() : set;
            s.add(emitter);
            return s;
        });

        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        send(userId, emitter, "hello", Map.of("ok", true));
        return emitter;

    }

    // 🖥 서버 상태 화면용
    public int totalConnections() {

        return connections.get();

    }

    public boolean online(Long userId) {

        Set<SseEmitter> set = users.get(userId);
        return set != null && !set.isEmpty();

    }

    public void send(Long userId, String event, Map<String, Object> data) {

        Set<SseEmitter> set = users.get(userId);
        if (set == null) return;
        for (SseEmitter e : set) send(userId, e, event, data);

    }

    @Scheduled(fixedDelay = 25_000, initialDelay = 25_000)
    public void heartbeat() {

        users.forEach((userId, set) -> {
            for (SseEmitter e : set) {
                try {
                    synchronized (e) {
                        e.send(SseEmitter.event().comment("ping"));
                    }
                } catch (Exception ex) {
                    remove(userId, e);
                }
            }
        });

    }

    private void send(Long userId, SseEmitter emitter, String event, Map<String, Object> data) {

        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            }
        } catch (Exception e) {
            remove(userId, emitter);
        }

    }

    private void remove(Long userId, SseEmitter emitter) {

        boolean[] removed = {false};
        users.computeIfPresent(userId, (k, set) -> {
            removed[0] = set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
        if (!removed[0]) return;

        connections.decrementAndGet();
        try {
            emitter.complete();
        } catch (Exception ignore) {
            // 이미 끝난 연결
        }

    }
}
