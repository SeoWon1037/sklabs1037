package com.sklabs1037.sklabs1037.push.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 📲 푸시 알림 DAO (SQL: resources/mapper/PushMapper.xml)
 */
@Mapper
public interface PushDao {

    // 서버 VAPID 키 (없으면 null)
    Map<String, Object> selectKey();

    // 처음 한 번 (동시에 두 서버가 만들어도 먼저 들어간 1줄만 남음)
    int insertKey(@Param("publicKey") String publicKey, @Param("privateKey") String privateKey);

    // 같은 브라우저(ENDPOINT)면 키·계정만 바꿔서 1줄 유지
    int upsertSub(@Param("userId") Long userId, @Param("endpoint") String endpoint, @Param("p256dh") String p256dh,
                  @Param("auth") String auth, @Param("device") String device);

    int deleteSub(@Param("userId") Long userId, @Param("endpoint") String endpoint);

    int deleteByEndpoint(@Param("endpoint") String endpoint);

    int countByUser(@Param("userId") Long userId);

    // 보낼 곳 (ID, ENDPOINT, P256DH, AUTH)
    List<Map<String, Object>> selectSubs(@Param("userId") Long userId);

    // 내 기기 목록 (ENDPOINT는 끝부분만 비교용으로)
    List<Map<String, Object>> selectMyDevices(@Param("userId") Long userId);

    int markOk(@Param("id") Long id);

    int markFail(@Param("id") Long id);
}
