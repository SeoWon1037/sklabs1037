package com.sklabs1037.sklabs1037.invite.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 🤝 친구 초대 DAO (SQL: resources/mapper/InviteMapper.xml)
 */
@Mapper
public interface InviteDao {

    // { id, nickname, provider, joinedAt(ms) } (탈퇴했으면 null)
    Map<String, Object> selectUser(Long userId);

    // 이미 초대받은 사람이면 0
    int insertInvite(@Param("inviteeId") Long inviteeId, @Param("inviterId") Long inviterId);

    int countInvited(Long inviterId);

    // 내가 초대한 사람들 (최근 것부터) [{ nickname, at }]
    List<Map<String, Object>> selectInvited(Long inviterId);

    // 나를 초대한 사람 닉네임 (없으면 null)
    String selectInviterName(Long inviteeId);
}
