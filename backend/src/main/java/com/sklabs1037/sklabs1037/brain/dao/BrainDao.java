package com.sklabs1037.sklabs1037.brain.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 🧠 세컨드 브레인 DAO (SQL: resources/mapper/BrainMapper.xml) — 모든 조회 · 수정에 USER_ID 조건 (본인 것만)
 */
@Mapper
public interface BrainDao {

    int countNotes(Long userId);

    // 목록 (본문 대신 앞부분만) { id, kind, title, topics, body(앞 300자), updatedAt } — kind · like 가 null이면 전체
    List<Map<String, Object>> selectNotes(@Param("userId") Long userId, @Param("kind") String kind, @Param("like") String like, @Param("limit") int limit);

    // 노트 하나 { id, kind, title, body, topics, createdAt, updatedAt } (없거나 남의 것이면 null)
    Map<String, Object> selectNote(@Param("userId") Long userId, @Param("id") Long id);

    // row: { userId, kind, title, body, topics } → 저장 후 row.id
    int insertNote(Map<String, Object> row);

    int updateNote(@Param("userId") Long userId, @Param("id") Long id, @Param("title") String title, @Param("body") String body, @Param("topics") String topics);

    int deleteNote(@Param("userId") Long userId, @Param("id") Long id);

    // ===== 연결 =====
    int insertLink(@Param("userId") Long userId, @Param("fromId") Long fromId, @Param("toId") Long toId);

    int deleteLinksOf(@Param("userId") Long userId, @Param("id") Long id);

    // 이 노트가 가리키는 노트 (출처) / 이 노트를 가리키는 노트 (이걸로 만든 것) { id, kind, title }
    List<Map<String, Object>> selectLinkedTo(@Param("userId") Long userId, @Param("id") Long id);

    List<Map<String, Object>> selectLinkedFrom(@Param("userId") Long userId, @Param("id") Long id);

    // ===== 그래프 =====
    List<Map<String, Object>> selectGraphNotes(@Param("userId") Long userId, @Param("limit") int limit);

    List<Map<String, Object>> selectLinks(Long userId);

    // ===== AI 정리 =====
    // like가 제목 · 본문 · 주제에 들어 있는 RAW 메모 (최근 것부터) { id, title, body, topics }
    List<Map<String, Object>> searchRaw(@Param("userId") Long userId, @Param("like") String like, @Param("limit") int limit);

    // 최근 24시간 동안 만든 WIKI 수 — AI 정리 하루 한도
    int countWikiToday(Long userId);

    // ===== 📥 사이트 코드 기록 (BrainImportService) =====
    // { id, storedHash, bodyHash } (없으면 null)
    Map<String, Object> selectSourceNote(@Param("userId") Long userId, @Param("key") String key);

    int countSeen(@Param("userId") Long userId, @Param("key") String key);

    int insertSeen(@Param("userId") Long userId, @Param("key") String key);

    // row: { userId, kind, title, body, topics, key, hash } → 저장 후 row.id
    int insertSourceNote(Map<String, Object> row);

    // 내가 고치지 않은 노트만 바뀜 (MD5(BODY) = SOURCE_HASH) → 바뀐 줄 수
    int updateSourceNote(@Param("userId") Long userId, @Param("id") Long id, @Param("title") String title, @Param("body") String body, @Param("topics") String topics, @Param("hash") String hash);

    List<Long> selectImportUsers();

    int countImported(Long userId);

}
