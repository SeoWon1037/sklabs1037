package com.sklabs1037.sklabs1037.market.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 🛍️ 중고거래 DAO (SQL: resources/mapper/MarketMapper.xml)
 */
@Mapper
public interface MarketDao {

    // ===== 상품 =====
    int insertItem(Map<String, Object> item);

    int updateItem(Map<String, Object> item);

    // 지우지 않은 상품 한 개 (판매자 닉네임 포함), me가 있으면 찜 여부도
    Map<String, Object> selectItem(@Param("id") Long id, @Param("me") Long me);

    // 목록 { category, q, sort, onlySale, sellerId, likedBy, me, limit, offset }
    List<Map<String, Object>> selectItems(Map<String, Object> cond);

    int addView(Long id);

    int updateStatus(@Param("id") Long id, @Param("sellerId") Long sellerId, @Param("status") String status);

    // 마지막으로 끌어올린 지 24시간이 지났을 때만
    int bump(@Param("id") Long id, @Param("sellerId") Long sellerId);

    // sellerId가 null이면 관리자 삭제
    int softDelete(@Param("id") Long id, @Param("sellerId") Long sellerId);

    int countToday(Long sellerId);

    int countOpen(Long sellerId);

    // 판매자 { nickname, itemCount, soldCount, joinedAt }
    Map<String, Object> selectSeller(Long sellerId);

    // ===== 사진 =====
    int insertPhoto(Map<String, Object> photo);

    List<Long> selectPhotoIds(Long itemId);

    int deletePhoto(@Param("itemId") Long itemId, @Param("photoId") Long photoId);

    int updatePhotoOrd(@Param("itemId") Long itemId, @Param("photoId") Long photoId, @Param("ord") int ord);

    // { contentType, data } — thumb=true면 작은 그림 (없으면 원본)
    Map<String, Object> selectPhoto(@Param("id") Long id, @Param("thumb") boolean thumb);

    // ===== 찜 =====
    int insertLike(@Param("userId") Long userId, @Param("itemId") Long itemId);

    int deleteLike(@Param("userId") Long userId, @Param("itemId") Long itemId);

    int syncLikeCnt(Long itemId);

    // ===== 채팅 시작 =====
    int insertChat(@Param("itemId") Long itemId, @Param("buyerId") Long buyerId, @Param("roomId") Long roomId);

    int addChatCnt(Long itemId);

    // ===== 신고 =====
    int insertReport(@Param("itemId") Long itemId, @Param("userId") Long userId, @Param("reason") String reason);

    // 신고 수 +1, limit 이상이면 숨김 → 지금 신고 수
    Integer addReport(@Param("itemId") Long itemId, @Param("limit") int limit);
}
