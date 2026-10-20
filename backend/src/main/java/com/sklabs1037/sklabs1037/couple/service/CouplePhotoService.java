package com.sklabs1037.sklabs1037.couple.service;

import com.sklabs1037.sklabs1037.couple.dao.CoupleDao;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import com.sklabs1037.sklabs1037.mock.service.MockMarketHours;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 📸 커플 기념일 앨범
 *
 * - 지난 기념일(100일 단위 · 주년)마다 사진 칸이 열림, 아무 때나 올리는 "추억" 칸도 있음
 * - 다음 기념일은 🔒 (그날이 되면 열림)
 * - 사진은 화면에서 긴 변 1600px JPEG로 줄여서 보냄 → 서버는 그림 파일인지(JPEG/PNG/WebP) · 3MB 이하인지만 확인
 * - 두 사람만 볼 수 있음 (그림 주소도 로그인 + 같은 커플인지 확인), 한 칸에 6장 · 커플당 200장
 * - 올리면 상대에게 🔔 알림, 지우기는 둘 다 가능
 */
@Slf4j
@Service
public class CouplePhotoService {

    static final String FREE = "추억";
    static final int MAX_BYTES = 3 * 1024 * 1024;
    static final int PER_SLOT = 6;
    static final int PER_COUPLE = 200;

    @Autowired
    private CoupleDao coupleDao;

    @Autowired
    private NotificationService notificationService;

    // 앨범: 지난 기념일 칸 (최근 것부터) + 추억 + 다음 기념일(잠김)
    public Map<String, Object> album(Long userId) {

        Map<String, Object> active = active(userId);
        Long coupleId = num(active.get("id"));
        LocalDate start = LocalDate.parse(String.valueOf(active.get("startDate")));
        LocalDate today = MockMarketHours.now().toLocalDate();

        Map<String, List<Map<String, Object>>> byLabel = new HashMap<>();
        for (Map<String, Object> p : coupleDao.selectPhotos(coupleId)) {
            Map<String, Object> row = new LinkedHashMap<>(p);
            row.put("mine", userId.equals(num(p.get("uploaderId"))));
            row.remove("uploaderId");
            row.put("src", "/api/couple/photos/" + p.get("id") + "/image");
            byLabel.computeIfAbsent(String.valueOf(p.get("milestone")), k -> new ArrayList<>()).add(row);
        }

        List<Map<String, Object>> slots = new ArrayList<>();
        List<Map<String, Object>> passed = new ArrayList<>(milestones(start, today));
        Collections.reverse(passed);   // 최근 기념일부터
        for (Map<String, Object> m : passed) {
            Map<String, Object> slot = new LinkedHashMap<>(m);
            slot.put("photos", byLabel.getOrDefault(String.valueOf(m.get("label")), List.of()));
            slots.add(slot);
        }

        Map<String, Object> free = new LinkedHashMap<>();
        free.put("label", FREE);
        free.put("date", null);
        free.put("photos", byLabel.getOrDefault(FREE, List.of()));
        slots.add(free);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slots", slots);
        result.put("next", CoupleService.nextMilestone(start, today));
        result.put("perSlot", PER_SLOT);
        return result;

    }

