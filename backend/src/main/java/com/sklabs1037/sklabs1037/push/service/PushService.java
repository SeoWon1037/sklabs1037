package com.sklabs1037.sklabs1037.push.service;

import com.sklabs1037.sklabs1037.client.ApiCallLog;
import com.sklabs1037.sklabs1037.client.RestClients;
import com.sklabs1037.sklabs1037.push.dao.PushDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * 📲 휴대폰·PC 푸시 알림
 *
 * 흐름
 *   1. 화면: 🔔 알림 창의 "휴대폰·PC 알림 켜기" → 브라우저가 허락을 묻고 → 구독 정보(ENDPOINT, 키 2개)를 서버에 저장
 *   2. NotificationService.notify 로 사이트 알림이 새로 생기면 → 그 사람의 기기마다 푸시 (따로 스레드, 원래 일은 안 기다림)
 *   3. 서비스 워커(public/sw.js)가 받아서 알림을 띄우고, 누르면 그 화면으로
 *
 * - 서버 키(VAPID)는 처음 필요할 때 스스로 만들어 DB에 1줄 저장 → .env나 GitHub Secrets에 넣을 필요 없음
 * - 보내는 곳은 브라우저 푸시 서비스 주소만 (구글·모질라·애플·마이크로소프트) → 아무 주소로나 요청을 보내지 않게
 * - 404/410(브라우저에서 알림을 끔·앱 삭제) → 그 기기 지움 / 같은 종류(Topic)는 아직 안 간 알림을 새 것으로 바꿈 (하트 연타)
 * - 내용은 RFC 8291로 암호화 → 푸시 서비스도 내용을 못 읽음
 */
@Slf4j
@Service
public class PushService {

    static final int MAX_DEVICES = 10;
    private static final long TEST_GAP_MS = 20_000;

    static final List<String> PUSH_HOSTS = List.of(
            "fcm.googleapis.com",                   // 크롬·엣지(안드로이드)·삼성 인터넷
            "updates.push.services.mozilla.com",    // 파이어폭스
            "push.apple.com",                       // 사파리 (아이폰은 홈 화면에 추가한 경우)
            "notify.windows.com");                  // 엣지(윈도우)

