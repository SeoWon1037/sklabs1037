package com.sklabs1037.sklabs1037.dm.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 💬 1:1 메시지 DAO (SQL: resources/mapper/DmMapper.xml)
 * - 방은 (작은 ID, 큰 ID) 한 쌍에 1개
 */
@Mapper
public interface DmDao {

    // 닉네임으로 상대 찾기 (탈퇴·AI 계정·나 빼고, 앞부분 일치)
    List<Map<String, Object>> searchUsers(@Param("q") String q, @Param("me") Long me, @Param("limit") int limit);

    Map<String, Object> selectUser(@Param("id") Long id);

    // 방 만들기 (이미 있으면 그대로) → ID
    Long upsertRoom(@Param("a") Long a, @Param("b") Long b);

    Map<String, Object> selectRoom(@Param("id") Long id);

    // 내 대화방 목록 (상대 닉네임, 마지막 메시지, 안 읽은 수)
    List<Map<String, Object>> selectRooms(@Param("me") Long me, @Param("limit") int limit);

    int countUnread(@Param("me") Long me);

    // 메시지 (before 보다 작은 ID, 최신부터 limit개)
    List<Map<String, Object>> selectMessages(@Param("roomId") Long roomId, @Param("before") Long before, @Param("limit") int limit);

    int insertMessage(Map<String, Object> msg);

    int touchRoom(@Param("roomId") Long roomId, @Param("msgId") Long msgId, @Param("senderIsA") boolean senderIsA);

    // 읽음 (뒤로 가지 않게 GREATEST)
    int markRead(@Param("roomId") Long roomId, @Param("isA") boolean isA, @Param("msgId") Long msgId);

    int countSentToday(@Param("me") Long me);

    // 둘 중 누구든 상대를 차단했으면 1 이상
    int countBlockBetween(@Param("a") Long a, @Param("b") Long b);

    int countBlocked(@Param("me") Long me, @Param("other") Long other);

    int insertBlock(@Param("me") Long me, @Param("other") Long other);

    int deleteBlock(@Param("me") Long me, @Param("other") Long other);
}