    public Map<String, Object> upload(Long userId, String milestone, String caption, String image) {

        Map<String, Object> active = active(userId);
        Long coupleId = num(active.get("id"));
        LocalDate start = LocalDate.parse(String.valueOf(active.get("startDate")));
        LocalDate today = MockMarketHours.now().toLocalDate();

        String label = milestone == null ? "" : milestone.strip();
        boolean open = FREE.equals(label) || milestones(start, today).stream().anyMatch(m -> label.equals(m.get("label")));
        if (!open) throw new IllegalArgumentException("아직 열리지 않은 기념일이에요.");

        if (coupleDao.countPhotos(coupleId, label) >= PER_SLOT) throw new IllegalArgumentException(label + " 칸에는 사진을 " + PER_SLOT + "장까지 올릴 수 있어요.");
        if (coupleDao.countPhotos(coupleId, null) >= PER_COUPLE) throw new IllegalArgumentException("앨범이 가득 찼어요. (" + PER_COUPLE + "장)");

        byte[] data = decode(image);
        String type = imageType(data);
        if (type == null) throw new IllegalArgumentException("JPG · PNG · WebP 사진만 올릴 수 있어요.");
        if (data.length > MAX_BYTES) throw new IllegalArgumentException("사진이 너무 커요. (3MB까지)");

        String cap = caption == null ? "" : caption.strip();
        if (cap.length() > 100) cap = cap.substring(0, 100);

        Map<String, Object> photo = new HashMap<>();
        photo.put("coupleId", coupleId);
        photo.put("uploaderId", userId);
        photo.put("milestone", label);
        photo.put("caption", cap.isEmpty() ? null : cap);
        photo.put("contentType", type);
        photo.put("bytes", data.length);
        photo.put("data", data);
        coupleDao.insertPhoto(photo);

        Map<String, Object> c = coupleDao.selectCouple(coupleId);
        Long partnerId = userId.equals(num(c.get("requesterId"))) ? num(c.get("partnerId")) : num(c.get("requesterId"));
        notificationService.notify(partnerId, "COUPLE", "📷 " + active.get("myName") + "님이 " + label + " 사진을 올렸어요",
                cap.isEmpty() ? "우리의 기념일 앨범에서 확인해 보세요" : cap, "/couple");

        return album(userId);

    }

    public Map<String, Object> delete(Long userId, Long photoId) {

        Long coupleId = num(active(userId).get("id"));
        if (coupleDao.deletePhoto(photoId, coupleId) == 0) throw new IllegalArgumentException("지울 수 없는 사진이에요.");
        return album(userId);

    }

    // 그림 데이터 (같은 커플만) → null이면 볼 수 없음
    public Map<String, Object> image(Long userId, Long photoId) {

        Map<String, Object> active = coupleDao.selectActive(userId);
        if (active == null) return null;

        Map<String, Object> p = coupleDao.selectPhotoData(photoId);
        if (p == null || !num(active.get("id")).equals(num(p.get("coupleId")))) return null;
        return p;

    }

    // ============================================
    // 순수 계산 (테스트 가능)
    // ============================================

    // 시작일부터 오늘까지 지난 기념일 (날짜 순): 100일 단위 + 주년
    static List<Map<String, Object>> milestones(LocalDate start, LocalDate today) {

        TreeMap<LocalDate, String> map = new TreeMap<>();

        for (long n = 100; ; n += 100) {
            LocalDate d = start.plusDays(n - 1);
            if (d.isAfter(today)) break;
            map.put(d, n + "일");
        }

        for (int y = 1; ; y++) {
            LocalDate d = start.plusYears(y);
            if (d.isAfter(today)) break;
            map.merge(d, y + "주년", (a, b) -> b);   // 같은 날이면 주년으로
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<LocalDate, String> e : map.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", e.getValue());
            m.put("date", e.getKey().toString());
            m.put("daysAgo", ChronoUnit.DAYS.between(e.getKey(), today));
            list.add(m);
        }
        return list;

    }

    // 파일 맨 앞 몇 바이트로 진짜 그림인지
    static String imageType(byte[] b) {

        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;

    }

    // "data:image/jpeg;base64,...." 또는 base64만
    static byte[] decode(String image) {

        if (image == null || image.isBlank()) throw new IllegalArgumentException("사진을 골라주세요.");
        String b64 = image.contains(",") ? image.substring(image.indexOf(',') + 1) : image;
        if (b64.length() > MAX_BYTES / 3 * 4 + 16) throw new IllegalArgumentException("사진이 너무 커요. (3MB까지)");
        try {
            return Base64.getDecoder().decode(b64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("사진을 읽지 못했어요.");
        }

    }

    private Map<String, Object> active(Long userId) {

        Map<String, Object> a = coupleDao.selectActive(userId);
        if (a == null) throw new IllegalArgumentException("커플끼리만 쓸 수 있는 앨범이에요.");
        return a;

    }

    private static Long num(Object o) {

        return o instanceof Number n ? n.longValue() : null;

    }
}
