package com.sklabs1037.sklabs1037.user.keep;

import com.sklabs1037.sklabs1037.user.vo.UserLoginVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 🔑 로그인 유지 DAO (SQL: resources/mapper/KeepLoginMapper.xml)
 */
@Mapper
public interface KeepLoginDao {

    int insert(@Param("hash") String hash, @Param("userId") Long userId, @Param("expiresAt") LocalDateTime expiresAt);

    // 아직 쓸 수 있는 표의 주인 (탈퇴 · 체험 · AI 계정 제외, 비밀번호는 담지 않음). 없으면 null
    UserLoginVO selectUser(@Param("hash") String hash, @Param("now") LocalDateTime now);

    // 쓴 시각 기록 + 기한을 다시 30일로
    int touch(@Param("hash") String hash, @Param("expiresAt") LocalDateTime expiresAt, @Param("now") LocalDateTime now);

    int delete(@Param("hash") String hash);

    int deleteByUser(@Param("userId") Long userId);

    int deleteByEmail(@Param("email") String email);

    int deleteExpired(@Param("now") LocalDateTime now);
}
