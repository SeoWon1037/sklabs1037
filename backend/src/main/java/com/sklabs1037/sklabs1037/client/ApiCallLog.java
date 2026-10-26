package com.sklabs1037.sklabs1037.client;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 🔌 외부 연동 기록장 (메모리)
 *
 * 외부 서비스를 부를 때마다 record()로 한 줄 → 큐에 쌓아 두기만 함 (DB에 바로 안 씀)
 *   → ApiCallLogService가 10초마다 drain()으로 꺼내 한꺼번에 저장
 *
 * 왜 바로 DB에 안 쓰나
 *   - 저녁 시세 수집은 토스를 수천 번 부름 → 그때마다 INSERT하면 수집이 느려지고 DB 연결도 그만큼 씀
 *   - 기록이 실패해도 본래 일(시세 수집, AI 답변)은 절대 실패하면 안 됨 → 여기서는 큐에 넣기만, 예외 없음
 *
 * static인 이유
 *   - RestClient는 각 클라이언트가 new로 만들어 필드에 들고 있어서(스프링 빈 아님) 주입받을 수 없음
 *   - 그래서 큐 하나를 프로그램 전체가 같이 씀 (서버 하나에 큐 하나)
 *
 * 안전장치: 저장이 계속 실패해 큐가 MAX_QUEUE를 넘으면 새 기록은 버리고 버린 수만 셈 (메모리가 끝없이 늘지 않게)
 */
public final class ApiCallLog {

    static final int MAX_QUEUE = 50_000;

    // 한 번 부른 기록
    public record Entry(String service, String action, boolean ok, Integer status, long elapsedMs, String error, Instant calledAt) {
    }

    private static final ConcurrentLinkedQueue<Entry> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger SIZE = new AtomicInteger();
    private static final AtomicLong DROPPED = new AtomicLong();

    private ApiCallLog() {
    }

    // 기록 남기기 (어디서 불러도 예외 없음)
    public static void record(String service, String action, boolean ok, Integer status, long elapsedMs, String error) {

        if (SIZE.incrementAndGet() > MAX_QUEUE) {
            SIZE.decrementAndGet();
            DROPPED.incrementAndGet();
            return;
        }

        QUEUE.add(new Entry(cut(service, 20), cut(action == null || action.isBlank() ? "-" : action, 60), ok, status,
                Math.max(0, elapsedMs), ok ? null : cut(error, 300), Instant.now()));

    }

    // 걸린 시간을 재면서 기록 (JDK HttpClient처럼 인터셉터를 못 붙이는 곳용)
    public static long start() {

        return System.nanoTime();

    }

    public static long elapsedMs(long startNanos) {

        return (System.nanoTime() - startNanos) / 1_000_000;

    }

    // 저장할 것 꺼내기 (최대 max개)
    public static List<Entry> drain(int max) {

        List<Entry> out = new ArrayList<>();

        for (int i = 0; i < max; i++) {
            Entry e = QUEUE.poll();
            if (e == null) break;
            SIZE.decrementAndGet();
            out.add(e);
        }

        return out;

    }

    // 지금까지 버린 수 (화면에 경고용)
    public static long dropped() {

        return DROPPED.get();

    }

    // 큐에 남은 수
    public static int pending() {

        return Math.max(0, SIZE.get());

    }

    // 주소 경로 → 기능 이름 ("/api/v1/prices" → "prices", "/v1/messages" → "messages")
    // - ? 뒤 조건(종목코드 등)과 키는 절대 안 남김
    public static String actionOf(String path) {

        if (path == null || path.isBlank() || "/".equals(path)) return "-";

        String p = path;
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);

        p = p.replaceFirst("^/(api/)?v\\d+/", "/").replaceAll("/+$", "");
        if (p.startsWith("/")) p = p.substring(1);

        return p.isEmpty() ? "-" : cut(p, 60);

    }

    // 주소 → 서비스 이름 (카카오·네이버 로그인처럼 한 RestClient가 여러 곳을 부를 때)
    public static String serviceOf(String host) {

        String h = host == null ? "" : host.toLowerCase();

        if (h.contains("kakao")) return "KAKAO";
        if (h.contains("naver")) return "NAVER";
        if (h.contains("tossinvest")) return "TOSS";
        if (h.contains("anthropic")) return "CLAUDE";
        if (h.contains("voyageai")) return "VOYAGE";
        if (h.contains("news.google")) return "NEWS_RSS";
        if (h.contains("frankfurter")) return "FX";
        if (h.contains("github")) return "GITHUB";

        return "OTHER";

    }

    private static String cut(String s, int max) {

        if (s == null) return null;
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;

    }
}
