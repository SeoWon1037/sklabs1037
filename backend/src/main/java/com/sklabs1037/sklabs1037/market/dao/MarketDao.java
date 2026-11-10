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

    // ===== 거래 완료 · 후기 =====
    // 이 상품으로 채팅을 건 사람들 { userId, nickname }
    List<Map<String, Object>> selectChatBuyers(Long itemId);

    // 판매완료 + 거래한 사람 (buyerId는 null 가능)
    int markSold(@Param("id") Long id, @Param("sellerId") Long sellerId, @Param("buyerId") Long buyerId);

    Long selectBuyerId(Long itemId);

    int insertReview(Map<String, Object> review);

    // 이 거래에 남겨진 후기 수 (있으면 판매완료를 되돌릴 수 없음)
    int countItemReviews(Long itemId);

    int countReview(@Param("itemId") Long itemId, @Param("writerId") Long writerId);

    // 받은 후기 수 { good, ok, bad }
    Map<String, Object> selectReviewStats(Long userId);

    // 받은 후기 (최근 것부터) { rating, tags, comment, writerName, writerRole, itemTitle, at }
    List<Map<String, Object>> selectReviews(@Param("userId") Long userId, @Param("limit") int limit);

    // ===== 키워드 알림 =====
    List<String> selectKeywords(Long userId);

    int insertKeyword(@Param("userId") Long userId, @Param("keyword") String keyword);

    int deleteKeyword(@Param("userId") Long userId, @Param("keyword") String keyword);

    // 제목에 내 키워드가 들어간 회원들 { userId, keyword } (판매자 제외)
    List<Map<String, Object>> selectKeywordMatches(@Param("title") String title, @Param("sellerId") Long sellerId);

    // ===== 가격 내림 =====
    int setPrevPrice(@Param("id") Long id, @Param("prevPrice") Long prevPrice);

    List<Long> selectLikers(Long itemId);

    // ===== 관리자 =====
    // 신고가 있거나 숨겨진 상품 (최근 신고 이유 포함)
    List<Map<String, Object>> selectReported();

    // 숨김 풀기: 판매중으로 + 신고 기록 지움
    int restore(Long id);

    int deleteReports(Long itemId);

    // ===== 📍 지역 · 📊 시세 =====
    // 상품이 많은 지역 { region, count }
    List<Map<String, Object>> selectRegions();

    // 비슷한 상품 가격 분포 { count, low, median, high }
    Map<String, Object> selectPriceGuide(@Param("category") String category, @Param("word") String word, @Param("exceptId") Long exceptId);

    // ===== 💰 가격 제안 =====
    int cancelPendingOffer(@Param("itemId") Long itemId, @Param("buyerId") Long buyerId);

    int insertOffer(@Param("itemId") Long itemId, @Param("buyerId") Long buyerId, @Param("price") long price);

    // 이 상품에 이 사람이 지금까지 보낸 제안 수 (취소·거절 포함)
    int countOffers(@Param("itemId") Long itemId, @Param("buyerId") Long buyerId);

    // 기다리는 제안들 (높은 가격부터) { id, buyerId, buyerName, price, at }
    List<Map<String, Object>> selectPendingOffers(Long itemId);

    Map<String, Object> selectMyOffer(@Param("itemId") Long itemId, @Param("buyerId") Long buyerId);

    // { id, itemId, buyerId, price, status, sellerId, title }
    Map<String, Object> selectOffer(Long offerId);

    // 기다리는 제안만 바뀜 (이미 답했으면 0)
    int updateOfferStatus(@Param("offerId") Long offerId, @Param("status") String status);
}