    @Autowired
    private PushDao pushDao;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${app.frontend-url:https://sklabs1037.com}")
    private String siteUrl;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService sender = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
            r -> {
                Thread t = new Thread(r, "push-sender");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.DiscardPolicy());
    private final Map<Long, Long> lastTest = new ConcurrentHashMap<>();

    private volatile String publicKey;
    private volatile PrivateKey privateKey;

    // ============================================
    // 서버 키
    // ============================================
    public synchronized String publicKey() {

        if (publicKey != null) return publicKey;

        Map<String, Object> row = pushDao.selectKey();
        if (row == null) {
            var kp = WebPushCrypto.generate();
            pushDao.insertKey(WebPushCrypto.b64(WebPushCrypto.rawPublic(kp.getPublic())), WebPushCrypto.b64(WebPushCrypto.rawPrivate(kp.getPrivate())));
            row = pushDao.selectKey();
            log.info("푸시 알림 서버 키를 새로 만들었어요.");
        }

        privateKey = WebPushCrypto.privateFromRaw(WebPushCrypto.unb64(String.valueOf(row.get("privateKey"))));
        publicKey = String.valueOf(row.get("publicKey"));
        return publicKey;

    }

    // ============================================
    // 구독
    // ============================================
    public Map<String, Object> subscribe(Long userId, String endpoint, String p256dh, String auth, String userAgent) {

        String ep = validEndpoint(endpoint);
        byte[] key = decode(p256dh, "p256dh");
        byte[] secret = decode(auth, "auth");
        if (key.length != 65 || key[0] != 4) throw new IllegalArgumentException("알림 키 모양이 달라요.");
        if (secret.length != 16) throw new IllegalArgumentException("알림 키 모양이 달라요.");

        if (pushDao.countByUser(userId) >= MAX_DEVICES && pushDao.selectMyDevices(userId).stream().noneMatch(d -> ep.endsWith(String.valueOf(d.get("tail"))))) {
            throw new IllegalArgumentException("알림은 기기 " + MAX_DEVICES + "개까지 켤 수 있어요. 안 쓰는 기기에서 먼저 꺼주세요.");
        }

        pushDao.upsertSub(userId, ep, WebPushCrypto.b64(key), WebPushCrypto.b64(secret), device(userAgent));
        return Map.of("devices", pushDao.countByUser(userId));

    }

    public void unsubscribe(Long userId, String endpoint) {

        if (endpoint != null) pushDao.deleteSub(userId, endpoint.strip());

    }

    public List<Map<String, Object>> devices(Long userId) {

        return pushDao.selectMyDevices(userId);

    }

    public int sendTest(Long userId) {

        long now = System.currentTimeMillis();
        Long last = lastTest.put(userId, now);
        if (last != null && now - last < TEST_GAP_MS) throw new IllegalArgumentException("테스트 알림은 20초에 한 번 보낼 수 있어요.");

        List<Map<String, Object>> subs = pushDao.selectSubs(userId);
        if (subs.isEmpty()) throw new IllegalArgumentException("알림을 켠 기기가 없어요.");

        send(userId, "TEST", "📲 알림이 잘 와요!", "앞으로 하트·대결 결과·가격 알림이 여기로 와요", "/");
        return subs.size();

    }

    // ============================================
    // 보내기 (NotificationService에서, 따로 스레드)
    // ============================================
    public void send(Long userId, String kind, String title, String body, String link) {

        if (userId == null) return;
        sender.execute(() -> {
            try {
                sendNow(userId, kind, title, body, link);
            } catch (Exception e) {
                log.warn("푸시 보내기 실패 (user {}): {}", userId, e.getMessage());
            }
        });

    }

    void sendNow(Long userId, String kind, String title, String body, String link) {

        List<Map<String, Object>> subs = pushDao.selectSubs(userId);
        if (subs.isEmpty()) return;

        publicKey();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("title", title);
        msg.put("body", body == null ? "" : body);
        msg.put("link", link == null || !link.startsWith("/") ? "/" : link);
        msg.put("tag", kind);
        byte[] plain = jsonMapper.writeValueAsString(msg).getBytes(StandardCharsets.UTF_8);

        for (Map<String, Object> s : subs) {

            Long id = ((Number) s.get("id")).longValue();
            String endpoint = String.valueOf(s.get("endpoint"));

            try {

                URI uri = URI.create(endpoint);
                String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
                String jwt = WebPushCrypto.vapidJwt(audience, siteUrl.startsWith("https://") ? siteUrl : "https://sklabs1037.com",
                        System.currentTimeMillis() / 1000 + 12 * 3600, privateKey);
                byte[] payload = WebPushCrypto.encrypt(plain, WebPushCrypto.unb64(String.valueOf(s.get("p256dh"))), WebPushCrypto.unb64(String.valueOf(s.get("auth"))));

                HttpRequest req = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(15))
                        .header("TTL", "86400")
                        .header("Urgency", "normal")
                        .header("Topic", topic(kind))
                        .header("Content-Encoding", "aes128gcm")
                        .header("Content-Type", "application/octet-stream")
                        .header("Authorization", "vapid t=" + jwt + ", k=" + publicKey)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                        .build();

                // 🔌 연동 기록 (구독 주소는 기기마다 달라서 저장 안 함, 어느 회사 푸시 서버인지만)
                String action = pushVendor(uri.getHost());
                long start = ApiCallLog.start();
                int code;

                try {
                    code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
                } catch (java.io.IOException | InterruptedException e) {
                    ApiCallLog.record("PUSH", action, false, null, ApiCallLog.elapsedMs(start), RestClients.errorText(e));
                    throw e;
                }

                // 404·410 = 사용자가 알림을 끈 것 (우리 쪽 잘못 아님) → 성공으로 침
                boolean ok = (code >= 200 && code < 300) || code == 404 || code == 410;
                ApiCallLog.record("PUSH", action, ok, code, ApiCallLog.elapsedMs(start), ok ? null : "HTTP " + code);

                if (code >= 200 && code < 300) {
                    pushDao.markOk(id);
                } else if (code == 404 || code == 410) {
                    pushDao.deleteByEndpoint(endpoint);   // 브라우저에서 알림을 껐거나 앱을 지움
                } else {
                    pushDao.markFail(id);
                    log.warn("푸시 응답 {} (sub {})", code, id);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                pushDao.markFail(id);
                log.warn("푸시 실패 (sub {}): {}", id, e.getMessage());
            }

        }

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================
    static String validEndpoint(String endpoint) {

        String ep = endpoint == null ? "" : endpoint.strip();
        if (ep.length() > 1000) throw new IllegalArgumentException("알림 주소가 너무 길어요.");

        URI uri;
        try {
            uri = URI.create(ep);
        } catch (Exception e) {
            throw new IllegalArgumentException("알림 주소를 확인해주세요.");
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean ok = "https".equals(uri.getScheme()) && PUSH_HOSTS.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
        if (!ok) throw new IllegalArgumentException("지원하지 않는 알림 주소예요.");
        return ep;

    }

    // Topic: 영문·숫자·-_ 32자까지 (같은 Topic은 안 간 알림을 새 것으로 바꿈)
    static String topic(String kind) {

        String t = (kind == null ? "sklabs" : kind).replaceAll("[^A-Za-z0-9_-]", "");
        if (t.isEmpty()) t = "sklabs";
        return t.length() > 32 ? t.substring(0, 32) : t;

    }

    static String device(String ua) {

        String u = ua == null ? "" : ua;
        String os = u.contains("iPhone") ? "iPhone" : u.contains("iPad") ? "iPad" : u.contains("Android") ? "Android"
                : u.contains("Windows") ? "Windows" : u.contains("Mac OS") ? "Mac" : u.contains("Linux") ? "Linux" : "기기";
        String br = u.contains("SamsungBrowser") ? "삼성 인터넷" : u.contains("Edg/") ? "Edge" : u.contains("Firefox") ? "Firefox"
                : u.contains("Chrome") ? "Chrome" : u.contains("Safari") ? "Safari" : "브라우저";
        return os + " · " + br;

    }

    private static byte[] decode(String s, String what) {

        try {
            return WebPushCrypto.unb64(s == null ? "" : s);
        } catch (Exception e) {
            throw new IllegalArgumentException("알림 키(" + what + ")를 읽지 못했어요.");
        }

    }

    // 푸시 서버 → 이름 (크롬·엣지 = FCM, 파이어폭스 = Mozilla, 아이폰·맥 사파리 = Apple)
    static String pushVendor(String host) {

        String h = host == null ? "" : host.toLowerCase();
        if (h.contains("googleapis")) return "fcm";
        if (h.contains("mozilla")) return "mozilla";
        if (h.contains("apple")) return "apple";
        if (h.contains("windows") || h.contains("microsoft")) return "wns";
        return "other";

    }
}
