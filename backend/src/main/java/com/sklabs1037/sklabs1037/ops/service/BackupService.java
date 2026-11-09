package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.ops.service.JobRunService.JobOutcome;
import com.sklabs1037.sklabs1037.support.dao.InquiryDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 💾 DB 백업 상태 (백업 자체는 docker-compose의 backup 컨테이너가 함: ops/backup/loop.sh)
 *
 * - backup 컨테이너와 같은 폴더(/backups)를 같이 씀
 *     sklabs-YYYYMMDD-HHMM.dump  백업 파일 (최근 7개)
 *     last.json                  마지막 시도 결과 { ok, file, at(초), seconds }
 *     last-error.txt             마지막 실패 이유
 *     run-now                    관리자 [지금 백업] → 이 파일을 만들면 30초 안에 시작
 * - 상태: OK(26시간 안에 성공) / STALE(오래됨) / FAILED(마지막 시도 실패) / NONE(아직 없음) / OFF(폴더 없음 = 로컬 PC)
 * - 매일 06:00 작업 BACKUP_CHECK: OK가 아니면 관리자 메일 + 📲
 */
@Slf4j
@Service
public class BackupService {

    static final long FRESH_MS = 26L * 3600 * 1000;

    @Value("${backup.dir:/backups}")
    private String dir;

    @Autowired
    private OpsAlertService opsAlertService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private InquiryDao inquiryDao;

    @Autowired
    private JsonMapper jsonMapper;

    // ============================================
    // 관리자 화면 (🖥 서버 상태)
    // ============================================
    public Map<String, Object> status() {

        Path root = Path.of(dir);
        Map<String, Object> m = new LinkedHashMap<>();
        if (!Files.isDirectory(root)) {
            m.put("status", "OFF");
            return m;
        }

        List<Map<String, Object>> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(root)) {
            s.filter(p -> p.getFileName().toString().matches("sklabs-\\d{8}-\\d{4}\\.dump")).forEach(p -> {
                try {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("name", p.getFileName().toString());
                    f.put("bytes", Files.size(p));
                    f.put("at", Files.getLastModifiedTime(p).toMillis());
                    files.add(f);
                } catch (IOException ignored) {
                    // 지우는 중인 파일
                }
            });
        } catch (IOException e) {
            log.warn("백업 폴더 읽기 실패: {}", e.getMessage());
        }
        files.sort(Comparator.comparingLong((Map<String, Object> f) -> (Long) f.get("at")).reversed());

        Map<String, Object> last = readLast(root);
        Long newest = files.isEmpty() ? null : (Long) files.get(0).get("at");
        Boolean lastOk = last.get("ok") instanceof Boolean b ? b : null;
        Long lastAt = last.get("at") instanceof Number n ? n.longValue() * 1000 : null;

        m.put("status", status(newest, lastOk, lastAt, System.currentTimeMillis()));
        m.put("files", files);
        m.put("totalBytes", files.stream().mapToLong(f -> (Long) f.get("bytes")).sum());
        m.put("lastAt", lastAt);
        m.put("lastSeconds", last.get("seconds"));
        m.put("lastError", Boolean.FALSE.equals(lastOk) ? readError(root) : null);
        m.put("running", Files.exists(root.resolve("run-now")));
        return m;

    }

    public void runNow() throws IOException {

        Path root = Path.of(dir);
        if (!Files.isDirectory(root)) throw new IllegalStateException("백업 폴더가 없어요. (운영 서버에서만 돼요)");
        Files.writeString(root.resolve("run-now"), String.valueOf(System.currentTimeMillis()));

    }

    // ============================================
    // 매일 06:00 (작업 BACKUP_CHECK)
    // ============================================
    public JobOutcome check() {

        Map<String, Object> s = status();
        String st = String.valueOf(s.get("status"));
        if ("OFF".equals(st)) return JobOutcome.of("백업 폴더 없음 (로컬 PC) → 건너뜀");
        if ("OK".equals(st)) return JobOutcome.of(1, 0, "정상 · 백업 " + ((List<?>) s.get("files")).size() + "개");

        String why = switch (st) {
            case "FAILED" -> "마지막 백업이 실패했어요: " + s.get("lastError");
            case "STALE" -> "26시간 넘게 새 백업이 없어요. backup 컨테이너가 멈췄는지 확인해주세요.";
            default -> "아직 백업 파일이 하나도 없어요.";
        };
        opsAlertService.alert("DB_BACKUP", "💾 DB 백업 확인 필요", why + "\n\nEC2에서: docker compose logs --tail 50 backup");
        for (Long admin : inquiryDao.selectAdminIds()) {
            notificationService.notify(admin, "ADMIN", "💾 DB 백업 확인 필요", why, "/admin/server", "BACKUP:" + java.time.LocalDate.now());
        }
        return JobOutcome.of(1, 1, st + " · " + why);

    }

    // 상태 판정 (순수 계산, 테스트 가능)
    static String status(Long newestAt, Boolean lastOk, Long lastAt, long now) {

        if (Boolean.FALSE.equals(lastOk) && lastAt != null && (newestAt == null || lastAt > newestAt)) return "FAILED";
        if (newestAt == null) return "NONE";
        return now - newestAt <= FRESH_MS ? "OK" : "STALE";

    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readLast(Path root) {

        try {
            Path p = root.resolve("last.json");
            if (!Files.exists(p)) return Map.of();
            return jsonMapper.readValue(Files.readString(p, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            return Map.of();
        }

    }

    private String readError(Path root) {

        try {
            String t = Files.readString(root.resolve("last-error.txt"), StandardCharsets.UTF_8).strip();
            // 비밀번호가 섞일 일은 없지만 혹시 몰라 password= 뒤는 가림
            t = t.replaceAll("(?i)(password[=:]\\s*)\\S+", "$1***");
            return t.length() > 300 ? t.substring(0, 300) : t;
        } catch (Exception e) {
            return null;
        }

    }
}
