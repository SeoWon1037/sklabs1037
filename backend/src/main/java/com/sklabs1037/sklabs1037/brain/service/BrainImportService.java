package com.sklabs1037.sklabs1037.brain.service;

import com.sklabs1037.sklabs1037.brain.dao.BrainDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 📥 SKLabs 스프링 코드 기록 → 내 세컨드 브레인
 *
 * - 노트 파일: resources/brain/notes/*.md (번들을 만들 때마다 같이 갱신)
 *     code-○○   : 패키지별 지금 코드 요약 (tools/brain/gen-code-notes.mjs가 자바 파일에서 만듦)
 *     update-○○ : 업데이트마다 바뀐 스프링 코드 설명
 * - 관리자가 /brain에서 [받기]를 한 번 누르면, 그 뒤로는 서버가 켜질 때(= 배포 때마다) 새 기록을 자동으로 넣음
 * - 내가 고친 노트는 덮어쓰지 않고, 내가 지운 노트는 다시 넣지 않음 (T_BRAIN_SEEN)
 * - AI를 부르지 않음 (AI가 보류 중이어도 동작)
 */
@Slf4j
@Service
public class BrainImportService {

    @Autowired
    private BrainDao brainDao;

    private volatile List<BrainImportRules.Note> cache;

    // 같이 배포된 노트 파일 (처음 한 번 읽어 둠)
    public List<BrainImportRules.Note> notes() {

        if (cache != null) return cache;

        List<BrainImportRules.Note> out = new ArrayList<>();
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:brain/notes/*.md");
            Arrays.sort(files, Comparator.comparing(r -> String.valueOf(r.getFilename())));
            Set<String> keys = new HashSet<>();
            for (Resource r : files) {
                try {
                    String text = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    BrainImportRules.Note n = BrainImportRules.parse(r.getFilename(), text);
                    if (keys.add(n.key())) out.add(n);
                    else log.warn("세컨드 브레인 노트 key가 겹쳐요: {}", n.key());
                } catch (Exception e) {
                    log.warn("세컨드 브레인 노트 파일을 건너뜀: {} ({})", r.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("세컨드 브레인 노트 파일을 읽지 못함", e);
        }

        cache = List.copyOf(out);
        return cache;

    }

    /** 이 사람 브레인에 새 기록 넣기 · 안 고친 기록 바꾸기 → { added, updated, total } */
    public Map<String, Object> sync(Long userId) {

        int added = 0;
        int updated = 0;
        Map<String, Long> ids = new HashMap<>();

        for (BrainImportRules.Note n : notes()) {

            String hash = BrainImportRules.hash(n.body());
            boolean seen = brainDao.countSeen(userId, n.key()) > 0;
            Map<String, Object> cur = brainDao.selectSourceNote(userId, n.key());
            String topics = BrainRules.topicsColumn(n.topics());

            switch (BrainImportRules.decide(seen || cur != null, str(cur, "storedHash"), str(cur, "bodyHash"), hash)) {
                case NEW -> {
                    if (brainDao.countNotes(userId) >= BrainRules.MAX_NOTES) {
                        log.warn("세컨드 브레인 노트가 가득 차서 코드 기록을 더 넣지 못함 (회원 {})", userId);
                        return result(added, updated);
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("userId", userId);
                    row.put("kind", n.kind());
                    row.put("title", n.title());
                    row.put("body", n.body());
                    row.put("topics", topics);
                    row.put("key", n.key());
                    row.put("hash", hash);
                    brainDao.insertSourceNote(row);
                    brainDao.insertSeen(userId, n.key());
                    ids.put(n.key(), ((Number) row.get("id")).longValue());
                    added++;
                }
                case UPDATE -> {
                    Long id = ((Number) cur.get("id")).longValue();
                    updated += brainDao.updateSourceNote(userId, id, n.title(), n.body(), topics, hash);
                    ids.put(n.key(), id);
                }
                case KEEP -> {
                    if (!seen) brainDao.insertSeen(userId, n.key());
                    if (cur != null) ids.put(n.key(), ((Number) cur.get("id")).longValue());
                }
            }

        }

        // 노트 사이 선 (이미 있으면 그대로)
        for (BrainImportRules.Note n : notes()) {
            Long from = ids.get(n.key());
            if (from == null) continue;
            for (String to : n.links()) {
                Long toId = ids.get(to);
                if (toId != null) brainDao.insertLink(userId, from, toId);
            }
        }

        return result(added, updated);

    }

    /** 화면용: { available(파일 수), imported(내 브레인에 있는 수), on(받은 적 있음) } */
    public Map<String, Object> status(Long userId) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", notes().size());
        m.put("imported", brainDao.countImported(userId));
        m.put("on", brainDao.selectImportUsers().contains(userId));
        return m;

    }

    // 배포(서버 시작) 때마다: 받은 적 있는 사람에게 새 기록을 넣어 줌
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {

        try {
            for (Long userId : brainDao.selectImportUsers()) {
                Map<String, Object> r = sync(userId);
                log.info("세컨드 브레인 코드 기록: 회원 {} → 새로 {}개, 바뀜 {}개", userId, r.get("added"), r.get("updated"));
            }
        } catch (Exception e) {
            log.warn("세컨드 브레인 코드 기록 자동 넣기 실패 (서버는 그대로 켜짐)", e);
        }

    }

    private static Map<String, Object> result(int added, int updated) {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("added", added);
        m.put("updated", updated);
        return m;

    }

    private static String str(Map<String, Object> m, String k) {

        return m == null || m.get(k) == null ? null : String.valueOf(m.get(k));

    }

}
