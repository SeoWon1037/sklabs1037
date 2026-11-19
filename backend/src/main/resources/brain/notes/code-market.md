key: code-market
title: Spring 코드 · market 패키지
topics: spring, market
---
## 요약
🛍️ 중고거래 장터

클래스 5개 · API 27개 · DB 표 10개 · 위치 `backend/src/main/java/com/sklabs1037/sklabs1037/market`

## API
- GET /api/market/meta → meta()
- GET /api/market/items → list()
- GET /api/market/items/{id} → detail()
- GET /api/market/photos/{photoId} → photo()
- GET /api/market/my → my()
- POST /api/market/items → create()
- POST /api/market/items/{id} → update()
- POST /api/market/items/{id}/status → status()
- POST /api/market/items/{id}/bump → bump()
- POST /api/market/items/{id}/delete → delete()
- POST /api/market/items/{id}/like → like()
- POST /api/market/items/{id}/chat → chat()
- POST /api/market/items/{id}/report → report()
- POST /api/market/ai-write → aiWrite()
- GET /api/market/users/{userId} → profile()
- GET /api/market/items/{id}/buyers → buyers()
- POST /api/market/items/{id}/sold → sold()
- POST /api/market/items/{id}/review → review()
- POST /api/market/keywords → addKeyword()
- POST /api/market/keywords/delete → removeKeyword()
- GET /api/market/regions → regions()
- GET /api/market/price-guide → priceGuide()
- POST /api/market/items/{id}/offer → offer()
- POST /api/market/items/{id}/offer/cancel → cancelOffer()
- POST /api/market/offers/{offerId}/answer → answerOffer()
- GET /api/market/admin/reported → reported()
- POST /api/market/admin/{id}/restore → restore()

## DB 표
- T_MARKET_CHAT, T_MARKET_ITEM, T_MARKET_KEYWORD, T_MARKET_LIKE, T_MARKET_OFFER, T_MARKET_PHOTO, T_MARKET_REPORT, T_MARKET_REVIEW, T_MARKET_VIEW_DAY, T_USER

## 클래스

### MarketController · Controller
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/market/controller/MarketController.java
- 🛍️ 중고거래 장터

### MarketDao · DAO (MyBatis)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/market/dao/MarketDao.java
- 🛍️ 중고거래 DAO (SQL: resources/mapper/MarketMapper.xml)
- 메서드: insertItem · updateItem · selectItem · selectItems · addView · updateStatus · bump · softDelete · countToday · countOpen · selectSeller · insertPhoto · selectPhotoIds · deletePhoto · updatePhotoOrd · selectPhoto · insertLike · deleteLike · syncLikeCnt · insertChat · addChatCnt · insertReport · addReport · selectChatBuyers · markSold · selectBuyerId · insertReview · countItemReviews · countReview · selectReviewStats · selectReviews · selectKeywords · insertKeyword · deleteKeyword · selectKeywordMatches · setPrevPrice · selectLikers · selectReported · restore · deleteReports · selectRegions · selectPriceGuide · cancelPendingOffer · insertOffer · countOffers · selectPendingOffers · selectMyOffer · selectOffer · updateOfferStatus · bumpViewDay · selectViewDays · deleteOldViewDays

### MarketPhotos · class
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/market/service/MarketPhotos.java
- 🛍️ 상품 사진 검사 (순수 계산)
- 화면에서 줄여서 보냄: 큰 그림(긴 변 1280px JPEG) + 목록용 작은 그림(360px JPEG)
- 서버는 진짜 그림 파일인지(파일 맨 앞 바이트) · 크기 제한만 확인

### MarketRules · Rules (순수 계산)
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/market/service/MarketRules.java
- 🛍️ 중고거래 규칙 (순수 계산 → 테스트 가능, DB·AI 없음)
- 카테고리 · 상품 상태 · 거래 방법 목록 (화면도 서버가 준 이 목록을 씀)
- 글 검사: 제목 2~40자 · 설명 10~1,000자 · 가격 0(나눔)~9,999만 원
- 거래 금지 품목(술 · 담배 · 의약품 · 계정 · 신분증 …)이 제목·설명에 있으면 올릴 수 없음
- 사기에 자주 쓰이는 말(선입금 · 외부 메신저 유도 …)은 막지는 않고 "주의" 표시 → 상세 화면에 안내
- 메서드: Draft(category, title, price, description, cond, tradeType, region) · clean(Map<String, in) · temperature(good, ok, bad) · reviewTags(rating, raw) · reviewComment(raw) · alertKeyword(raw) · priceDropped(before, after) · offerPrice(itemPrice, raw) · guideWord(title) · region(raw) · banned(text) · cautions(text) · canChange(from, to) · sortOf(sort)

### MarketService · Service
- 파일: backend/src/main/java/com/sklabs1037/sklabs1037/market/service/MarketService.java
- 🛍️ 중고거래 장터
- 올리기: 로그인 회원 누구나 (하루 10개 · 판매중/예약중 합쳐 50개까지), 사진 0~5장, 규칙 검사는 MarketRules
- 보기: 로그인 없이 누구나 (목록 · 검색 · 카테고리 · 정렬 · 상세)
- 사기: [채팅하기] → 판매자와 1:1 메시지 방을 열고(💬 DmService), 처음이면 "어떤 상품 문의인지" 안내 메시지를 대신 보냄
- 결제 기능은 없음 (대화로 직접 거래)
- 메서드: meta() · list(category, q, sort, onlySale, free, page) · list(category, q, sort, onlySale, free, drop, region, page) · detail(me, id, admin) · ogItem(id) · cleanViewDays() · profile(userId) · my(me) · photo(photoId, thumb) · create(me, Map<String, in) · update(me, id, Map<String, in) · status(me, id, status) · buyers(me, id) · sold(me, id, buyerId)
