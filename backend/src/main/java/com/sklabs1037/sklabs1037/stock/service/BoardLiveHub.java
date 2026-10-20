package com.sklabs1037.sklabs1037.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 실시간 토론방 (SSE: Server-Sent Events)
 *
 * 왜 WebSocket이 아니라 SSE인지
 *   - 토론방은 "글 쓰기"는 지금처럼 POST로 보내고, 서버 → 화면 방향으로 "새 글 왔어요" 알림만 있으면 됨 (한 방향)
 *   - SSE는 그냥 HTTP 응답을 끊지 않고 조금씩 보내는 방식 → nginx·세션·로그인을 그대로 쓰고, 끊기면 브라우저가 알아서 다시 붙음
 *   - WebSocket은 양방향(채팅·게임)에 맞고, 프로토콜이 달라서 nginx 업그레이드 설정·별도 인증·재접속 코드가 더 필요
 *
 * 구독 단위 (topic)
 *   "stock:{종목ID}"  종목 토론 탭을 보고 있는 화면 → 그 종목 글·댓글·삭제 + 보는 사람 수
 *   "all"            메인 화면 "지금 토론 중" → 모든 종목의 새 글·삭제
 *
 * 보내는 이벤트 (데이터는 "무엇이 바뀌었는지"만, 화면이 목록을 다시 불러옴 → 권한·삭제 버튼 계산은 기존 API 그대로)
 *   hello    { viewers }                 연결 직후
 *   post     { stockId }                 새 글
 *   comment  { stockId, postId }         새 댓글
 *   delete   { stockId, postId }         글·댓글 삭제
 *   viewers  { viewers }                 보는 사람 수가 바뀜
 *   (25초마다 빈 주석 ": ping" → 프록시가 조용한 연결을 끊지 않게 + 끊긴 화면 정리)
 */
@Slf4j
@Component
public class BoardLiveHub {

    // 한 연결 최대 시간 (지나면 브라우저 EventSource가 자동으로 다시 연결)
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    // 서버 전체 동시 연결 상한 (메모리 보호)
    private static final int MAX_CONNECTIONS = 1000;

    // 이 종목 화면을 열어둔 연결들
    private final Map<String, Set<SseEmitter>> topics = new ConcurrentHashMap<>();
    private final AtomicInteger connections = new AtomicInteger();

    // 화면 하나 연결 (상한을 넘으면 null → 컨트롤러가 503)
    public SseEmitter subscribe(Long stockId) {

        if (connections.incrementAndGet() > MAX_CONNECTIONS) {
            connections.decrementAndGet();
            return null;
        }

        String topic = topic(stockId);
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        topics.compute(topic, (k, set) -> {
            Set<SseEmitter> s = (set == null) ? ConcurrentHashMap.newKeySet() : set;
            s.add(emitter);
            return s;
        });

        // 끝나는 경우 셋 다 같은 정리 (정상 종료·시간 초과·오류)
        Runnable cleanup = () -> remove(topic, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        send(topic, emitter, "hello", Map.of("viewers", viewers(topic)));
        if (stockId != null) broadcast(topic, "viewers", Map.of("viewers", viewers(topic)));

        return emitter;

    }

    // 새 글: 그 종목 화면 + 메인 화면
    public void postCreated(Long stockId) {

        Map<String, Object> data = Map.of("stockId", stockId);

        broadcast(topic(stockId), "post", data);
        broadcast(topic(null), "post", data);

    }

    // 새 댓글: 그 종목 화면만 (메인은 댓글 수까지는 실시간 아님)
    public void commentCreated(Long stockId, Long postId) {

        broadcast(topic(stockId), "comment", Map.of("stockId", stockId, "postId", postId));

    }

    // 글·댓글 삭제
    public void deleted(Long stockId, Long postId, boolean wholePost) {

        Map<String, Object> data = Map.of("stockId", stockId, "postId", postId);

        broadcast(topic(stockId), "delete", data);
        if (wholePost) broadcast(topic(null), "delete", data);

    }

    // 이 종목을 보고 있는 화면 수
    public int viewers(Long stockId) {

        return viewers(topic(stockId));

    }

    // 25초마다 ping (조용한 연결을 nginx·통신사가 끊지 않게, 이미 끊긴 화면은 여기서 정리됨)
    @Scheduled(fixedDelay = 25_000, initialDelay = 25_000)
    public void heartbeat() {

        topics.forEach((topic, set) -> {
            for (SseEmitter emitter : set) {
                try {
                    synchronized (emitter) {
                        emitter.send(SseEmitter.event().comment("ping"));
                    }
                } catch (Exception e) {
                    remove(topic, emitter);
                }
            }
        });

    }

    public int totalConnections() {

        return connections.get();

    }

    // ============================================
    // 내부
    // ============================================
    private void broadcast(String topic, String event, Map<String, Object> data) {

        Set<SseEmitter> set = topics.get(topic);
        if (set == null) return;

        for (SseEmitter emitter : set) send(topic, emitter, event, data);

    }

    private void send(String topic, SseEmitter emitter, String event, Map<String, Object> data) {

        try {

            // 같은 연결에 두 스레드가 동시에 쓰면 내용이 섞임 → 연결마다 잠금
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            }

        } catch (Exception e) {

            // 탭을 닫은 화면 → 정리 (오류 로그는 남기지 않음, 흔한 일)
            remove(topic, emitter);

        }

    }

    private void remove(String topic, SseEmitter emitter) {

        // 빼고, 비면 topic 자체를 지움 (subscribe와 같은 compute로 → 동시에 들어와도 안 꼬임)
        boolean[] removed = {false};
        topics.computeIfPresent(topic, (k, set) -> {
            removed[0] = set.remove(emitter);
            return set.isEmpty() ? null : set;
        });

        if (!removed[0]) return;   // 이미 정리됨 (콜백이 여러 번 와도 한 번만)

        connections.decrementAndGet();

        try {
            emitter.complete();
        } catch (Exception ignore) {
            // 이미 끝난 연결
        }

        // 종목 화면이면 남은 사람들에게 바뀐 인원 알림
        if (!"all".equals(topic)) broadcast(topic, "viewers", Map.of("viewers", viewers(topic)));

    }

    private int viewers(String topic) {

        Set<SseEmitter> set = topics.get(topic);

        return set == null ? 0 : set.size();

    }

    private static String topic(Long stockId) {

        return stockId == null ? "all" : "stock:" + stockId;

    }
}
